package com.myproxy.desktop.core

import com.myproxy.desktop.model.ProtocolType
import com.myproxy.desktop.model.ProxyNode
import com.myproxy.desktop.model.RoutingMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object DesktopConfigBuilder {
    const val SOCKS_PORT = 10808
    const val HTTP_PORT = 10809
    private const val PROXY_TAG = "proxy"
    private const val DIRECT_TAG = "direct"
    private const val BLOCK_TAG = "block"
    private val json = Json { encodeDefaults = true }

    fun build(node: ProxyNode?, mode: RoutingMode): String {
        if (mode != RoutingMode.DIRECT) validate(requireNotNull(node) { "请先选择节点" })
        val config = buildJsonObject {
            put("log", buildJsonObject {
                put("loglevel", "warning")
                put("access", "none")
            })
            put("inbounds", buildInbounds())
            put("outbounds", buildOutbounds(node, mode))
            put("routing", buildRouting(mode))
            put("dns", buildJsonObject {
                put("servers", buildJsonArray {
                    add("1.1.1.1")
                    add("8.8.8.8")
                })
            })
        }
        return json.encodeToString(JsonObject.serializer(), config)
    }

    private fun buildInbounds(): JsonArray = buildJsonArray {
        add(buildJsonObject {
            put("tag", "local-socks")
            put("listen", "127.0.0.1")
            put("port", SOCKS_PORT)
            put("protocol", "socks")
            put("settings", buildJsonObject {
                put("auth", "noauth")
                put("udp", true)
            })
            put("sniffing", sniffing())
        })
        add(buildJsonObject {
            put("tag", "local-http")
            put("listen", "127.0.0.1")
            put("port", HTTP_PORT)
            put("protocol", "http")
            put("settings", buildJsonObject { })
            put("sniffing", sniffing())
        })
    }

    private fun sniffing(): JsonObject = buildJsonObject {
        put("enabled", true)
        put("destOverride", buildJsonArray {
            add("http")
            add("tls")
            add("quic")
        })
        put("routeOnly", true)
    }

    private fun buildOutbounds(node: ProxyNode?, mode: RoutingMode): JsonArray = buildJsonArray {
        if (mode != RoutingMode.DIRECT) add(proxyOutbound(requireNotNull(node)))
        add(buildJsonObject {
            put("tag", DIRECT_TAG)
            put("protocol", "freedom")
        })
        add(buildJsonObject {
            put("tag", BLOCK_TAG)
            put("protocol", "blackhole")
        })
    }

    private fun proxyOutbound(node: ProxyNode): JsonObject = buildJsonObject {
        put("tag", PROXY_TAG)
        put("protocol", node.protocol.name.lowercase())
        put("settings", outboundSettings(node))
        streamSettings(node)?.let { put("streamSettings", it) }
    }

    private fun outboundSettings(node: ProxyNode): JsonObject = when (node.protocol) {
        ProtocolType.VLESS -> buildJsonObject {
            put("vnext", buildJsonArray {
                add(buildJsonObject {
                    put("address", node.address)
                    put("port", node.port)
                    put("users", buildJsonArray {
                        add(buildJsonObject {
                            put("id", required(node.uuid, "VLESS 节点缺少 UUID"))
                            put("encryption", "none")
                            node.flow?.takeIf(String::isNotBlank)?.let { put("flow", it) }
                        })
                    })
                })
            })
        }
        ProtocolType.VMESS -> buildJsonObject {
            put("vnext", buildJsonArray {
                add(buildJsonObject {
                    put("address", node.address)
                    put("port", node.port)
                    put("users", buildJsonArray {
                        add(buildJsonObject {
                            put("id", required(node.uuid, "VMess 节点缺少 UUID"))
                            put("alterId", node.extra["aid"]?.toIntOrNull() ?: 0)
                            put("security", node.method?.takeIf(String::isNotBlank) ?: "auto")
                        })
                    })
                })
            })
        }
        ProtocolType.TROJAN -> buildJsonObject {
            put("servers", buildJsonArray {
                add(buildJsonObject {
                    put("address", node.address)
                    put("port", node.port)
                    put("password", required(node.password, "Trojan 节点缺少密码"))
                })
            })
        }
        ProtocolType.SHADOWSOCKS -> buildJsonObject {
            put("servers", buildJsonArray {
                add(buildJsonObject {
                    put("address", node.address)
                    put("port", node.port)
                    put("method", required(node.method, "Shadowsocks 节点缺少加密方式"))
                    put("password", required(node.password, "Shadowsocks 节点缺少密码"))
                })
            })
        }
    }

    private fun streamSettings(node: ProxyNode): JsonObject? {
        val network = node.network?.takeIf(String::isNotBlank)?.lowercase()?.let {
            if (it == "h2") "http" else it
        }
        val security = node.security?.takeIf(String::isNotBlank)?.lowercase()
        if (network == null && security == null) return null
        return buildJsonObject {
            network?.let { put("network", it) }
            security?.let { value ->
                put("security", value)
                securitySettings(node, value)?.let { put("${value}Settings", it) }
            }
            networkSettings(node, network)?.forEach { (key, value) -> put(key, value) }
        }
    }

    private fun securitySettings(node: ProxyNode, security: String): JsonObject? = when (security) {
        "tls" -> buildJsonObject {
            putIfNotBlank("serverName", node.sni)
            put("allowInsecure", node.allowInsecure)
            if (node.alpn.isNotEmpty()) put("alpn", JsonArray(node.alpn.map(::JsonPrimitive)))
        }
        "reality" -> buildJsonObject {
            putIfNotBlank("serverName", node.sni)
            putIfNotBlank("fingerprint", node.extra["fingerprint"])
            putIfNotBlank("publicKey", node.extra["publicKey"])
            putIfNotBlank("shortId", node.extra["shortId"])
            putIfNotBlank("spiderX", node.extra["spiderX"])
        }
        else -> null
    }

    private fun networkSettings(node: ProxyNode, network: String?): Map<String, JsonObject>? =
        when (network) {
            "ws" -> mapOf("wsSettings" to buildJsonObject {
                putIfNotBlank("path", node.path)
                node.host?.takeIf(String::isNotBlank)?.let {
                    put("headers", buildJsonObject { put("Host", it) })
                }
            })
            "grpc" -> mapOf("grpcSettings" to buildJsonObject {
                putIfNotBlank("serviceName", node.path?.trimStart('/'))
                putIfNotBlank("authority", node.host)
            })
            "http" -> mapOf("httpSettings" to buildJsonObject {
                putIfNotBlank("path", node.path)
                node.host?.takeIf(String::isNotBlank)?.let {
                    put("host", buildJsonArray { add(it) })
                }
            })
            "tcp" -> node.extra["type"]?.takeUnless { it == "none" }?.let { headerType ->
                mapOf("tcpSettings" to buildJsonObject {
                    put("header", buildJsonObject { put("type", headerType) })
                })
            }
            else -> null
        }

    private fun buildRouting(mode: RoutingMode): JsonObject = buildJsonObject {
        put("domainStrategy", if (mode == RoutingMode.RULE) "IPIfNonMatch" else "AsIs")
        put("rules", buildJsonArray {
            add(buildJsonObject {
                put("type", "field")
                put("protocol", buildJsonArray { add("bittorrent") })
                put("outboundTag", BLOCK_TAG)
            })
            when (mode) {
                RoutingMode.RULE -> {
                    add(routeRule("ip", "geoip:private", DIRECT_TAG))
                    add(routeRule("domain", "geosite:gfw", PROXY_TAG))
                    add(routeRule("domain", "geosite:cn", DIRECT_TAG))
                    add(routeRule("ip", "geoip:cn", DIRECT_TAG))
                    add(catchAll(DIRECT_TAG))
                }
                RoutingMode.GLOBAL -> add(catchAll(PROXY_TAG))
                RoutingMode.DIRECT -> add(catchAll(DIRECT_TAG))
            }
        })
    }

    private fun routeRule(field: String, value: String, outbound: String): JsonObject = buildJsonObject {
        put("type", "field")
        put(field, buildJsonArray { add(value) })
        put("outboundTag", outbound)
    }

    private fun catchAll(outbound: String): JsonObject = buildJsonObject {
        put("type", "field")
        put("network", "tcp,udp")
        put("outboundTag", outbound)
    }

    private fun validate(node: ProxyNode) {
        require(node.address.isNotBlank()) { "节点缺少服务器地址" }
        require(node.port in 1..65535) { "节点端口不合法" }
        if (node.security.equals("reality", true)) {
            required(node.sni, "Reality 节点缺少 SNI")
            required(node.extra["publicKey"], "Reality 节点缺少 publicKey")
        }
    }

    private fun required(value: String?, message: String): String =
        value?.takeIf(String::isNotBlank) ?: throw IllegalArgumentException(message)

    private fun kotlinx.serialization.json.JsonObjectBuilder.putIfNotBlank(key: String, value: String?) {
        value?.takeIf(String::isNotBlank)?.let { put(key, it) }
    }
}
