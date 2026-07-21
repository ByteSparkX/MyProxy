package com.myproxy.app.model

import kotlinx.serialization.Serializable

@Serializable
enum class ProtocolType {
    // VLESS 节点，通常使用 uuid，可搭配 TLS、Reality、WS、gRPC 等传输参数。
    VLESS,
    // VMess 节点，通常使用 uuid 和 alterId 兼容字段，当前模型把扩展项放入 extra。
    VMESS,
    // Trojan 节点，通常使用 password，可搭配 TLS 或 Reality。
    TROJAN,
    // Shadowsocks 节点，通常使用 method 和 password。
    SHADOWSOCKS,
}
