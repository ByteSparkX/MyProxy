@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.myproxy.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.myproxy.desktop.model.ConnectionState
import com.myproxy.desktop.model.ConnectionStatus
import com.myproxy.desktop.model.DesktopState
import com.myproxy.desktop.model.ProxyNode
import com.myproxy.desktop.model.RoutingMode
import java.awt.Dimension
import javax.swing.SwingUtilities

private val Teal = Color(0xFF00A99D)
private val Coral = Color(0xFFFF5A4E)
private val Ink = Color(0xFF17212B)
private val Canvas = Color(0xFFF4F6F8)
private val Muted = Color(0xFF66727F)

fun main() {
    if (System.getProperty("os.name").contains("Windows", ignoreCase = true)) {
        // 兼容旧显卡驱动与远程桌面环境，避免窗口启动后出现空白。
        System.setProperty("skiko.renderApi", "SOFTWARE_FAST")
    }
    application {
        val controller = remember { DesktopController() }
        val windowState: WindowState = rememberWindowState(width = 1120.dp, height = 720.dp)
        Window(
            onCloseRequest = {
                controller.close()
                exitApplication()
            },
            title = "MyProxy",
            state = windowState,
        ) {
            LaunchedEffect(Unit) { window.minimumSize = Dimension(900, 600) }
            DisposableEffect(Unit) { onDispose(controller::close) }
            MyProxyDesktopApp(controller)
        }
    }
}

private enum class DesktopPage(val label: String, val icon: ImageVector) {
    HOME("首页", Icons.Default.Home),
    IMPORT("导入", Icons.Default.AddLink),
    ABOUT("关于", Icons.Default.Info),
}

@Composable
private fun MyProxyDesktopApp(controller: DesktopController) {
    val colors = androidx.compose.material3.lightColorScheme(
        primary = Teal,
        secondary = Coral,
        background = Canvas,
        surface = Color.White,
        onPrimary = Color.White,
        onBackground = Ink,
        onSurface = Ink,
    )
    var page by remember { mutableStateOf(DesktopPage.HOME) }
    MaterialTheme(colorScheme = colors) {
        Scaffold(containerColor = Canvas) { padding ->
            Row(Modifier.fillMaxSize().padding(padding)) {
                DesktopNavigation(page = page, onPageSelected = { page = it })
                Box(Modifier.fillMaxHeight().width(1.dp).background(Color(0xFFE4E8EC)))
                when (page) {
                    DesktopPage.HOME -> HomePage(controller)
                    DesktopPage.IMPORT -> ImportPage(controller, onImported = { page = DesktopPage.HOME })
                    DesktopPage.ABOUT -> AboutPage()
                }
            }
        }
    }
}

@Composable
private fun DesktopNavigation(page: DesktopPage, onPageSelected: (DesktopPage) -> Unit) {
    NavigationRail(
        modifier = Modifier.width(92.dp),
        containerColor = Color.White,
        header = {
            Box(
                modifier = Modifier.padding(vertical = 22.dp).size(44.dp)
                    .background(Ink, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text("M", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 21.sp)
            }
        },
    ) {
        DesktopPage.entries.forEach { item ->
            NavigationRailItem(
                selected = page == item,
                onClick = { onPageSelected(item) },
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label) },
            )
        }
    }
}

@Composable
private fun HomePage(controller: DesktopController) {
    val state by controller.state.collectAsState()
    val connection by controller.connection.collectAsState()
    Column(Modifier.fillMaxSize().padding(horizontal = 30.dp, vertical = 24.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("我的代理", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("选择节点与路由模式，然后启用系统代理", color = Muted)
            }
            ModeSelector(state.routingMode, controller::setRoutingMode)
        }
        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            NodeList(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                state = state,
                onSelect = controller::selectNode,
                onDelete = controller::deleteNode,
            )
            ConnectionPanel(
                modifier = Modifier.width(310.dp).fillMaxHeight(),
                state = state,
                connection = connection,
                onToggle = controller::toggleConnection,
            )
        }
    }
}

@Composable
private fun ModeSelector(selected: RoutingMode, onSelected: (RoutingMode) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RoutingMode.entries.forEach { mode ->
            val label = when (mode) {
                RoutingMode.RULE -> "规则"
                RoutingMode.GLOBAL -> "全局"
                RoutingMode.DIRECT -> "直连"
            }
            FilterChip(
                selected = selected == mode,
                onClick = { onSelected(mode) },
                label = { Text(label) },
            )
        }
    }
}

@Composable
private fun NodeList(
    modifier: Modifier,
    state: DesktopState,
    onSelect: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    Surface(modifier, shape = RoundedCornerShape(8.dp), color = Color.White) {
        Column(Modifier.fillMaxSize().padding(20.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("节点", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text("${state.nodes.size} 个", color = Muted)
            }
            Spacer(Modifier.height(14.dp))
            if (state.nodes.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Public, null, tint = Color(0xFF9AA4AD), modifier = Modifier.size(42.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("还没有节点", fontWeight = FontWeight.Medium)
                        Text("请从左侧“导入”添加分享链接或订阅", color = Muted)
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(state.nodes, key = ProxyNode::id) { node ->
                        NodeRow(
                            node = node,
                            selected = node.id == state.selectedNodeId,
                            onSelect = { onSelect(node.id) },
                            onDelete = { onDelete(node.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NodeRow(node: ProxyNode, selected: Boolean, onSelect: () -> Unit, onDelete: () -> Unit) {
    val background = if (selected) Color(0xFFE7F7F5) else Color(0xFFF7F8FA)
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onSelect),
        colors = CardDefaults.cardColors(containerColor = background),
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(10.dp).background(if (selected) Teal else Color(0xFFBBC3CA), CircleShape),
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    node.remark,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "${node.protocol.name} · ${node.address}:${node.port}",
                    color = Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            TooltipBox(
                positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                tooltip = { Text("删除节点") },
                state = rememberTooltipState(),
            ) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "删除节点", tint = Muted)
                }
            }
        }
    }
}

@Composable
private fun ConnectionPanel(
    modifier: Modifier,
    state: DesktopState,
    connection: ConnectionState,
    onToggle: () -> Unit,
) {
    Surface(modifier, shape = RoundedCornerShape(8.dp), color = Ink) {
        Column(Modifier.fillMaxSize().padding(24.dp)) {
            Text("连接状态", color = Color(0xFFBCC6CE))
            Spacer(Modifier.height(24.dp))
            val statusColor = when (connection.status) {
                ConnectionStatus.CONNECTED -> Teal
                ConnectionStatus.ERROR -> Coral
                ConnectionStatus.CONNECTING -> Color(0xFFFFC857)
                ConnectionStatus.DISCONNECTED -> Color(0xFF7F8B95)
            }
            Box(Modifier.size(74.dp).background(statusColor.copy(alpha = 0.18f), CircleShape), Alignment.Center) {
                if (connection.status == ConnectionStatus.CONNECTING) {
                    CircularProgressIndicator(modifier = Modifier.size(34.dp), color = statusColor, strokeWidth = 3.dp)
                } else {
                    Icon(
                        if (connection.status == ConnectionStatus.CONNECTED) Icons.Default.CheckCircle
                        else Icons.Default.PowerSettingsNew,
                        null,
                        tint = statusColor,
                        modifier = Modifier.size(36.dp),
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            Text(
                when (connection.status) {
                    ConnectionStatus.DISCONNECTED -> "未连接"
                    ConnectionStatus.CONNECTING -> "连接中"
                    ConnectionStatus.CONNECTED -> "已连接"
                    ConnectionStatus.ERROR -> "需要处理"
                },
                color = Color.White,
                fontSize = 25.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(connection.message, color = Color(0xFFBCC6CE), maxLines = 3)
            Spacer(Modifier.height(26.dp))
            HorizontalDivider(color = Color(0xFF35414B))
            Spacer(Modifier.height(20.dp))
            Text("当前模式", color = Color(0xFF8F9BA5), style = MaterialTheme.typography.labelMedium)
            Text(
                when (state.routingMode) {
                    RoutingMode.RULE -> "规则分流"
                    RoutingMode.GLOBAL -> "全局代理"
                    RoutingMode.DIRECT -> "本地直连"
                },
                color = Color.White,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(14.dp))
            Text("选中节点", color = Color(0xFF8F9BA5), style = MaterialTheme.typography.labelMedium)
            Text(
                state.nodes.firstOrNull { it.id == state.selectedNodeId }?.remark
                    ?: if (state.routingMode == RoutingMode.DIRECT) "直连无需节点" else "未选择",
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.weight(1f))
            Button(
                onClick = onToggle,
                enabled = connection.status != ConnectionStatus.CONNECTING,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (connection.status == ConnectionStatus.CONNECTED) Coral else Teal,
                ),
                shape = RoundedCornerShape(8.dp),
            ) {
                Icon(Icons.Default.PowerSettingsNew, null)
                Spacer(Modifier.width(8.dp))
                Text(if (connection.status == ConnectionStatus.CONNECTED) "断开" else "连接")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportPage(controller: DesktopController, onImported: () -> Unit) {
    val importing by controller.importing.collectAsState()
    var input by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("支持 VLESS、VMess、Trojan、Shadowsocks 分享链接和订阅 URL") }
    Column(Modifier.fillMaxSize().padding(34.dp)) {
        Text("导入节点", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("节点只保存在当前电脑，不会上传或同步", color = Muted)
        Spacer(Modifier.height(28.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = Color.White,
        ) {
            Column(Modifier.padding(24.dp)) {
                Text("分享链接或订阅", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.fillMaxWidth().height(170.dp),
                    placeholder = { Text("粘贴分享链接或 https:// 订阅地址") },
                    enabled = !importing,
                )
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = {
                            controller.import(input) { result ->
                                SwingUtilities.invokeLater {
                                    message = result.message
                                    if (result.successCount > 0) {
                                        input = ""
                                        onImported()
                                    }
                                }
                            }
                        },
                        enabled = input.isNotBlank() && !importing,
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        if (importing) {
                            CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.AddLink, null)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(if (importing) "正在导入" else "导入")
                    }
                    Spacer(Modifier.width(16.dp))
                    Text(message, color = Muted)
                }
            }
        }
    }
}

@Composable
private fun AboutPage() {
    Column(Modifier.fillMaxSize().padding(34.dp)) {
        Text("关于 MyProxy", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))
        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), color = Color.White) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("MyProxy Desktop 1.1.0", style = MaterialTheme.typography.titleLarge)
                Text("基于 Xray-core 的开源桌面客户端", color = Muted)
                HorizontalDivider()
                Text("桌面首版使用系统 HTTP、HTTPS 与 SOCKS 代理。忽略系统代理的应用不会被接管。")
                Text("Windows 支持 x64；macOS 分别提供 Intel 与 Apple Silicon 安装包。")
                Text("项目不集成广告、统计、第三方推送、账号系统或云同步。")
            }
        }
    }
}
