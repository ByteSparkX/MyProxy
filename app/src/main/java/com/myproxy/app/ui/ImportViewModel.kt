package com.myproxy.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.myproxy.app.data.NodeRepository
import com.myproxy.app.data.SubscriptionRepository
import com.myproxy.app.data.parser.ShareLinkParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ImportViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val nodeRepository = NodeRepository.getInstance(application)
    private val subscriptionRepository = SubscriptionRepository.getInstance(application)
    private val mutableState = MutableStateFlow(ImportUiState())

    val state: StateFlow<ImportUiState> = mutableState.asStateFlow()

    fun importShareLink(link: String) {
        val normalized = link.trim()
        if (normalized.isBlank()) {
            mutableState.update {
                it.copy(shareError = "分享链接为空", shareMessage = null)
            }
            return
        }

        viewModelScope.launch {
            mutableState.update { it.copy(shareLoading = true, shareError = null, shareMessage = null) }
            val node = ShareLinkParser.parse(normalized)
            if (node == null) {
                mutableState.update {
                    it.copy(shareLoading = false, shareError = "分享链接解析失败", shareMessage = null)
                }
                return@launch
            }

            runCatching { nodeRepository.insert(node) }
                .onSuccess {
                    // 只显示协议和备注，避免把服务器、UUID、密码显示到结果信息里。
                    mutableState.update {
                        it.copy(
                            shareLoading = false,
                            shareError = null,
                            shareMessage = "已导入：${node.remark} · ${node.protocol}",
                        )
                    }
                }
                .onFailure {
                    mutableState.update {
                        it.copy(shareLoading = false, shareError = "节点保存失败", shareMessage = null)
                    }
                }
        }
    }

    fun importScannedText(rawValue: String) {
        val normalized = rawValue.trim()
        if (normalized.isBlank()) {
            mutableState.update {
                it.copy(scanError = "二维码内容为空", scanMessage = null)
            }
            return
        }

        viewModelScope.launch {
            mutableState.update { it.copy(scanLoading = true, scanError = null, scanMessage = null) }
            val node = ShareLinkParser.parse(normalized)
            if (node == null) {
                mutableState.update {
                    it.copy(scanLoading = false, scanError = "二维码不是支持的节点分享链接", scanMessage = null)
                }
                return@launch
            }

            runCatching { nodeRepository.insert(node) }
                .onSuccess {
                    mutableState.update {
                        it.copy(
                            scanLoading = false,
                            scanError = null,
                            scanMessage = "扫码导入成功：${node.remark} · ${node.protocol}",
                        )
                    }
                }
                .onFailure {
                    mutableState.update {
                        it.copy(scanLoading = false, scanError = "节点保存失败", scanMessage = null)
                    }
                }
        }
    }

    fun importSubscription(url: String) {
        val normalized = url.trim()
        if (normalized.isBlank()) {
            mutableState.update {
                it.copy(subscriptionError = "订阅 URL 为空", subscriptionMessage = null)
            }
            return
        }

        viewModelScope.launch {
            mutableState.update {
                it.copy(subscriptionLoading = true, subscriptionError = null, subscriptionMessage = null)
            }
            val result = subscriptionRepository.importFromUrl(normalized)
            mutableState.update {
                it.copy(
                    subscriptionLoading = false,
                    subscriptionError = result.errorMessage,
                    subscriptionMessage = if (result.errorMessage == null) {
                        "成功 ${result.successCount} 个，失败 ${result.failureCount} 个"
                    } else {
                        null
                    },
                )
            }
        }
    }
}

data class ImportUiState(
    val shareLoading: Boolean = false,
    val shareMessage: String? = null,
    val shareError: String? = null,
    val scanLoading: Boolean = false,
    val scanMessage: String? = null,
    val scanError: String? = null,
    val subscriptionLoading: Boolean = false,
    val subscriptionMessage: String? = null,
    val subscriptionError: String? = null,
)
