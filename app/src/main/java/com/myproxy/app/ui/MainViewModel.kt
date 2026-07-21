package com.myproxy.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.myproxy.app.core.AppLog
import com.myproxy.app.core.ConfigBuilder
import com.myproxy.app.data.NodeRepository
import com.myproxy.app.data.SettingsRepository
import com.myproxy.app.model.ProxyNode
import com.myproxy.app.model.RoutingMode
import com.myproxy.app.service.TrafficStatsRepository
import com.myproxy.app.service.TrafficStatsState
import com.myproxy.app.service.VpnConnectionState
import com.myproxy.app.service.VpnState
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

class MainViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val nodeRepository = NodeRepository.getInstance(application)
    private val settingsRepository = SettingsRepository.getInstance(application)

    // Room 节点列表转为 StateFlow，界面重建后会立即拿到最近一次数据。
    val nodes: StateFlow<List<ProxyNode>> = nodeRepository.observeAll()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    // DataStore 保存当前选中节点 id，重启应用后仍能恢复。
    val selectedNodeId: StateFlow<Long?> = settingsRepository.selectedNodeId
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    val routingMode: StateFlow<RoutingMode> = settingsRepository.routingMode
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = RoutingMode.RULE,
        )

    // VPN 服务通过单例 StateFlow 上报真实连接状态，UI 只订阅这一份状态。
    val connectionState: StateFlow<VpnState> = VpnConnectionState.state
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = VpnConnectionState.state.value,
        )

    val trafficStats: StateFlow<TrafficStatsState> = TrafficStatsRepository.state
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = TrafficStatsRepository.state.value,
        )

    private val mutableLatencyResults = MutableStateFlow<Map<Long, NodeLatencyState>>(emptyMap())
    val latencyResults: StateFlow<Map<Long, NodeLatencyState>> = mutableLatencyResults.asStateFlow()

    fun selectNode(node: ProxyNode) {
        viewModelScope.launch {
            settingsRepository.setSelectedNodeId(node.id)
            AppLog.i(TAG, "已选择节点：remark=${node.remark} protocol=${node.protocol}")
        }
    }

    fun setRoutingMode(mode: RoutingMode) {
        viewModelScope.launch {
            settingsRepository.setRoutingMode(mode)
            AppLog.i(TAG, "已切换路由模式：mode=$mode")
        }
    }

    fun deleteNode(node: ProxyNode) {
        viewModelScope.launch {
            nodeRepository.deleteById(node.id)
            if (settingsRepository.getSelectedNodeId() == node.id) {
                settingsRepository.clearSelectedNodeId()
            }
            AppLog.i(TAG, "已删除节点：remark=${node.remark} protocol=${node.protocol}")
        }
    }

    fun updateNode(node: ProxyNode) {
        viewModelScope.launch {
            nodeRepository.update(node)
            AppLog.i(TAG, "已更新节点：remark=${node.remark} protocol=${node.protocol}")
        }
    }

    fun testNodeLatency(node: ProxyNode) {
        viewModelScope.launch {
            measureNodeLatency(node)
        }
    }

    fun testAllLatencies() {
        viewModelScope.launch {
            val semaphore = Semaphore(LATENCY_CONCURRENCY)
            coroutineScope {
                nodes.value.map { node ->
                    async {
                        semaphore.withPermit {
                            measureNodeLatency(node)
                        }
                    }
                }.awaitAll()
            }
        }
    }

    suspend fun canStartConnection(): Boolean {
        return settingsRepository.getRoutingMode() == RoutingMode.DIRECT ||
            settingsRepository.getSelectedNodeId() != null
    }

    suspend fun buildConnectionConfig(): BuildConfigResult {
        val routingMode = settingsRepository.getRoutingMode()
        val dnsServers = settingsRepository.getCustomDnsServers()
        if (routingMode == RoutingMode.DIRECT) {
            return runCatching {
                BuildConfigResult.Success(ConfigBuilder.buildDirect(dnsServers))
            }.getOrElse { error ->
                AppLog.e(TAG, "直连配置生成失败。", error)
                BuildConfigResult.Failure(error.message ?: "直连配置生成失败")
            }
        }

        val nodeId = settingsRepository.getSelectedNodeId()
            ?: return BuildConfigResult.Failure("请先选择一个节点")

        val node = nodeRepository.getById(nodeId)
        if (node == null) {
            settingsRepository.clearSelectedNodeId()
            return BuildConfigResult.Failure("选中的节点不存在，请重新选择")
        }

        return runCatching {
            // 只记录非敏感字段，完整配置包含密码/UUID，不能写入日志。
            AppLog.i(
                TAG,
                "准备使用节点连接：remark=${node.remark} protocol=${node.protocol} mode=$routingMode",
            )
            BuildConfigResult.Success(
                ConfigBuilder.build(
                    node = node,
                    dnsServers = dnsServers,
                    routingMode = routingMode,
                ),
            )
        }.getOrElse { error ->
            AppLog.e(TAG, "节点配置生成失败。", error)
            BuildConfigResult.Failure(error.message ?: "节点配置生成失败")
        }
    }

    fun setConnecting() {
        VpnConnectionState.setConnecting()
    }

    fun setDisconnected() {
        VpnConnectionState.setDisconnected()
    }

    fun setError(message: String) {
        VpnConnectionState.setError(message)
    }

    sealed interface BuildConfigResult {
        data class Success(val configJson: String) : BuildConfigResult
        data class Failure(val message: String) : BuildConfigResult
    }

    private suspend fun measureNodeLatency(node: ProxyNode) {
        mutableLatencyResults.update { current ->
            current + (node.id to NodeLatencyState(isTesting = true))
        }

        val result = withContext(Dispatchers.IO) {
            runCatching {
                var elapsedMs = 0L
                Socket().use { socket ->
                    elapsedMs = measureTimeMillis {
                        socket.connect(
                            InetSocketAddress(node.address, node.port),
                            LATENCY_TIMEOUT_MS,
                        )
                    }
                }
                NodeLatencyState(latencyMs = elapsedMs)
            }.getOrElse {
                NodeLatencyState(errorMessage = "失败")
            }
        }

        mutableLatencyResults.update { current -> current + (node.id to result) }
    }

    companion object {
        private const val TAG = "MainViewModel"
        private const val LATENCY_TIMEOUT_MS = 3_000
        private const val LATENCY_CONCURRENCY = 8
    }
}

data class NodeLatencyState(
    val latencyMs: Long? = null,
    val isTesting: Boolean = false,
    val errorMessage: String? = null,
)
