package com.myproxy.app.service

import com.myproxy.app.core.AppLog
import java.lang.ref.WeakReference

object VpnServiceBridge {
    private const val TAG = "VpnServiceBridge"
    private var serviceRef: WeakReference<MyVpnService>? = null

    fun register(service: MyVpnService) {
        serviceRef = WeakReference(service)
        AppLog.i(TAG, "VPN 服务引用已注册。")
    }

    fun unregister(service: MyVpnService) {
        if (serviceRef?.get() === service) {
            serviceRef = null
            AppLog.i(TAG, "VPN 服务引用已释放。")
        }
    }

    fun protectSocket(socketFd: Int): Boolean {
        if (socketFd < 0) {
            AppLog.w(TAG, "protect 收到无效 socket fd：$socketFd")
            return false
        }

        val service = serviceRef?.get()
        if (service == null) {
            AppLog.w(TAG, "protect 被调用时 VPN 服务不可用。")
            return false
        }

        // 不调用 protect 会导致内核出站连接再次进入 VPN，形成流量回环。
        val protected = service.protect(socketFd)
        AppLog.d(TAG, "protect 已调用，fd=$socketFd result=$protected")
        return protected
    }
}
