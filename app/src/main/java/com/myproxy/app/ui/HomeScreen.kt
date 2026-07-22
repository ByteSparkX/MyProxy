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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.myproxy.app.R
import com.myproxy.app.core.AppLog
import com.myproxy.app.model.ProxyNode
import com.myproxy.app.model.RoutingMode
import com.myproxy.app.service.MyVpnService
import com.myproxy.app.service.VpnUiState
import kotlinx.coroutines.launch

private const val TAG = "HomeScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    mainViewModel: MainViewModel = viewModel(),
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val nodes by mainViewModel.nodes.collectAsStateWithLifecycle()
    val selectedNodeId by mainViewModel.selectedNodeId.collectAsStateWithLifecycle()
    val routingMode by mainViewModel.routingMode.collectAsStateWithLifecycle()
    val vpnState by mainViewModel.connectionState.collectAsStateWithLifecycle()
    val trafficStats by mainViewModel.trafficStats.collectAsStateWithLifecycle()
    val isConnecting = vpnState.uiState == VpnUiState.CONNECTING
    val isConnected = vpnState.uiState == VpnUiState.CONNECTED

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

    suspend fun buildConnectionConfig(): String? {
        return when (val result = mainViewModel.buildConnectionConfig()) {
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
            val configJson = buildConnectionConfig() ?: return@launch
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
            // 用户同意 VPN 授权后，再读取路由模式和节点并生成动态配置。
            startSelectedNodeFlow()
        } else {
            mainViewModel.setDisconnected()
            Toast.makeText(context, "未授予 VPN 权限，已取消连接", Toast.LENGTH_SHORT).show()
        }
    }

    suspend fun requestVpnPermissionThenStart() {
        if (!mainViewModel.canStartConnection()) {
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
            if (!mainViewModel.canStartConnection()) {
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

    val selectedNode = nodes.firstOrNull { node -> node.id == selectedNodeId }

    // 首页只展示连接概览、路由模式和流量，节点管理统一放到配置页。
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
                    routingMode = routingMode,
                )
            }
            item {
                RoutingModeSelector(
                    selectedMode = routingMode,
                    enabled = !isConnecting && !isConnected,
                    onModeSelected = mainViewModel::setRoutingMode,
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
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun ConnectionOverview(
    state: VpnUiState,
    errorMessage: String?,
    selectedNode: ProxyNode?,
    routingMode: RoutingMode,
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
                    text = if (routingMode == RoutingMode.DIRECT) {
                        "本地网络直连"
                    } else {
                        selectedNode?.remark?.ifBlank { "未命名节点" } ?: "尚未选择节点"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (routingMode == RoutingMode.DIRECT) {
                        "不使用代理节点"
                    } else {
                        selectedNode?.let { node ->
                            "${node.protocol.name}  ·  ${node.address}:${node.port}"
                        } ?: "请前往“配置”页选择节点"
                    },
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoutingModeSelector(
    selectedMode: RoutingMode,
    enabled: Boolean,
    onModeSelected: (RoutingMode) -> Unit,
) {
    val modes = RoutingMode.entries
    val description = when (selectedMode) {
        RoutingMode.RULE -> "仅受限网站走代理，国内及其他网站直连"
        RoutingMode.GLOBAL -> "全部网络流量通过所选代理节点"
        RoutingMode.DIRECT -> "全部流量使用本地网络，不经过代理节点"
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 640.dp)
                .padding(horizontal = 20.dp, vertical = 14.dp),
        ) {
            Text(
                text = "代理模式",
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(modifier = Modifier.height(10.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                modes.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = selectedMode == mode,
                        onClick = { onModeSelected(mode) },
                        enabled = enabled,
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                        label = {
                            Text(
                                text = when (mode) {
                                    RoutingMode.RULE -> "规则"
                                    RoutingMode.GLOBAL -> "全局"
                                    RoutingMode.DIRECT -> "直连"
                                },
                            )
                        },
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (enabled) description else "$description，断开后可切换",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
