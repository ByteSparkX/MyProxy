package com.myproxy.desktop.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class ProtocolType {
    VLESS,
    VMESS,
    TROJAN,
    SHADOWSOCKS,
}

@Serializable
enum class RoutingMode {
    RULE,
    GLOBAL,
    DIRECT,
}

@Serializable
data class ProxyNode(
    val id: String = UUID.randomUUID().toString(),
    val remark: String,
    val protocol: ProtocolType,
    val address: String,
    val port: Int,
    val uuid: String? = null,
    val password: String? = null,
    val method: String? = null,
    val network: String? = null,
    val security: String? = null,
    val sni: String? = null,
    val host: String? = null,
    val path: String? = null,
    val flow: String? = null,
    val alpn: List<String> = emptyList(),
    val allowInsecure: Boolean = false,
    val extra: Map<String, String> = emptyMap(),
)

@Serializable
data class DesktopState(
    val nodes: List<ProxyNode> = emptyList(),
    val selectedNodeId: String? = null,
    val routingMode: RoutingMode = RoutingMode.RULE,
)

enum class ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR,
}

data class ConnectionState(
    val status: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val message: String = "未连接",
)

data class ImportResult(
    val successCount: Int,
    val failedCount: Int,
    val message: String,
)
