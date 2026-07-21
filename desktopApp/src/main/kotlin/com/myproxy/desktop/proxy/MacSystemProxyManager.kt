package com.myproxy.desktop.proxy

import java.util.concurrent.TimeUnit

class MacSystemProxyManager : SystemProxyManager {
    override fun enable(httpPort: Int, socksPort: Int) {
        val service = activeNetworkService()
        val currentWeb = readProxy("-getwebproxy", service)
        val currentSecureWeb = readProxy("-getsecurewebproxy", service)
        val currentSocks = readProxy("-getsocksfirewallproxy", service)
        require(listOf(currentWeb, currentSecureWeb, currentSocks).none { it.authenticated }) {
            "当前 macOS 系统代理使用身份认证，无法在不丢失凭据的情况下安全覆盖"
        }
        if (!ProxySnapshotStore.exists()) {
            ProxySnapshotStore.write(
                ProxySnapshot(
                    platform = "macos",
                    mac = MacProxySnapshot(
                        service = service,
                        web = currentWeb,
                        secureWeb = currentSecureWeb,
                        socks = currentSocks,
                    ),
                ),
            )
        }
        applyCommands(
            proxyCommands("-setwebproxy", "-setwebproxystate", service, httpPort) +
                proxyCommands("-setsecurewebproxy", "-setsecurewebproxystate", service, httpPort) +
                proxyCommands("-setsocksfirewallproxy", "-setsocksfirewallproxystate", service, socksPort),
        )
    }

    override fun restore() {
        val snapshot = ProxySnapshotStore.read()?.mac ?: return
        applyCommands(
            restoreCommands("-setwebproxy", "-setwebproxystate", snapshot.service, snapshot.web) +
                restoreCommands(
                    "-setsecurewebproxy",
                    "-setsecurewebproxystate",
                    snapshot.service,
                    snapshot.secureWeb,
                ) +
                restoreCommands(
                    "-setsocksfirewallproxy",
                    "-setsocksfirewallproxystate",
                    snapshot.service,
                    snapshot.socks,
                ),
        )
        ProxySnapshotStore.clear()
    }

    private fun activeNetworkService(): String {
        val route = run("/sbin/route", "-n", "get", "default")
        val device = route.lineSequence()
            .map(String::trim)
            .firstOrNull { it.startsWith("interface:") }
            ?.substringAfter(":")
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?: throw IllegalStateException("无法识别当前网络接口")
        val order = run(NETWORK_SETUP, "-listnetworkserviceorder").lines()
        var service: String? = null
        order.forEach { line ->
            val trimmed = line.trim()
            val serviceMatch = Regex("^\\(\\d+\\)\\s+(.+)$").find(trimmed)
            if (serviceMatch != null) {
                service = serviceMatch.groupValues[1].removePrefix("*").trim()
            } else if (trimmed.contains("Device: $device)")) {
                return service ?: throw IllegalStateException("无法识别当前网络服务")
            }
        }
        throw IllegalStateException("未找到当前网络服务")
    }

    private fun readProxy(command: String, service: String): MacProxyValue {
        val values = run(NETWORK_SETUP, command, service).lineSequence().mapNotNull { line ->
            val key = line.substringBefore(":", "").trim()
            val value = line.substringAfter(":", "").trim()
            key.takeIf(String::isNotBlank)?.let { it to value }
        }.toMap()
        return MacProxyValue(
            enabled = values["Enabled"].equals("Yes", true),
            server = values["Server"].orEmpty(),
            port = values["Port"]?.toIntOrNull() ?: 0,
            authenticated = values["Authenticated Proxy Enabled"] == "1" ||
                values["Authenticated Proxy Enabled"].equals("Yes", true),
        )
    }

    private fun proxyCommands(
        setCommand: String,
        stateCommand: String,
        service: String,
        port: Int,
    ): List<List<String>> = listOf(
        listOf(NETWORK_SETUP, setCommand, service, "127.0.0.1", port.toString(), "off"),
        listOf(NETWORK_SETUP, stateCommand, service, "on"),
    )

    private fun restoreCommands(
        setCommand: String,
        stateCommand: String,
        service: String,
        value: MacProxyValue,
    ): List<List<String>> = buildList {
        if (value.server.isNotBlank() && value.port in 1..65535) {
            add(listOf(NETWORK_SETUP, setCommand, service, value.server, value.port.toString(), "off"))
        }
        add(listOf(NETWORK_SETUP, stateCommand, service, if (value.enabled) "on" else "off"))
    }

    private fun applyCommands(commands: List<List<String>>) {
        val directResult = runCatching { commands.forEach { run(*it.toTypedArray()) } }
        if (directResult.isSuccess) return

        // macOS 可能要求管理员权限；一次授权完成本次全部代理变更。
        val shellCommand = commands.joinToString(" && ") { command ->
            command.joinToString(" ") { shellQuote(it) }
        }
        val appleScript = "do shell script \"${appleScriptEscape(shellCommand)}\" with administrator privileges"
        runCommand(listOf("/usr/bin/osascript", "-e", appleScript), timeoutSeconds = 120)
    }

    private fun shellQuote(value: String): String = "'${value.replace("'", "'\\\"'\\\"'")}'"

    private fun appleScriptEscape(value: String): String = value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")

    private fun run(vararg command: String): String = runCommand(command.toList(), timeoutSeconds = 15)

    private fun runCommand(command: List<String>, timeoutSeconds: Long): String {
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        var output = ""
        val reader = kotlin.concurrent.thread(name = "myproxy-system-command", isDaemon = true) {
            output = process.inputStream.bufferedReader().use { it.readText() }
        }
        if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            process.waitFor(2, TimeUnit.SECONDS)
            throw IllegalStateException("系统代理命令执行超时")
        }
        reader.join(1_000)
        if (process.exitValue() != 0) {
            throw IllegalStateException("系统代理设置失败，退出码 ${process.exitValue()}")
        }
        return output
    }

    companion object {
        private const val NETWORK_SETUP = "/usr/sbin/networksetup"
    }
}
