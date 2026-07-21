package com.myproxy.app.data

import android.content.Context
import android.util.Base64
import com.myproxy.app.data.parser.ShareLinkParser
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

data class SubscriptionImportResult(
    val successCount: Int,
    val failureCount: Int,
    val errorMessage: String? = null,
)

class SubscriptionRepository private constructor(
    private val nodeRepository: NodeRepository,
    private val httpClient: OkHttpClient,
) {
    suspend fun importFromUrl(subscriptionUrl: String): SubscriptionImportResult = withContext(Dispatchers.IO) {
        val normalizedUrl = subscriptionUrl.trim()
        if (normalizedUrl.isBlank()) {
            return@withContext SubscriptionImportResult(
                successCount = 0,
                failureCount = 0,
                errorMessage = "订阅 URL 为空",
            )
        }

        runCatching {
            // 只发起直连 GET 请求，不在日志中输出订阅 URL。
            val request = Request.Builder()
                .url(normalizedUrl)
                .header("User-Agent", USER_AGENT)
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext SubscriptionImportResult(
                        successCount = 0,
                        failureCount = 0,
                        errorMessage = "订阅请求失败，HTTP ${response.code}",
                    )
                }

                val responseText = response.body?.string().orEmpty()
                val lines = extractShareLinks(responseText)
                // 解析失败的行会被过滤，避免非法订阅内容导致应用崩溃。
                val parsedNodes = lines.mapNotNull { ShareLinkParser.parse(it) }

                if (parsedNodes.isNotEmpty()) {
                    // 仅写入解析后的节点对象，不打印密码、UUID 或原始分享链接。
                    nodeRepository.insertAll(parsedNodes)
                }

                SubscriptionImportResult(
                    successCount = parsedNodes.size,
                    failureCount = lines.size - parsedNodes.size,
                    errorMessage = null,
                )
            }
        }.getOrElse { error ->
            SubscriptionImportResult(
                successCount = 0,
                failureCount = 0,
                errorMessage = error.safeMessage(),
            )
        }
    }

    private fun extractShareLinks(responseText: String): List<String> {
        val decoded = decodeBase64ToString(responseText)
        val content = if (decoded != null && containsShareScheme(decoded)) {
            decoded
        } else {
            responseText
        }

        // 订阅内容按行拆分；不记录原始行，避免泄露密码、UUID 或订阅来源。
        return content
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() && containsShareScheme(it) }
            .toList()
    }

    private fun decodeBase64ToString(value: String): String? {
        val compact = value.filterNot { it.isWhitespace() }
            .replace('-', '+')
            .replace('_', '/')
            .let(::withBase64Padding)

        if (compact.isBlank()) return null

        return runCatching {
            String(Base64.decode(compact, Base64.DEFAULT), Charsets.UTF_8)
        }.getOrNull()
    }

    private fun withBase64Padding(value: String): String {
        val remainder = value.length % 4
        return if (remainder == 0) value else value + "=".repeat(4 - remainder)
    }

    private fun containsShareScheme(value: String): Boolean {
        return value.contains("vmess://", ignoreCase = true) ||
            value.contains("vless://", ignoreCase = true) ||
            value.contains("trojan://", ignoreCase = true) ||
            value.contains("ss://", ignoreCase = true)
    }

    private fun Throwable.safeMessage(): String {
        return when (this) {
            is IllegalArgumentException -> "订阅 URL 格式不正确"
            is IOException -> "订阅网络请求失败"
            else -> "订阅导入失败"
        }
    }

    companion object {
        private const val USER_AGENT = "MyProxy/0.1 Android"

        @Volatile
        private var instance: SubscriptionRepository? = null

        fun getInstance(context: Context): SubscriptionRepository {
            return instance ?: synchronized(this) {
                instance ?: SubscriptionRepository(
                    nodeRepository = NodeRepository.getInstance(context.applicationContext),
                    httpClient = OkHttpClient.Builder()
                        // 默认直连，不配置代理；超时避免网络异常长期挂起。
                        .connectTimeout(15, TimeUnit.SECONDS)
                        .readTimeout(20, TimeUnit.SECONDS)
                        .writeTimeout(20, TimeUnit.SECONDS)
                        .callTimeout(30, TimeUnit.SECONDS)
                        .build(),
                ).also { instance = it }
            }
        }
    }
}
