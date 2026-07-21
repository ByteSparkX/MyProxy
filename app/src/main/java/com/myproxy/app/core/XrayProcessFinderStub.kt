package com.myproxy.app.core

import libv2ray.ProcessFinder

class XrayProcessFinderStub : ProcessFinder {
    override fun findProcessByConnection(
        localIp: String?,
        localPort: String?,
        remoteIp: Long,
        remotePort: String?,
        protocol: Long,
    ): Long {
        // 当前配置不依赖进程归属路由，分应用规则由 VpnService.Builder 负责。
        AppLog.d(TAG, "Process lookup stub protocol=$protocol")
        return PROCESS_UNKNOWN
    }

    private companion object {
        private const val TAG = "XrayProcessFinderStub"
        private const val PROCESS_UNKNOWN = 0L
    }
}
