package com.myproxy.desktop.proxy

import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WindowsSystemProxyManagerSmokeTest {
    @Test
    fun enableAndRestoreWhenExplicitlyRequested() {
        if (!System.getProperty("os.name").contains("windows", ignoreCase = true)) return
        if (System.getenv("MYPROXY_RUN_SYSTEM_PROXY_SMOKE") != "1") return

        val manager = WindowsSystemProxyManager()
        manager.recoverIfNeeded()
        val before = readState()

        try {
            manager.enable(httpPort = 18_080, socksPort = 18_081)
            val enabled = readState()
            assertEquals(1, enabled.proxyEnable)
            assertTrue(enabled.proxyServer.orEmpty().contains("127.0.0.1:18080"))
            assertTrue(ProxySnapshotStore.exists())
        } finally {
            manager.restore()
        }

        assertEquals(before, readState())
        assertFalse(ProxySnapshotStore.exists())
    }

    private fun readState(): RegistryState = RegistryState(
        proxyEnable = readInt("ProxyEnable"),
        proxyServer = readString("ProxyServer"),
        proxyOverride = readString("ProxyOverride"),
    )

    private fun readInt(name: String): Int? = if (valueExists(name)) {
        Advapi32Util.registryGetIntValue(ROOT, KEY, name)
    } else {
        null
    }

    private fun readString(name: String): String? = if (valueExists(name)) {
        Advapi32Util.registryGetStringValue(ROOT, KEY, name)
    } else {
        null
    }

    private fun valueExists(name: String): Boolean =
        Advapi32Util.registryValueExists(ROOT, KEY, name)

    private data class RegistryState(
        val proxyEnable: Int?,
        val proxyServer: String?,
        val proxyOverride: String?,
    )

    companion object {
        private val ROOT = WinReg.HKEY_CURRENT_USER
        private const val KEY = "Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings"
    }
}
