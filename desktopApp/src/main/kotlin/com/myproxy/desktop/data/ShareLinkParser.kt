package com.myproxy.desktop.data

import com.myproxy.desktop.model.ProtocolType
import com.myproxy.desktop.model.ProxyNode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Base64

object ShareLinkParser {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun parse(link: String): ProxyNode? {
        val value = link.trim()
        if (value.isEmpty()) return null
        return runCatching {
            when {
                value.startsWith("vmess://", true) -> parseVmess(value)
                value.startsWith("vless://", true) -> parseStandardUri(value, ProtocolType.VLESS)
                value.startsWith("trojan://", true) -> parseStandardUri(value, ProtocolType.TROJAN)
                value.startsWith("ss://", true) -> parseShadowsocks(value)
                else -> null
            }
        }.getOrNull()
    }

    private fun parseVmess(link: String): ProxyNode? {
        val decoded = decodeBase64(link.substringAfter("://")) ?: return null
        val value = json.parseToJsonElement(decoded) as? JsonObject ?: return null
        val address = value.text("add")?.takeIf(String::isNotBlank) ?: return null
        val port = value.text("port")?.toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
        val uuid = value.text("id")?.takeIf(String::isNotBlank) ?: return null
        return ProxyNode(
            remark = value.text("ps")?.takeIf(String::isNotBlank) ?: address,
            protocol = ProtocolType.VMESS,
            address = address,
            port = port,
            uuid = uuid,
            method = value.text("scy"),
            network = value.text("net"),
            security = value.text("tls")?.takeIf(String::isNotBlank) ?: "none",
            sni = value.text("sni"),
            host = value.text("host"),
            path = value.text("path"),
            alpn = parseAlpn(value.text("alpn")),
            allowInsecure = parseBoolean(value.text("allowInsecure")),
            extra = buildMap {
                value.text("aid")?.let { put("aid", it) }
                value.text("type")?.let { put("type", it) }
            },
        )
    }

    private fun parseStandardUri(link: String, protocol: ProtocolType): ProxyNode? {
        val uri = URI(link)
        val credential = decode(uri.rawUserInfo)?.takeIf(String::isNotBlank) ?: return null
        val address = uri.host?.takeIf(String::isNotBlank) ?: return null
        val port = uri.port.takeIf { it in 1..65535 } ?: return null
        val query = parseQuery(uri.rawQuery)
        val security = query["security"] ?: if (protocol == ProtocolType.TROJAN) "tls" else null
        return ProxyNode(
            remark = decode(uri.rawFragment)?.takeIf(String::isNotBlank) ?: address,
            protocol = protocol,
            address = address,
            port = port,
            uuid = credential.takeIf { protocol == ProtocolType.VLESS },
            password = credential.takeIf { protocol == ProtocolType.TROJAN },
            network = query["type"],
            security = security,
            sni = query["sni"],
            host = query["host"],
            path = query["path"] ?: query["serviceName"],
            flow = query["flow"],
            alpn = parseAlpn(query["alpn"]),
            allowInsecure = parseBoolean(query["allowInsecure"]),
            extra = buildMap {
                query["fp"]?.let { put("fingerprint", it) }
                (query["pbk"] ?: query["publicKey"])?.let { put("publicKey", it) }
                (query["sid"] ?: query["shortId"])?.let { put("shortId", it) }
                (query["spx"] ?: query["spiderX"])?.let { put("spiderX", it) }
                query["headerType"]?.let { put("type", it) }
            },
        )
    }

    private fun parseShadowsocks(link: String): ProxyNode? {
        val body = link.substringAfter("://")
        val remark = decode(body.substringAfter("#", "")).takeIf(String::isNotBlank)
        val main = body.substringBefore("#").substringBefore("?")
        val decodedMain = if ("@" in main) main else decodeBase64(main) ?: return null
        val userInfoRaw = decodedMain.substringBefore("@", "")
        val serverPart = decodedMain.substringAfter("@", "")
        if (userInfoRaw.isBlank() || serverPart.isBlank()) return null
        val userInfo = if (":" in userInfoRaw) userInfoRaw else decodeBase64(userInfoRaw) ?: return null
        val method = decode(userInfo.substringBefore(":"))
        val password = decode(userInfo.substringAfter(":", ""))
        val address = parseHost(serverPart) ?: return null
        val port = serverPart.substringAfterLast(":", "").toIntOrNull()?.takeIf { it in 1..65535 }
            ?: return null
        if (method.isBlank() || password.isBlank()) return null
        return ProxyNode(
            remark = remark ?: address,
            protocol = ProtocolType.SHADOWSOCKS,
            address = address,
            port = port,
            password = password,
            method = method,
        )
    }

    private fun parseHost(serverPart: String): String? {
        val raw = serverPart.substringBeforeLast(":", "").removePrefix("[").removeSuffix("]")
        return decode(raw).takeIf(String::isNotBlank)
    }

    private fun parseQuery(rawQuery: String?): Map<String, String> {
        if (rawQuery.isNullOrBlank()) return emptyMap()
        return rawQuery.split("&").mapNotNull { item ->
            val key = decode(item.substringBefore("="))
            val value = decode(item.substringAfter("=", ""))
            key.takeIf(String::isNotBlank)?.let { it to value }
        }.toMap()
    }

    private fun parseAlpn(value: String?): List<String> = value
        ?.split(",", "|")
        ?.map(String::trim)
        ?.filter(String::isNotBlank)
        ?: emptyList()

    private fun parseBoolean(value: String?): Boolean =
        value == "1" || value.equals("true", true)

    private fun decodeBase64(value: String): String? {
        val normalized = value.trim().replace('-', '+').replace('_', '/').let {
            it + "=".repeat((4 - it.length % 4) % 4)
        }
        return runCatching {
            String(Base64.getDecoder().decode(normalized), StandardCharsets.UTF_8)
        }.getOrNull()
    }

    private fun decode(value: String?): String = value?.let {
        runCatching { URLDecoder.decode(it, StandardCharsets.UTF_8) }.getOrDefault(it)
    }.orEmpty()

    private fun JsonObject.text(name: String): String? = this[name]?.jsonPrimitive?.contentOrNull
}
