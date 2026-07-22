package com.myproxy.app.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.myproxy.app.model.ProxyNode
import com.myproxy.app.service.VpnUiState

/** 节点配置集中页：首页只展示连接信息，不再承载长节点列表。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigScreen(
    onOpenImport: () -> Unit,
    mainViewModel: MainViewModel = viewModel(),
) {
    val context = LocalContext.current
    val nodes by mainViewModel.nodes.collectAsStateWithLifecycle()
    val selectedNodeId by mainViewModel.selectedNodeId.collectAsStateWithLifecycle()
    val latencyResults by mainViewModel.latencyResults.collectAsStateWithLifecycle()
    val vpnState by mainViewModel.connectionState.collectAsStateWithLifecycle()
    val configurationLocked = vpnState.uiState == VpnUiState.CONNECTING ||
        vpnState.uiState == VpnUiState.CONNECTED
    var editingNode by remember { mutableStateOf<ProxyNode?>(null) }
    var pendingDeleteNode by remember { mutableStateOf<ProxyNode?>(null) }

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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "配置",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                },
                actions = {
                    IconButton(onClick = mainViewModel::testAllLatencies) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "全部测延",
                        )
                    }
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
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 12.dp,
                end = 12.dp,
                top = 12.dp,
                bottom = 20.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                ConfigurationSummary(
                    nodeCount = nodes.size,
                    selectedNode = selectedNode,
                    configurationLocked = configurationLocked,
                    onTestAll = mainViewModel::testAllLatencies,
                )
            }

            if (nodes.isEmpty()) {
                item {
                    EmptyConfigurationState(onImport = onOpenImport)
                }
            } else {
                items(
                    items = nodes,
                    key = { node -> node.id },
                ) { node ->
                    NodeConfigurationCard(
                        node = node,
                        selected = node.id == selectedNodeId,
                        latencyState = latencyResults[node.id],
                        configurationLocked = configurationLocked,
                        onClick = {
                            if (configurationLocked) {
                                Toast.makeText(context, "请先断开连接再切换节点", Toast.LENGTH_SHORT).show()
                            } else {
                                mainViewModel.selectNode(node)
                            }
                        },
                        onTestLatency = { mainViewModel.testNodeLatency(node) },
                        onEdit = { editingNode = node },
                        onDelete = { pendingDeleteNode = node },
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 720.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ConfigurationSummary(
    nodeCount: Int,
    selectedNode: ProxyNode?,
    configurationLocked: Boolean,
    onTestAll: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "全部配置", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "$nodeCount 个可用节点",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onTestAll, enabled = nodeCount > 0) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "全部测延")
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selectedNode != null) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "当前：${selectedNode.remark.ifBlank { "未命名节点" }}",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    Text(
                        text = "尚未选择节点",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (configurationLocked) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "连接期间已锁定当前配置，断开后可切换或编辑",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
    }
}

@Composable
private fun NodeConfigurationCard(
    node: ProxyNode,
    selected: Boolean,
    latencyState: NodeLatencyState?,
    configurationLocked: Boolean,
    onClick: () -> Unit,
    onTestLatency: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = if (selected) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
        } else {
            null
        },
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(start = 12.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = MaterialTheme.shapes.small,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = protocolCode(node),
                        style = MaterialTheme.typography.labelLarge,
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
                // 配置页只显示识别信息，不显示 UUID、密码或订阅 URL。
                Text(
                    text = node.remark.ifBlank { "未命名节点" },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(3.dp))
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
                        enabled = !configurationLocked,
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
                        enabled = !configurationLocked,
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        },
                    )
                }
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
private fun LatencyLabel(latencyState: NodeLatencyState?) {
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
        maxLines = 1,
    )
}

@Composable
private fun EmptyConfigurationState(onImport: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Surface(
                modifier = Modifier.size(60.dp),
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
            Spacer(modifier = Modifier.height(14.dp))
            Text(text = "还没有节点配置", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "可导入分享链接、二维码或订阅",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onImport, shape = MaterialTheme.shapes.medium) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "导入配置")
            }
        }
    }
}

private fun protocolCode(node: ProxyNode): String = when (node.protocol.name) {
    "SHADOWSOCKS" -> "SS"
    "TROJAN" -> "TR"
    "VMESS" -> "VM"
    "VLESS" -> "VL"
    else -> node.protocol.name.take(2)
}
