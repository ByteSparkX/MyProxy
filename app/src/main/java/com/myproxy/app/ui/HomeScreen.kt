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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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

    val selectedNode = nodes.firstOrNull { node -> node.id == selectedNodeId }

    // 主页只负责节点选择、授权和发送启停请求，真实链路生命周期由 MyVpnService 管理。
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_brand_mark),
                            contentDescription = null,
                            modifier = Modifier.size(30.dp),
                            contentScale = ContentScale.Fit,
                        )
                        Text(
                            text = stringResource(id = R.string.app_name),
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenImport) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "导入节点",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        bottomBar = {
            ConnectionActionBar(
                state = vpnState.uiState,
                enabled = !isConnecting,
                onClick = {
                    if (isConnected) {
                        stopVpnFlow()
                    } else {
                        requestPermissionsThenStart()
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            item {
                ConnectionOverview(
                    state = vpnState.uiState,
                    errorMessage = vpnState.errorMessage,
                    selectedNode = selectedNode,
                )
            }
            item {
                Spacer(modifier = Modifier.height(12.dp))
                TrafficStatsPanel(
                    uploadSpeed = trafficStats.uploadSpeed,
                    downloadSpeed = trafficStats.downloadSpeed,
                    uploadTotal = trafficStats.uploadTotal,
                    downloadTotal = trafficStats.downloadTotal,
                )
            }
            item {
                SectionHeader(
                    title = "节点",
                    subtitle = "${nodes.size} 个可用节点",
                    onTestAll = { mainViewModel.testAllLatencies() },
                    onImport = onOpenImport,
                )
            }
            if (nodes.isEmpty()) {
                item {
                    EmptyNodeState(onImport = onOpenImport)
                }
            } else {
                itemsIndexed(
                    items = nodes,
                    key = { _, node -> node.id },
                ) { index, node ->
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        NodeSelectionRow(
                            node = node,
                            selected = node.id == selectedNodeId,
                            latencyState = latencyResults[node.id],
                            showDivider = index < nodes.lastIndex,
                            onClick = { selectNode(node) },
                            onTestLatency = { mainViewModel.testNodeLatency(node) },
                            onEdit = { editNode(node) },
                            onDelete = { deleteNode(node) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 640.dp),
                        )
                    }
                }
            }
            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun ConnectionOverview(
    state: VpnUiState,
    errorMessage: String?,
    selectedNode: ProxyNode?,
) {
    val statusColor = when (state) {
        VpnUiState.CONNECTED -> MaterialTheme.colorScheme.primary
        VpnUiState.CONNECTING -> MaterialTheme.colorScheme.tertiary
        VpnUiState.ERROR -> MaterialTheme.colorScheme.error
        VpnUiState.DISCONNECTED -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val statusText = when (state) {
        VpnUiState.CONNECTED -> "已连接"
        VpnUiState.CONNECTING -> "正在建立安全连接"
        VpnUiState.ERROR -> "连接异常"
        VpnUiState.DISCONNECTED -> "未连接"
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp)
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(statusColor, CircleShape),
                    )
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.titleMedium,
                        color = statusColor,
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Surface(
                    modifier = Modifier.size(96.dp),
                    shape = CircleShape,
                    color = statusColor.copy(alpha = 0.12f),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (state == VpnUiState.CONNECTING) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(42.dp),
                                color = statusColor,
                                strokeWidth = 3.dp,
                            )
                        } else {
                            Icon(
                                imageVector = if (state == VpnUiState.CONNECTED) {
                                    Icons.Filled.CheckCircle
                                } else {
                                    Icons.Filled.PlayArrow
                                },
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = statusColor,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = selectedNode?.remark?.ifBlank { "未命名节点" } ?: "尚未选择节点",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = selectedNode?.let { node ->
                        "${node.protocol.name}  ·  ${node.address}:${node.port}"
                    } ?: "从下方列表选择一个节点",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                if (state == VpnUiState.ERROR && !errorMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Text(
                            text = errorMessage,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionActionBar(
    state: VpnUiState,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val disconnecting = state == VpnUiState.CONNECTED
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp)
                    .defaultMinSize(minHeight = 52.dp),
                enabled = enabled,
                onClick = onClick,
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (disconnecting) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                ),
            ) {
                if (state == VpnUiState.CONNECTING) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        imageVector = if (disconnecting) Icons.Filled.Close else Icons.Filled.PlayArrow,
                        contentDescription = null,
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when (state) {
                        VpnUiState.DISCONNECTED -> "连接"
                        VpnUiState.CONNECTING -> "连接中"
                        VpnUiState.CONNECTED -> "断开连接"
                        VpnUiState.ERROR -> "重试连接"
                    },
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String,
    onTestAll: () -> Unit,
    onImport: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 640.dp)
                .padding(start = 16.dp, end = 8.dp, top = 18.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onTestAll) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "全部测延",
                )
            }
            IconButton(onClick = onImport) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "导入节点",
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
    showDivider: Boolean,
    onClick: () -> Unit,
    onTestLatency: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Surface(
        modifier = modifier,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        } else {
            MaterialTheme.colorScheme.surface
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier.size(42.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = protocolCode(node),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (selected) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                ) {
                    // 列表展示基础识别信息，不展示 UUID、密码或订阅 URL。
                    Text(
                        text = node.remark.ifBlank { "未命名节点" },
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${node.protocol.name}  ·  ${node.address}:${node.port}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                LatencyLabel(latencyState = latencyState)
                if (selected) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = "已选中",
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(20.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "节点操作",
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(text = "测试延迟") },
                            leadingIcon = {
                                Icon(imageVector = Icons.Filled.Refresh, contentDescription = null)
                            },
                            onClick = {
                                menuExpanded = false
                                onTestLatency()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(text = "编辑节点") },
                            leadingIcon = {
                                Icon(imageVector = Icons.Filled.Edit, contentDescription = null)
                            },
                            onClick = {
                                menuExpanded = false
                                onEdit()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(text = "删除节点") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            },
                        )
                    }
                }
            }
            if (showDivider) {
                HorizontalDivider(
                    modifier = Modifier.padding(start = 70.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
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
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Text(text = "实时流量", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    MetricItem(label = "上行速率", value = formatSpeed(uploadSpeed), modifier = Modifier.weight(1f))
                    MetricDivider()
                    MetricItem(label = "下行速率", value = formatSpeed(downloadSpeed), modifier = Modifier.weight(1f))
                }
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                Row(modifier = Modifier.fillMaxWidth()) {
                    MetricItem(label = "本次上传", value = formatBytes(uploadTotal), modifier = Modifier.weight(1f))
                    MetricDivider()
                    MetricItem(label = "本次下载", value = formatBytes(downloadTotal), modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun MetricItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MetricDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(42.dp)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
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
    onImport: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Surface(
                modifier = Modifier.size(56.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = "还没有节点", style = MaterialTheme.typography.titleSmall)
            Text(
                text = "导入分享链接、二维码或订阅",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(14.dp))
            Button(onClick = onImport, shape = MaterialTheme.shapes.medium) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "导入节点")
            }
        }
    }
}

private fun protocolCode(node: ProxyNode): String {
    return when (node.protocol.name) {
        "SHADOWSOCKS" -> "SS"
        "TROJAN" -> "TR"
        "VMESS" -> "VM"
        "VLESS" -> "VL"
        else -> node.protocol.name.take(2)
    }
}
