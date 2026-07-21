package com.myproxy.app.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "proxy_nodes")
data class ProxyNode(
    // 本地数据库自增主键，新增节点时保持 0，由 Room 自动生成。
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    // 用户可见备注名，不包含真实服务端敏感信息时才用于展示。
    val remark: String,
    // 节点协议类型，用于决定后续配置生成逻辑。
    val protocol: ProtocolType,
    // 服务器域名或 IP；真实值只来自用户输入或本地存储，不写入代码常量。
    val address: String,
    // 服务器端口。
    val port: Int,
    // VLESS/VMESS 常用用户标识；Trojan/Shadowsocks 可为空。
    val uuid: String? = null,
    // Trojan/Shadowsocks 常用密码；VLESS/VMESS 可为空。
    val password: String? = null,
    // Shadowsocks 加密方法；其他协议通常为空。
    val method: String? = null,
    // 传输网络类型，例如 tcp、ws、grpc，按协议配置需要填写。
    val network: String? = null,
    // 传输安全类型，例如 tls、reality、none。
    val security: String? = null,
    // TLS/Reality 的 SNI 或 serverName。
    val sni: String? = null,
    // WebSocket/gRPC 等传输层 Host 头或 authority。
    val host: String? = null,
    // WebSocket path 或其他路径类传输参数。
    val path: String? = null,
    // VLESS Reality/XTLS 等场景使用的 flow 参数。
    val flow: String? = null,
    // TLS ALPN 列表，例如 h2、http/1.1。
    val alpn: List<String> = emptyList(),
    // 是否允许不安全证书，默认关闭。
    val allowInsecure: Boolean = false,
    // 保留扩展字段，只存协议特有的非敏感参数，不存真实密码、订阅 URL 或完整分享链接。
    val extra: Map<String, String> = emptyMap(),
)
