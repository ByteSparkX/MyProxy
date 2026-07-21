package com.myproxy.desktop.data

import com.myproxy.desktop.model.ImportResult
import com.myproxy.desktop.model.ProxyNode
import okhttp3.OkHttpClient
import okhttp3.Request
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.Base64

class SubscriptionImporter {
    private val client = OkHttpClient.Builder()
        .connectTimeout(Duration.ofSeconds(15))
        .readTimeout(Duration.ofSeconds(30))
        .callTimeout(Duration.ofSeconds(45))
        .build()

    fun import(value: String): Pair<List<ProxyNode>, ImportResult> {
        val trimmed = value.trim()
        if (trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true)) {
            return importSubscription(trimmed)
        }
        val node = ShareLinkParser.parse(trimmed)
        return if (node == null) {
            emptyList<ProxyNode>() to ImportResult(0, 1, "无法识别分享链接")
        } else {
            listOf(node) to ImportResult(1, 0, "节点已导入")
        }
    }

    private fun importSubscription(url: String): Pair<List<ProxyNode>, ImportResult> {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "MyProxy-Desktop/1.1")
            .get()
            .build()
        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return emptyList<ProxyNode>() to ImportResult(
                        0,
                        0,
                        "订阅请求失败：HTTP ${response.code}",
                    )
                }
                val raw = response.body?.string().orEmpty()
                val payload = if (containsLinks(raw)) raw else decodeBase64(raw) ?: raw
                val lines = payload.lineSequence().map(String::trim).filter(String::isNotBlank).toList()
                val nodes = lines.mapNotNull(ShareLinkParser::parse)
                nodes to ImportResult(
                    successCount = nodes.size,
                    failedCount = (lines.size - nodes.size).coerceAtLeast(0),
                    message = if (nodes.isEmpty()) "订阅中没有可识别节点" else "已导入 ${nodes.size} 个节点",
                )
            }
        }.getOrElse {
            emptyList<ProxyNode>() to ImportResult(0, 0, "订阅请求失败，请检查网络和地址")
        }
    }

    private fun containsLinks(value: String): Boolean = listOf("vmess://", "vless://", "trojan://", "ss://")
        .any { value.contains(it, true) }

    private fun decodeBase64(value: String): String? {
        val normalized = value.filterNot(Char::isWhitespace).replace('-', '+').replace('_', '/').let {
            it + "=".repeat((4 - it.length % 4) % 4)
        }
        return runCatching {
            String(Base64.getDecoder().decode(normalized), StandardCharsets.UTF_8)
        }.getOrNull()
    }
}
