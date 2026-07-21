package com.myproxy.desktop

import com.myproxy.desktop.core.DesktopConfigBuilder
import com.myproxy.desktop.core.XrayCoreManager
import com.myproxy.desktop.data.StateStore
import com.myproxy.desktop.data.SubscriptionImporter
import com.myproxy.desktop.model.ConnectionState
import com.myproxy.desktop.model.ConnectionStatus
import com.myproxy.desktop.model.DesktopState
import com.myproxy.desktop.model.ImportResult
import com.myproxy.desktop.model.RoutingMode
import com.myproxy.desktop.proxy.SystemProxyFactory
import com.myproxy.desktop.proxy.SystemProxyManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean

class DesktopController(
    private val store: StateStore = StateStore(),
    private val core: XrayCoreManager = XrayCoreManager(),
    private val systemProxy: SystemProxyManager = SystemProxyFactory.create(),
    private val importer: SubscriptionImporter = SubscriptionImporter(),
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutableState = MutableStateFlow(store.load())
    private val mutableConnection = MutableStateFlow(ConnectionState())
    private val mutableImporting = MutableStateFlow(false)
    private val operationMutex = Mutex()
    private val closed = AtomicBoolean(false)
    private var monitorJob: Job? = null

    val state: StateFlow<DesktopState> = mutableState.asStateFlow()
    val connection: StateFlow<ConnectionState> = mutableConnection.asStateFlow()
    val importing: StateFlow<Boolean> = mutableImporting.asStateFlow()

    init {
        scope.launch {
            runCatching { operationMutex.withLock { systemProxy.recoverIfNeeded() } }
                .onFailure {
                    mutableConnection.value = ConnectionState(
                        ConnectionStatus.ERROR,
                        "检测到上次异常退出，但系统代理恢复失败",
                    )
                }
        }
    }

    fun selectNode(id: String) {
        val changed = mutableState.value.selectedNodeId != id
        updateState { copy(selectedNodeId = id) }
        if (changed && mutableConnection.value.status == ConnectionStatus.CONNECTED) disconnect()
    }

    fun setRoutingMode(mode: RoutingMode) {
        val changed = mutableState.value.routingMode != mode
        updateState { copy(routingMode = mode) }
        if (changed && mutableConnection.value.status == ConnectionStatus.CONNECTED) disconnect()
    }

    fun deleteNode(id: String) {
        val deletingActiveNode = mutableState.value.selectedNodeId == id &&
            mutableConnection.value.status == ConnectionStatus.CONNECTED
        updateState {
            copy(
                nodes = nodes.filterNot { it.id == id },
                selectedNodeId = selectedNodeId.takeUnless { it == id },
            )
        }
        if (deletingActiveNode) disconnect()
    }

    fun import(value: String, onComplete: (ImportResult) -> Unit) {
        if (mutableImporting.value) return
        mutableImporting.value = true
        scope.launch {
            val (newNodes, result) = importer.import(value)
            if (newNodes.isNotEmpty()) {
                updateState {
                    val existingKeys = nodes.map { "${it.protocol}|${it.address}|${it.port}|${it.uuid}|${it.password}" }
                        .toHashSet()
                    val unique = newNodes.filter {
                        "${it.protocol}|${it.address}|${it.port}|${it.uuid}|${it.password}" !in existingKeys
                    }
                    copy(
                        nodes = nodes + unique,
                        selectedNodeId = selectedNodeId ?: unique.firstOrNull()?.id,
                    )
                }
            }
            mutableImporting.value = false
            onComplete(result)
        }
    }

    fun toggleConnection() {
        when (mutableConnection.value.status) {
            ConnectionStatus.CONNECTING -> Unit
            ConnectionStatus.CONNECTED -> disconnect()
            ConnectionStatus.DISCONNECTED, ConnectionStatus.ERROR -> connect()
        }
    }

    fun connect() {
        if (mutableConnection.value.status == ConnectionStatus.CONNECTING) return
        mutableConnection.value = ConnectionState(ConnectionStatus.CONNECTING, "正在启动")
        scope.launch {
            operationMutex.withLock {
                runCatching {
                    monitorJob?.cancel()
                    runCatching { systemProxy.restore() }
                    core.stop()
                    val snapshot = mutableState.value
                    if (snapshot.routingMode == RoutingMode.DIRECT) {
                        mutableConnection.value = ConnectionState(ConnectionStatus.CONNECTED, "直连模式已生效")
                        return@runCatching
                    }
                    val node = snapshot.nodes.firstOrNull { it.id == snapshot.selectedNodeId }
                        ?: throw IllegalStateException("请先选择节点")
                    core.start(DesktopConfigBuilder.build(node, snapshot.routingMode))
                    try {
                        systemProxy.enable(DesktopConfigBuilder.HTTP_PORT, DesktopConfigBuilder.SOCKS_PORT)
                    } catch (error: Throwable) {
                        core.stop()
                        runCatching { systemProxy.restore() }
                        throw error
                    }
                    mutableConnection.value = ConnectionState(ConnectionStatus.CONNECTED, "系统代理已启用")
                    startMonitor()
                }.onFailure { error ->
                    mutableConnection.value = ConnectionState(
                        ConnectionStatus.ERROR,
                        error.message?.takeIf(String::isNotBlank) ?: "连接失败",
                    )
                }
            }
        }
    }

    fun disconnect() {
        monitorJob?.cancel()
        monitorJob = null
        mutableConnection.value = ConnectionState(ConnectionStatus.CONNECTING, "正在断开")
        scope.launch {
            operationMutex.withLock {
                val restoreError = runCatching { systemProxy.restore() }.exceptionOrNull()
                core.stop()
                mutableConnection.value = if (restoreError == null) {
                    ConnectionState(ConnectionStatus.DISCONNECTED, "已断开并恢复系统代理")
                } else {
                    ConnectionState(ConnectionStatus.ERROR, "已停止内核，但系统代理恢复失败")
                }
            }
        }
    }

    fun close() {
        if (!closed.compareAndSet(false, true)) return
        runBlocking(Dispatchers.IO) {
            monitorJob?.cancel()
            operationMutex.withLock {
                runCatching { systemProxy.restore() }
                core.stop()
            }
        }
        scope.cancel()
    }

    private fun startMonitor() {
        monitorJob = scope.launch {
            while (true) {
                delay(1_000)
                if (!core.isRunning()) {
                    operationMutex.withLock {
                        runCatching { systemProxy.restore() }
                        mutableConnection.value = ConnectionState(ConnectionStatus.ERROR, "Xray 内核意外停止")
                    }
                    break
                }
            }
        }
    }

    private fun updateState(transform: DesktopState.() -> DesktopState) {
        mutableState.update(transform)
        store.save(mutableState.value)
    }
}
