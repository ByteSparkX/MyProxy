package com.myproxy.app.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.myproxy.app.R
import com.myproxy.app.core.AppLog
import com.myproxy.app.model.ProxyNode
import com.myproxy.app.service.MyVpnService
import com.myproxy.app.service.VpnUiState
import kotlinx.coroutines.launch

private const val TAG = "HomeScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenImport: () -> Unit,
    mainViewModel: MainViewModel = viewModel(),
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val nodes by mainViewModel.nodes.collectAsStateWithLifecycle()
    val selectedNodeId by mainViewModel.selectedNodeId.collectAsStateWithLifecycle()
    val vpnState by mainViewModel.connectionState.collectAsStateWithLifecycle()
    val trafficStats by mainViewModel.trafficStats.collectAsStateWithLifecycle()
    val latencyResults by mainViewModel.latencyResults.collectAsStateWithLifecycle()
    val isConnecting = vpnState.uiState == VpnUiState.CONNECTING
    val isConnected = vpnState.uiState == VpnUiState.CONNECTED
    var editingNode by remember { mutableStateOf<ProxyNode?>(null) }
    var pendingDeleteNode by remember { mutableStateOf<ProxyNode?>(null) }

    fun startVpnFlow(configJson: String) {
        runCatching {
            mainViewModel.setConnecting()
            ContextCompat.startForegroundService(
                context,
                Intent(context, MyVpnService::class.java).apply {
                    action = MyVpnService.ACTION_START
                    putExtra(MyVpnService.EXTRA_CONFIG_JSON, configJson)
                },
            )
            AppLog.i(TAG, "已发送 VPN 链路启动请求。")
        }.onFailure { error ->
            mainViewModel.setError(error.message ?: "启动失败")
            AppLog.e(TAG, "发送 VPN 链路启动请求失败", error)
            Toast.makeText(context, "启动失败", Toast.LENGTH_SHORT).show()
        }
    }

    suspend fun buildSelectedNodeConfig(): String? {
        return when (val result = mainViewModel.buildSelectedNodeConfig()) {
            is MainViewModel.BuildConfigResult.Success -> result.configJson
            is MainViewModel.BuildConfigResult.Failure -> {
                mainViewModel.setError(result.message)
                Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                null
            }
        }
    }

    fun startSelectedNodeFlow() {
        coroutineScope.launch {
            mainViewModel.setConnecting()
            val configJson = buildSelectedNodeConfig() ?: return@launch
            startVpnFlow(configJson)
        }
    }

    fun stopVpnFlow() {
        runCatching {
            context.startService(Intent(context, MyVpnService::class.java).apply {
                action = MyVpnService.ACTION_STOP
            })
            AppLog.i(TAG, "已发送 VPN 链路停止请求。")
        }.onFailure { error ->
            mainViewModel.setError(error.message ?: "停止失败")
            AppLog.e(TAG, "发送 VPN 链路停止请求失败", error)
            Toast.makeText(context, "停止失败", Toast.LENGTH_SHORT).show()
        }
    }

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // 用户同意 VPN 授权后，再读取当前选中节点并生成动态配置。
            startSelectedNodeFlow()
        } else {
            mainViewModel.setDisconnected()
            Toast.makeText(context, "未授予 VPN 权限，已取消连接", Toast.LENGTH_SHORT).show()
        }
    }

    suspend fun requestVpnPermissionThenStart() {
        if (!mainViewModel.hasSelectedNode()) {
            Toast.makeText(context, "请先选择一个节点", Toast.LENGTH_SHORT).show()
            return
        }

        // VpnService.prepare 返回 null 表示已授权；否则需要拉起系统授权页。
        val permissionIntent = VpnService.prepare(context)
        if (permissionIntent == null) {
            startSelectedNodeFlow()
        } else {
            mainViewModel.setConnecting()
            vpnPermissionLauncher.launch(permissionIntent)
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) {
            Toast.makeText(context, "未授予通知权限，连接仍会继续", Toast.LENGTH_SHORT).show()
        }
        coroutineScope.launch { requestVpnPermissionThenStart() }
    }

    fun requestPermissionsThenStart() {
        coroutineScope.launch {
            if (!mainViewModel.hasSelectedNode()) {
                Toast.makeText(context, "请先选择一个节点", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val needsNotificationPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) != PackageManager.PERMISSION_GRANTED

            if (needsNotificationPermission) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                requestVpnPermissionThenStart()
            }
        }
    }

    LaunchedEffect(vpnState.uiState, vpnState.errorMessage) {
        if (vpnState.uiState == VpnUiState.ERROR) {
            Toast.makeText(context, vpnState.errorMessage ?: "连接出错", Toast.LENGTH_SHORT).show()
        }
    }

    fun selectNode(node: ProxyNode) {
        mainViewModel.selectNode(node)
    }

    fun deleteNode(node: ProxyNode) {
        pendingDeleteNode = node
    }

    fun editNode(node: ProxyNode) {
        editingNode = node
    }

    editingNode?.let { node ->
        NodeEditDialog(
            node = node,
            onDismiss = { editingNode = null },
            onSave = { updatedNode ->
                mainViewModel.updateNode(updatedNode)
                editingNode = null
                Toast.makeText(context, "节点已更新，下次连接生效", Toast.LENGTH_SHORT).show()
            },
        )
    }

    pendingDeleteNode?.let { node ->
        AlertDialog(
            onDismissRequest = { pendingDeleteNode = null },
            title = { Text(text = "删除节点") },
            text = { Text(text = "确定删除“${node.remark.ifBlank { "未命名节点" }}”吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        mainViewModel.deleteNode(node)
                        pendingDeleteNode = null
                        Toast.makeText(context, "已删除节点", Toast.LENGTH_SHORT).show()
                    },
                ) {
                    Text(text = "删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteNode = null }) {
                    Text(text = "取消")
                }
            },
        )
    }

    // 主页只负责节点选择、授权和发送启停请求，真实链路生命周期由 MyVpnService 管理。
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(text = stringResource(id = R.string.app_name))
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val errorMessage = vpnState.errorMessage

            Text(
                text = when (vpnState.uiState) {
                    VpnUiState.DISCONNECTED -> "未连接"
                    VpnUiState.CONNECTING -> "连接中"
                    VpnUiState.CONNECTED -> "已连接"
                    VpnUiState.ERROR -> "出错"
                },
                style = MaterialTheme.typography.titleMedium,
            )

            if (vpnState.uiState == VpnUiState.ERROR && errorMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            TrafficStatsPanel(
                uploadSpeed = trafficStats.uploadSpeed,
                downloadSpeed = trafficStats.downloadSpeed,
                uploadTotal = trafficStats.uploadTotal,
                downloadTotal = trafficStats.downloadTotal,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp),
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "节点",
                    style = MaterialTheme.typography.titleMedium,
                )
                Row {
                    TextButton(onClick = { mainViewModel.testAllLatencies() }) {
                        Text(text = "全部测延")
                    }
                    TextButton(onClick = onOpenImport) {
                        Text(text = "导入")
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (nodes.isEmpty()) {
                EmptyNodeState(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .widthIn(max = 560.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .widthIn(max = 560.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(
                        items = nodes,
                        key = { node -> node.id },
                    ) { node ->
                        NodeSelectionRow(
                            node = node,
                            selected = node.id == selectedNodeId,
                            latencyState = latencyResults[node.id],
                            onClick = { selectNode(node) },
                            onTestLatency = { mainViewModel.testNodeLatency(node) },
                            onEdit = { editNode(node) },
                            onDelete = { deleteNode(node) },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp)
                    .defaultMinSize(minHeight = 56.dp),
                enabled = !isConnecting,
                onClick = {
                    if (isConnected) {
                        stopVpnFlow()
                    } else {
                        requestPermissionsThenStart()
                    }
                },
            ) {
                Text(
                    text = when (vpnState.uiState) {
                        VpnUiState.DISCONNECTED -> "未连接 · 点击连接"
                        VpnUiState.CONNECTING -> "连接中"
                        VpnUiState.CONNECTED -> "已连接 · 点击断开"
                        VpnUiState.ERROR -> "出错 · 重试连接"
                    },
                    style = MaterialTheme.typography.titleLarge,
                )
            }
        }
    }
}

@Composable
private fun NodeEditDialog(
    node: ProxyNode,
    onDismiss: () -> Unit,
    onSave: (ProxyNode) -> Unit,
) {
    var remark by remember(node.id) { mutableStateOf(node.remark) }
    var address by remember(node.id) { mutableStateOf(node.address) }
    var portText by remember(node.id) { mutableStateOf(node.port.toString()) }
    val port = portText.toIntOrNull()
    val canSave = address.isNotBlank() && port != null && port in 1..65535

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "编辑节点") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = node.protocol.name,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                OutlinedTextField(
                    value = remark,
                    onValueChange = { remark = it },
                    label = { Text(text = "备注") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it.trim() },
                    label = { Text(text = "服务器地址") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = portText,
                    onValueChange = { portText = it.filter(Char::isDigit).take(5) },
                    label = { Text(text = "端口") },
                    singleLine = true,
                    isError = portText.isNotBlank() && (port == null || port !in 1..65535),
                )
                Text(
                    text = "认证信息不会在此处显示或修改。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = {
                    onSave(
                        node.copy(
                            remark = remark.trim().ifBlank { address },
                            address = address,
                            port = requireNotNull(port),
                        ),
                    )
                },
            ) {
                Text(text = "保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "取消")
            }
        },
    )
}

@Composable
private fun NodeSelectionRow(
    node: ProxyNode,
    selected: Boolean,
    latencyState: NodeLatencyState?,
    onClick: () -> Unit,
    onTestLatency: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = selected,
                    onClick = onClick,
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp),
                ) {
                    // 列表展示基础识别信息，不展示 UUID、密码或订阅 URL。
                    Text(
                        text = node.remark.ifBlank { "未命名节点" },
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = node.protocol.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = node.address,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                LatencyLabel(latencyState = latencyState)
                Row {
                    TextButton(onClick = onTestLatency) {
                        Text(text = "测延")
                    }
                    TextButton(onClick = onEdit) {
                        Text(text = "编辑")
                    }
                    TextButton(onClick = onDelete) {
                        Text(text = "删除")
                    }
                }
            }
        }
    }
}

@Composable
private fun TrafficStatsPanel(
    uploadSpeed: Long,
    downloadSpeed: Long,
    uploadTotal: Long,
    downloadTotal: Long,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = "连接状态", style = MaterialTheme.typography.titleSmall)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(text = "上行 ${formatSpeed(uploadSpeed)}")
                Text(text = "下行 ${formatSpeed(downloadSpeed)}")
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(text = "已发 ${formatBytes(uploadTotal)}")
                Text(text = "已收 ${formatBytes(downloadTotal)}")
            }
        }
    }
}

@Composable
private fun LatencyLabel(
    latencyState: NodeLatencyState?,
) {
    val color = when {
        latencyState?.errorMessage != null -> MaterialTheme.colorScheme.error
        latencyState?.isTesting == true -> MaterialTheme.colorScheme.primary
        latencyState?.latencyMs == null -> MaterialTheme.colorScheme.onSurfaceVariant
        latencyState.latencyMs < 300L -> Color(0xFF2E7D32)
        latencyState.latencyMs < 800L -> Color(0xFFF57C00)
        else -> MaterialTheme.colorScheme.error
    }

    Text(
        text = when {
            latencyState?.isTesting == true -> "测量中"
            latencyState?.errorMessage != null -> latencyState.errorMessage
            latencyState?.latencyMs != null -> "${latencyState.latencyMs} ms"
            else -> "未测延"
        },
        style = MaterialTheme.typography.bodySmall,
        color = color,
    )
}

private fun formatSpeed(bytesPerSecond: Long): String {
    return "${formatBytes(bytesPerSecond)}/s"
}

private fun formatBytes(bytes: Long): String {
    val kb = 1024.0
    val mb = kb * 1024.0
    val gb = mb * 1024.0
    return when {
        bytes >= gb -> String.format("%.2f GB", bytes / gb)
        bytes >= mb -> String.format("%.2f MB", bytes / mb)
        bytes >= kb -> String.format("%.1f KB", bytes / kb)
        else -> "$bytes B"
    }
}

@Composable
private fun EmptyNodeState(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "暂无节点，请先导入订阅或添加节点",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
