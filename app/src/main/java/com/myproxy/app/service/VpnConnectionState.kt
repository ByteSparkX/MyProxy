package com.myproxy.app.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class VpnUiState {
    // 未连接，服务未持有 TUN 或内核运行状态。
    DISCONNECTED,
    // 正在申请权限、启动内核、建立 TUN 或启动 tun2socks。
    CONNECTING,
    // VPN 链路已建立，前台服务处于运行状态。
    CONNECTED,
    // 启动或停止过程中出现错误，界面应展示错误信息。
    ERROR,
}

data class VpnState(
    val uiState: VpnUiState = VpnUiState.DISCONNECTED,
    val errorMessage: String? = null,
)

object VpnConnectionState {
    private val mutableState = MutableStateFlow(VpnState())

    // 服务和 ViewModel 共享同一份 StateFlow，避免界面重建后状态丢失。
    val state: StateFlow<VpnState> = mutableState.asStateFlow()

    fun setConnecting() {
        mutableState.value = VpnState(uiState = VpnUiState.CONNECTING)
    }

    fun setConnected() {
        mutableState.value = VpnState(uiState = VpnUiState.CONNECTED)
    }

    fun setDisconnected() {
        mutableState.value = VpnState(uiState = VpnUiState.DISCONNECTED)
    }

    fun setError(message: String) {
        mutableState.value = VpnState(uiState = VpnUiState.ERROR, errorMessage = message)
    }
}
