package com.myproxy.desktop.proxy

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg

class WindowsSystemProxyManager : SystemProxyManager {
    override fun enable(httpPort: Int, socksPort: Int) {
        if (!ProxySnapshotStore.exists()) {
            ProxySnapshotStore.write(ProxySnapshot("windows", windows = capture()))
        }
        Advapi32Util.registrySetIntValue(ROOT, KEY, "ProxyEnable", 1)
        Advapi32Util.registrySetStringValue(
            ROOT,
            KEY,
            "ProxyServer",
            "http=127.0.0.1:$httpPort;https=127.0.0.1:$httpPort;socks=127.0.0.1:$socksPort",
        )
        Advapi32Util.registrySetStringValue(ROOT, KEY, "ProxyOverride", "<local>")
        notifyChanged()
    }

    override fun restore() {
        val snapshot = ProxySnapshotStore.read()?.windows ?: return
        restoreInt("ProxyEnable", snapshot.proxyEnableExists, snapshot.proxyEnable)
        restoreString("ProxyServer", snapshot.proxyServerExists, snapshot.proxyServer)
        restoreString("ProxyOverride", snapshot.proxyOverrideExists, snapshot.proxyOverride)
        notifyChanged()
        ProxySnapshotStore.clear()
    }

    private fun capture(): WindowsProxySnapshot {
        val enableExists = valueExists("ProxyEnable")
        val serverExists = valueExists("ProxyServer")
        val overrideExists = valueExists("ProxyOverride")
        return WindowsProxySnapshot(
            proxyEnableExists = enableExists,
            proxyEnable = if (enableExists) Advapi32Util.registryGetIntValue(ROOT, KEY, "ProxyEnable") else 0,
            proxyServerExists = serverExists,
            proxyServer = if (serverExists) Advapi32Util.registryGetStringValue(ROOT, KEY, "ProxyServer") else "",
            proxyOverrideExists = overrideExists,
            proxyOverride = if (overrideExists) {
                Advapi32Util.registryGetStringValue(ROOT, KEY, "ProxyOverride")
            } else {
                ""
            },
        )
    }

    private fun valueExists(name: String): Boolean =
        Advapi32Util.registryValueExists(ROOT, KEY, name)

    private fun restoreInt(name: String, existed: Boolean, value: Int) {
        if (existed) Advapi32Util.registrySetIntValue(ROOT, KEY, name, value) else deleteIfPresent(name)
    }

    private fun restoreString(name: String, existed: Boolean, value: String) {
        if (existed) Advapi32Util.registrySetStringValue(ROOT, KEY, name, value) else deleteIfPresent(name)
    }

    private fun deleteIfPresent(name: String) {
        if (valueExists(name)) Advapi32Util.registryDeleteValue(ROOT, KEY, name)
    }

    private fun notifyChanged() {
        WinInet.INSTANCE.InternetSetOptionW(null, INTERNET_OPTION_SETTINGS_CHANGED, null, 0)
        WinInet.INSTANCE.InternetSetOptionW(null, INTERNET_OPTION_REFRESH, null, 0)
    }

    private interface WinInet : Library {
        fun InternetSetOptionW(
            internet: Pointer?,
            option: Int,
            buffer: Pointer?,
            bufferLength: Int,
        ): Boolean

        companion object {
            val INSTANCE: WinInet = Native.load("wininet", WinInet::class.java)
        }
    }

    companion object {
        private val ROOT = WinReg.HKEY_CURRENT_USER
        private const val KEY = "Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings"
        private const val INTERNET_OPTION_REFRESH = 37
        private const val INTERNET_OPTION_SETTINGS_CHANGED = 39
    }
}
