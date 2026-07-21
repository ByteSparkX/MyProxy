package com.myproxy.app.core

import libv2ray.CoreCallbackHandler

class XrayCoreCallback : CoreCallbackHandler {
    override fun startup(): Long {
        // AAR 只要求确认生命周期回调，VPN 链路由 MyVpnService 独立管理。
        AppLog.i(TAG, "Xray core startup callback")
        return RESULT_SUCCESS
    }

    override fun shutdown(): Long {
        // 返回成功后，外层仍按 tun2socks、TUN、核心顺序完成资源清理。
        AppLog.i(TAG, "Xray core shutdown callback")
        return RESULT_SUCCESS
    }

    override fun onEmitStatus(status: Long, message: String?): Long {
        // 只记录状态码和文本长度，避免第三方内核文本携带敏感上下文。
        AppLog.d(TAG, "Xray core status=$status messageLength=${message?.length ?: 0}")
        return RESULT_SUCCESS
    }

    private companion object {
        private const val TAG = "XrayCoreCallback"
        private const val RESULT_SUCCESS = 0L
    }
}
