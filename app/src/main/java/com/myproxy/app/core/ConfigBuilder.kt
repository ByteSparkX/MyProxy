package com.myproxy.app.core

import com.myproxy.app.BuildConfig
import com.myproxy.app.model.ProtocolType
import com.myproxy.app.model.ProxyNode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object ConfigBuilder {
    private const val TAG = "ConfigBuilder"
    private const val LOCAL_SOCKS_HOST = "127.0.0.1"
    private const val LOCAL_SOCKS_PORT = 10808
    private const val OUTBOUND_PROXY_TAG = "proxy"
    private const val OUTBOUND_DIRECT_TAG = "direct"
    private const val OUTBOUND_BLOCK_TAG = "block"

    private val json = Json {
        prettyPrint = false
        encodeDefaults = true
    }

    fun build(
        node: ProxyNode,
        dnsServers: List<String> = DEFAULT_DNS_SERVERS,
    ): String {
        validateCommon(node)
        AppLog.i(TAG, "生成 Xray 配置：remark=${node.remark} protocol=${node.protocol}")

        val config = buildJsonObject {
            // log 控制核心日志级别，不包含节点敏感信息。
            put("log", buildLog())
            // inbounds 提供给 tun2socks 连接的本地 SOCKS 入站。
            put("inbounds", buildInbounds())
            // outbounds 包含目标代理、直连和阻断三类出口。
            put("outbounds", buildOutbounds(node))
            // routing 决定 DNS、私有地址等流量如何分流。
            put("routing", buildRouting())
            // dns 提供基础解析服务器，后续可按设置扩展。
            put("dns", buildDns(dnsServers.ifEmpty { DEFAULT_DNS_SERVERS }))
        }

        return json.encodeToString(JsonObject.serializer(), config)
    }

    private fun buildLog(): JsonObject = buildJsonObject {
        put("loglevel", if (BuildConfig.VERBOSE_LOGGING) "info" else "warning")
    }

    private fun buildInbounds(): JsonArray = buildJsonArray {
        add(buildJsonObject {
            put("tag", "local-socks")
            put("listen", LOCAL_SOCKS_HOST)
            put("port", LOCAL_SOCKS_PORT)
            put("protocol", "socks")
            put("settings", buildJsonObject {
                put("udp", true)
                put("auth", "noauth")
            })
        })
    }

    private fun buildOutbounds(node: ProxyNode): JsonArray = buildJsonArray {
        add(buildProxyOutbound(node))
        add(buildJsonObject {
            put("tag", OUTBOUND_DIRECT_TAG)
            put("protocol", "freedom")
        })
        add(buildJsonObject {
            put("tag", OUTBOUND_BLOCK_TAG)
            put("protocol", "blackhole")
        })
    }

    private fun buildProxyOutbound(node: ProxyNode): JsonObject {
        return buildJsonObject {
            put("tag", OUTBOUND_PROXY_TAG)
            put("protocol", node.protocol.toXrayProtocol())
            put("settings", buildOutboundSettings(node))
            buildStreamSettings(node)?.let { put("streamSettings", it) }
        }
    }

    private fun buildOutboundSettings(node: ProxyNode): JsonObject {
        return when (node.protocol) {
            ProtocolType.VLESS -> buildVlessSettings(node)
            ProtocolType.VMESS -> buildVmessSettings(node)
            ProtocolType.TROJAN -> buildTrojanSettings(node)
            ProtocolType.SHADOWSOCKS -> buildShadowsocksSettings(node)
        }
    }

    private fun buildVlessSettings(node: ProxyNode): JsonObject {
        val uuid = required(node.uuid, "VLESS 节点缺少 uuid")
        return buildJsonObject {
            put("vnext", buildJsonArray {
                add(buildJsonObject {
                    put("address", node.address)
                    put("port", node.port)
                    put("users", buildJsonArray {
                        add(buildJsonObject {
                            put("id", uuid)
                            put("encryption", "none")
                            node.flow?.takeIf(String::isNotBlank)?.let { put("flow", it) }
                        })
                    })
                })
            })
        }
    }

    private fun buildVmessSettings(node: ProxyNode): JsonObject {
        val uuid = required(node.uuid, "VMess 节点缺少 uuid")
        return buildJsonObject {
            put("vnext", buildJsonArray {
                add(buildJsonObject {
                    put("address", node.address)
                    put("port", node.port)
                    put("users", buildJsonArray {
                        add(buildJsonObject {
                            put("id", uuid)
                            put("alterId", node.extra["aid"]?.toIntOrNull() ?: 0)
                            put("security", node.method?.takeIf(String::isNotBlank) ?: "auto")
                        })
                    })
                })
            })
        }
    }

    private fun buildTrojanSettings(node: ProxyNode): JsonObject {
        val password = required(node.password, "Trojan 节点缺少 password")
        return buildJsonObject {
            put("servers", buildJsonArray {
                add(buildJsonObject {
                    put("address", node.address)
                    put("port", node.port)
                    put("password", password)
                })
            })
        }
    }

    private fun buildShadowsocksSettings(node: ProxyNode): JsonObject {
        val password = required(node.password, "Shadowsocks 节点缺少 password")
        val method = required(node.method, "Shadowsocks 节点缺少 method")
        return buildJsonObject {
            put("servers", buildJsonArray {
                add(buildJsonObject {
                    put("address", node.address)
                    put("port", node.port)
                    put("method", method)
                    put("password", password)
                })
            })
        }
    }

    private fun buildStreamSettings(node: ProxyNode): JsonObject? {
        val network = node.network?.takeIf(String::isNotBlank)?.toXrayNetwork()
        val security = node.security?.takeIf(String::isNotBlank)?.lowercase()
        if (network == null && security == null) return null

        return buildJsonObject {
            network?.let { put("network", it) }
            security?.let { value ->
                put("security", value)
                buildSecuritySettings(node, value)?.let { put("${value}Settings", it) }
            }
            buildNetworkSettings(node, network)?.forEach { key, value ->
                put(key, value)
            }
        }
    }

    private fun buildSecuritySettings(node: ProxyNode, security: String): JsonObject? {
        return when (security) {
            "tls" -> buildJsonObject {
                putIfNotBlank("serverName", node.sni)
                put("allowInsecure", node.allowInsecure)
                if (node.alpn.isNotEmpty()) {
                    put("alpn", JsonArray(node.alpn.map(::JsonPrimitive)))
                }
            }
            "reality" -> buildJsonObject {
                putIfNotBlank("serverName", node.sni)
                putIfNotBlank("fingerprint", node.extra["fingerprint"])
                putIfNotBlank("publicKey", node.extra["publicKey"] ?: node.extra["pbk"])
                putIfNotBlank("shortId", node.extra["shortId"] ?: node.extra["sid"])
                putIfNotBlank("spiderX", node.extra["spiderX"] ?: node.extra["spx"])
            }
            else -> null
        }
    }

    private fun buildNetworkSettings(node: ProxyNode, network: String?): Map<String, JsonObject>? {
        return when (network?.lowercase()) {
            "ws" -> mapOf(
                "wsSettings" to buildJsonObject {
                    putIfNotBlank("path", node.path)
                    val headers = buildHeaders(node.host)
                    if (headers.isNotEmpty()) put("headers", JsonObject(headers))
                },
            )
            "grpc" -> mapOf(
                "grpcSettings" to buildJsonObject {
                    putIfNotBlank("serviceName", node.path?.trimStart('/'))
                    putIfNotBlank("authority", node.host)
                },
            )
            "http", "h2" -> mapOf(
                "httpSettings" to buildJsonObject {
                    putIfNotBlank("path", node.path)
                    node.host?.takeIf(String::isNotBlank)?.let {
                        put("host", buildJsonArray { add(it) })
                    }
                },
            )
            "tcp" -> {
                val headerType = node.extra["type"]?.takeIf(String::isNotBlank)
                if (headerType == null || headerType == "none") {
                    null
                } else {
                    mapOf(
                        "tcpSettings" to buildJsonObject {
                            put("header", buildJsonObject {
                                put("type", headerType)
                            })
                        },
                    )
                }
            }
            else -> null
        }
    }

    private fun buildRouting(): JsonObject = buildJsonObject {
        put("domainStrategy", "AsIs")
        put("rules", buildJsonArray {
            add(buildJsonObject {
                put("type", "field")
                put("ip", buildJsonArray {
                    add("geoip:private")
                })
                put("outboundTag", OUTBOUND_DIRECT_TAG)
            })
            add(buildJsonObject {
                put("type", "field")
                put("protocol", buildJsonArray {
                    add("bittorrent")
                })
                put("outboundTag", OUTBOUND_BLOCK_TAG)
            })
        })
    }

    private fun buildDns(dnsServers: List<String>): JsonObject = buildJsonObject {
        put("servers", buildJsonArray {
            dnsServers.forEach { server ->
                add(server)
            }
        })
    }

    private fun validateCommon(node: ProxyNode) {
        require(node.address.isNotBlank()) { "节点缺少 address" }
        require(node.port in 1..65535) { "节点 port 不合法" }

        if (node.security.equals("reality", ignoreCase = true)) {
            required(node.sni, "Reality 节点缺少 sni")
            required(
                node.extra["publicKey"] ?: node.extra["pbk"],
                "Reality 节点缺少 publicKey",
            )
        }
    }

    private fun required(value: String?, message: String): String {
        return value?.takeIf(String::isNotBlank) ?: throw IllegalArgumentException(message)
    }

    private fun ProtocolType.toXrayProtocol(): String {
        return when (this) {
            ProtocolType.VLESS -> "vless"
            ProtocolType.VMESS -> "vmess"
            ProtocolType.TROJAN -> "trojan"
            ProtocolType.SHADOWSOCKS -> "shadowsocks"
        }
    }

    private fun String.toXrayNetwork(): String {
        // 分享链接常用 h2 表示 HTTP/2，Xray streamSettings 中统一写为 http。
        return when (lowercase()) {
            "h2" -> "http"
            else -> this
        }
    }

    private fun buildHeaders(host: String?): Map<String, JsonElement> {
        val hostValue = host?.takeIf(String::isNotBlank) ?: return emptyMap()
        return mapOf("Host" to JsonPrimitive(hostValue))
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.putIfNotBlank(key: String, value: String?) {
        value?.takeIf(String::isNotBlank)?.let { put(key, it) }
    }

    private val DEFAULT_DNS_SERVERS = listOf("1.1.1.1", "8.8.8.8")
}
