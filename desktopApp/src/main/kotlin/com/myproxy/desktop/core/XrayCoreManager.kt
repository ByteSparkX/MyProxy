package com.myproxy.desktop.core

import com.myproxy.desktop.data.DesktopPaths
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class XrayCoreManager {
    @Volatile
    private var process: Process? = null

    @Synchronized
    fun start(configJson: String) {
        check(process?.isAlive != true) { "Xray 已经在运行" }
        val environment = XrayEnvironment.locate()
        val config = DesktopPaths.coreConfigFile
        Files.writeString(config, configJson)
        restrictConfig(config)

        try {
            val validation = command(environment, config, testOnly = true).start()
            consumeOutput(validation, "myproxy-xray-validation")
            if (!validation.waitFor(20, TimeUnit.SECONDS)) {
                validation.destroyForcibly()
                validation.waitFor(2, TimeUnit.SECONDS)
                throw IllegalStateException("Xray 配置校验超时")
            }
            if (validation.exitValue() != 0) {
                throw IllegalStateException("Xray 配置校验失败，请检查节点参数")
            }

            val started = command(environment, config, testOnly = false).start()
            consumeOutput(started, "myproxy-xray-output")
            Thread.sleep(800)
            if (!started.isAlive) {
                throw IllegalStateException("Xray 启动失败，退出码 ${started.exitValue()}")
            }
            process = started
        } finally {
            // 核心启动后已加载配置，立即删除含节点凭据的临时文件。
            runCatching { Files.deleteIfExists(config) }
        }
    }

    @Synchronized
    fun stop() {
        val running = process ?: return
        process = null
        if (running.isAlive) {
            running.destroy()
            if (!running.waitFor(3, TimeUnit.SECONDS)) {
                running.destroyForcibly()
                running.waitFor(2, TimeUnit.SECONDS)
            }
        }
        runCatching { Files.deleteIfExists(DesktopPaths.coreConfigFile) }
    }

    fun isRunning(): Boolean = process?.isAlive == true

    private fun consumeOutput(target: Process, threadName: String) {
        thread(name = threadName, isDaemon = true) {
            // 持续消费输出避免子进程阻塞，但不记录可能含节点信息的内容。
            target.inputStream.bufferedReader().useLines { lines -> lines.forEach { _ -> } }
        }
    }

    private fun command(
        xrayEnvironment: XrayEnvironment,
        config: Path,
        testOnly: Boolean,
    ): ProcessBuilder {
        val arguments = buildList {
            add(xrayEnvironment.executable.toAbsolutePath().toString())
            add("run")
            if (testOnly) add("-test")
            add("-c")
            add(config.toAbsolutePath().toString())
        }
        return ProcessBuilder(arguments)
            .directory(xrayEnvironment.resourcesDirectory.toFile())
            .redirectErrorStream(true)
            .apply {
                environment()["XRAY_LOCATION_ASSET"] = xrayEnvironment.resourcesDirectory.toString()
            }
    }

    private fun restrictConfig(path: Path) {
        runCatching {
            Files.setPosixFilePermissions(
                path,
                setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
            )
        }
    }
}

data class XrayEnvironment(
    val resourcesDirectory: Path,
    val executable: Path,
) {
    companion object {
        fun locate(): XrayEnvironment {
            val override = System.getenv("MYPROXY_XRAY_DIR")?.takeIf(String::isNotBlank)
            val packaged = System.getProperty("compose.application.resources.dir")
                ?.takeIf(String::isNotBlank)
            val directory = Path.of(override ?: packaged ?: "").toAbsolutePath().normalize()
            val executableName = if (System.getProperty("os.name").lowercase().contains("win")) {
                "xray.exe"
            } else {
                "xray"
            }
            val executable = directory.resolve(executableName)
            require(Files.isRegularFile(executable)) {
                "未找到 Xray 核心，请使用正式桌面发布包"
            }
            require(Files.isRegularFile(directory.resolve("geoip.dat"))) { "缺少 geoip.dat" }
            require(Files.isRegularFile(directory.resolve("geosite.dat"))) { "缺少 geosite.dat" }
            if (!System.getProperty("os.name").lowercase().contains("win")) {
                executable.toFile().setExecutable(true, true)
            }
            return XrayEnvironment(directory, executable)
        }
    }
}
