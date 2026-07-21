package com.myproxy.desktop.proxy

import com.myproxy.desktop.data.DesktopPaths
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermission

interface SystemProxyManager {
    fun enable(httpPort: Int, socksPort: Int)
    fun restore()
    fun recoverIfNeeded() = restore()
}

object SystemProxyFactory {
    fun create(): SystemProxyManager {
        val os = System.getProperty("os.name").lowercase()
        return when {
            os.contains("win") -> WindowsSystemProxyManager()
            os.contains("mac") -> MacSystemProxyManager()
            else -> UnsupportedSystemProxyManager()
        }
    }
}

@Serializable
data class ProxySnapshot(
    val platform: String,
    val windows: WindowsProxySnapshot? = null,
    val mac: MacProxySnapshot? = null,
)

@Serializable
data class WindowsProxySnapshot(
    val proxyEnableExists: Boolean,
    val proxyEnable: Int = 0,
    val proxyServerExists: Boolean,
    val proxyServer: String = "",
    val proxyOverrideExists: Boolean,
    val proxyOverride: String = "",
)

@Serializable
data class MacProxySnapshot(
    val service: String,
    val web: MacProxyValue,
    val secureWeb: MacProxyValue,
    val socks: MacProxyValue,
)

@Serializable
data class MacProxyValue(
    val enabled: Boolean,
    val server: String,
    val port: Int,
    val authenticated: Boolean = false,
)

internal object ProxySnapshotStore {
    private val json = Json { ignoreUnknownKeys = true }

    fun exists(): Boolean = Files.isRegularFile(DesktopPaths.proxySnapshotFile)

    fun read(): ProxySnapshot? = runCatching {
        json.decodeFromString<ProxySnapshot>(Files.readString(DesktopPaths.proxySnapshotFile))
    }.getOrNull()

    fun write(snapshot: ProxySnapshot) {
        val target = DesktopPaths.proxySnapshotFile
        val temporary = target.resolveSibling("${target.fileName}.tmp")
        Files.writeString(temporary, json.encodeToString(snapshot))
        restrictToCurrentUser(temporary)
        runCatching {
            Files.move(
                temporary,
                target,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        }.getOrElse {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING)
        }
        restrictToCurrentUser(target)
    }

    fun clear() {
        Files.deleteIfExists(DesktopPaths.proxySnapshotFile)
    }

    private fun restrictToCurrentUser(path: java.nio.file.Path) {
        runCatching {
            Files.setPosixFilePermissions(
                path,
                setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
            )
        }
    }
}

private class UnsupportedSystemProxyManager : SystemProxyManager {
    override fun enable(httpPort: Int, socksPort: Int) {
        throw UnsupportedOperationException("当前桌面系统暂不支持自动系统代理")
    }

    override fun restore() {
        ProxySnapshotStore.clear()
    }
}
