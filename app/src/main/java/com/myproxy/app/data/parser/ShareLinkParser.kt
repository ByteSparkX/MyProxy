package com.myproxy.app.data.parser

import android.net.Uri
import android.util.Base64
import com.myproxy.app.model.ProtocolType
import com.myproxy.app.model.ProxyNode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

object ShareLinkParser {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun parse(link: String): ProxyNode? {
        val normalized = link.trim()
        if (normalized.isBlank()) return null

        return runCatching {
            when {
                normalized.startsWith("vmess://", ignoreCase = true) -> parseVmess(normalized)
                normalized.startsWith("vless://", ignoreCase = true) -> parseVless(normalized)
                normalized.startsWith("trojan://", ignoreCase = true) -> parseTrojan(normalized)
                normalized.startsWith("ss://", ignoreCase = true) -> parseShadowsocks(normalized)
                else -> null
            }
        }.getOrNull()
    }

    private fun parseVmess(link: String): ProxyNode? {
        val payload = decodeText(link.substringAfter("://", missingDelimiterValue = "")) ?: return null
        val jsonText = decodeBase64ToString(payload) ?: return null
        val obj = json.parseToJsonElement(jsonText) as? JsonObject ?: return null

        val address = obj.stringValue("add")?.takeIf { it.isNotBlank() } ?: return null
        val port = obj.stringValue("port")?.toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
        val uuid = obj.stringValue("id")?.takeIf { it.isNotBlank() } ?: return null
        val network = obj.stringValue("net")
        val security = obj.stringValue("tls")?.takeIf { it.isNotBlank() } ?: "none"
        val remark = obj.stringValue("ps")?.takeIf { it.isNotBlank() } ?: address

        // VMess 的 aid、type 后续生成配置时仍可能需要，放入扩展字段。
        val extra = buildMap {
            obj.stringValue("aid")?.let { put("aid", it) }
            obj.stringValue("type")?.let { put("type", it) }
        }

        return ProxyNode(
            remark = remark,
            protocol = ProtocolType.VMESS,
            address = address,
            port = port,
            uuid = uuid,
            method = obj.stringValue("scy"),
            network = network,
            security = security,
            sni = obj.stringValue("sni"),
            host = obj.stringValue("host"),
            path = obj.stringValue("path"),
            alpn = parseAlpn(obj.stringValue("alpn")),
            allowInsecure = parseBooleanFlag(obj.stringValue("allowInsecure")),
            extra = extra,
        )
    }

    private fun parseVless(link: String): ProxyNode? {
        val uri = Uri.parse(link)
        val uuid = uri.userInfo?.takeIf { it.isNotBlank() } ?: return null
        val address = uri.host?.takeIf { it.isNotBlank() } ?: return null
        val port = safePort(uri) ?: return null

        return ProxyNode(
            remark = uri.fragment?.takeIf { it.isNotBlank() } ?: address,
            protocol = ProtocolType.VLESS,
            address = address,
            port = port,
            uuid = uuid,
            network = uri.queryValue("type"),
            security = uri.queryValue("security"),
            sni = uri.queryValue("sni"),
            host = uri.queryValue("host"),
            path = uri.queryValue("path") ?: uri.queryValue("serviceName"),
            flow = uri.queryValue("flow"),
            alpn = parseAlpn(uri.queryValue("alpn")),
            allowInsecure = parseBooleanFlag(uri.queryValue("allowInsecure")),
            extra = buildStreamExtra(uri),
        )
    }

    private fun parseTrojan(link: String): ProxyNode? {
        val uri = Uri.parse(link)
        val password = uri.userInfo?.takeIf { it.isNotBlank() } ?: return null
        val address = uri.host?.takeIf { it.isNotBlank() } ?: return null
        val port = safePort(uri) ?: return null

        return ProxyNode(
            remark = uri.fragment?.takeIf { it.isNotBlank() } ?: address,
            protocol = ProtocolType.TROJAN,
            address = address,
            port = port,
            password = password,
            network = uri.queryValue("type"),
            security = uri.queryValue("security") ?: "tls",
            sni = uri.queryValue("sni"),
            host = uri.queryValue("host"),
            path = uri.queryValue("path") ?: uri.queryValue("serviceName"),
            alpn = parseAlpn(uri.queryValue("alpn")),
            allowInsecure = parseBooleanFlag(uri.queryValue("allowInsecure")),
            extra = buildStreamExtra(uri),
        )
    }

    private fun parseShadowsocks(link: String): ProxyNode? {
        val body = link.removePrefixIgnoringCase("ss://")
        val fragment = body.substringAfter("#", missingDelimiterValue = "")
        val withoutFragment = body.substringBefore("#")
        val remark = decodeText(fragment)?.takeIf { it.isNotBlank() }

        val mainPart = withoutFragment.substringBefore("?")
        val queryPart = withoutFragment.substringAfter("?", missingDelimiterValue = "")

        return if ("@" in mainPart) {
            parseSip002Shadowsocks(mainPart, queryPart, remark)
        } else {
            parseLegacyShadowsocks(mainPart, remark)
        }
    }

    private fun parseSip002Shadowsocks(
        mainPart: String,
        queryPart: String,
        remark: String?,
    ): ProxyNode? {
        val userInfoRaw = mainPart.substringBefore("@")
        val serverPart = mainPart.substringAfter("@")
        val userInfo = if (":" in userInfoRaw) {
            userInfoRaw
        } else {
            decodeBase64ToString(userInfoRaw) ?: return null
        }

        val method = decodeText(userInfo.substringBefore(":"))?.takeIf { it.isNotBlank() } ?: return null
        val password = decodeText(userInfo.substringAfter(":", missingDelimiterValue = ""))
            ?.takeIf { it.isNotBlank() } ?: return null
        val address = parseHost(serverPart) ?: return null
        val port = parsePort(serverPart) ?: return null

        return ProxyNode(
            remark = remark ?: address,
            protocol = ProtocolType.SHADOWSOCKS,
            address = address,
            port = port,
            password = password,
            method = method,
            extra = parseQueryToMap(queryPart),
        )
    }

    private fun parseLegacyShadowsocks(mainPart: String, remark: String?): ProxyNode? {
        val decoded = decodeBase64ToString(mainPart) ?: return null
        val decodedRemark = decoded.substringAfter("#", missingDelimiterValue = "")
            .let(::decodeText)
            ?.takeIf { it.isNotBlank() }
        val decodedMainPart = decoded.substringBefore("#")
        val userInfo = decodedMainPart.substringBefore("@")
        val serverPart = decodedMainPart.substringAfter("@", missingDelimiterValue = "")

        val method = decodeText(userInfo.substringBefore(":"))?.takeIf { it.isNotBlank() } ?: return null
        val password = decodeText(userInfo.substringAfter(":", missingDelimiterValue = ""))
            ?.takeIf { it.isNotBlank() } ?: return null
        val address = parseHost(serverPart) ?: return null
        val port = parsePort(serverPart) ?: return null

        return ProxyNode(
            remark = remark ?: decodedRemark ?: address,
            protocol = ProtocolType.SHADOWSOCKS,
            address = address,
            port = port,
            password = password,
            method = method,
        )
    }

    private fun JsonObject.stringValue(name: String): String? {
        // VMess JSON 字段不是表单编码，不能把合法的 '+' 错当成空格。
        return this[name]?.jsonPrimitive?.contentOrNullCompat()
    }

    private fun kotlinx.serialization.json.JsonPrimitive.contentOrNullCompat(): String? {
        return runCatching { content }.getOrNull()
    }

    private fun Uri.queryValue(name: String): String? {
        // Android Uri 已按 URI 规则解码 query，继续用 URLDecoder 会造成二次解码。
        return runCatching { getQueryParameter(name) }.getOrNull()
    }

    private fun buildStreamExtra(uri: Uri): Map<String, String> = buildMap {
        // Reality 与传输层扩展参数会在 ConfigBuilder 中映射到 streamSettings。
        uri.queryValue("fp")?.takeIf(String::isNotBlank)?.let { put("fingerprint", it) }
        (uri.queryValue("pbk") ?: uri.queryValue("publicKey"))
            ?.takeIf(String::isNotBlank)
            ?.let { put("publicKey", it) }
        (uri.queryValue("sid") ?: uri.queryValue("shortId"))
            ?.takeIf(String::isNotBlank)
            ?.let { put("shortId", it) }
        (uri.queryValue("spx") ?: uri.queryValue("spiderX"))
            ?.takeIf(String::isNotBlank)
            ?.let { put("spiderX", it) }
        uri.queryValue("headerType")?.takeIf(String::isNotBlank)?.let { put("type", it) }
    }

    private fun safePort(uri: Uri): Int? {
        return runCatching { uri.port }.getOrNull()?.takeIf { it in 1..65535 }
    }

    private fun parseAlpn(value: String?): List<String> {
        return value
            ?.split(",", "|")
            ?.mapNotNull { decodeText(it)?.takeIf(String::isNotBlank) }
            ?: emptyList()
    }

    private fun parseBooleanFlag(value: String?): Boolean {
        return value == "1" || value.equals("true", ignoreCase = true)
    }

    private fun parseQueryToMap(query: String): Map<String, String> {
        if (query.isBlank()) return emptyMap()

        return query.split("&")
            .mapNotNull { item ->
                val key = decodeText(item.substringBefore("="))?.takeIf { it.isNotBlank() }
                val value = decodeText(item.substringAfter("=", missingDelimiterValue = ""))
                if (key == null || value == null) null else key to value
            }
            .toMap()
    }

    private fun parseHost(serverPart: String): String? {
        val value = serverPart.substringBeforeLast(":", missingDelimiterValue = "")
        return decodeText(value.removePrefix("[").removeSuffix("]"))?.takeIf { it.isNotBlank() }
    }

    private fun parsePort(serverPart: String): Int? {
        return serverPart.substringAfterLast(":", missingDelimiterValue = "")
            .toIntOrNull()
            ?.takeIf { it in 1..65535 }
    }

    private fun decodeBase64ToString(value: String): String? {
        val normalized = value
            .trim()
            .replace('-', '+')
            .replace('_', '/')
            .let(::withBase64Padding)

        return runCatching {
            String(Base64.decode(normalized, Base64.DEFAULT), Charsets.UTF_8)
        }.getOrNull()
    }

    private fun withBase64Padding(value: String): String {
        val remainder = value.length % 4
        return if (remainder == 0) value else value + "=".repeat(4 - remainder)
    }

    private fun decodeText(value: String?): String? {
        return value?.let { runCatching { Uri.decode(it) }.getOrDefault(it) }
    }

    private fun String.removePrefixIgnoringCase(prefix: String): String {
        return if (startsWith(prefix, ignoreCase = true)) substring(prefix.length) else this
    }
}
