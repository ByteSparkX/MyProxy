package com.myproxy.app.ui

import android.widget.ImageView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.myproxy.app.data.AppProxyMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsViewModel: SettingsViewModel = viewModel(),
) {
    val installedApps by settingsViewModel.installedApps.collectAsStateWithLifecycle()
    val appProxyMode by settingsViewModel.appProxyMode.collectAsStateWithLifecycle()
    val selectedPackages by settingsViewModel.selectedAppPackages.collectAsStateWithLifecycle()
    val bootStartEnabled by settingsViewModel.bootStartEnabled.collectAsStateWithLifecycle()
    val customDns by settingsViewModel.customDns.collectAsStateWithLifecycle()
    val dnsFeedback by settingsViewModel.dnsFeedback.collectAsStateWithLifecycle()
    var searchText by rememberSaveable { mutableStateOf("") }
    var dnsText by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(customDns) {
        dnsText = customDns.orEmpty()
    }

    val filteredApps = installedApps.filter { app ->
        searchText.isBlank() ||
            app.label.contains(searchText, ignoreCase = true) ||
            app.packageName.contains(searchText, ignoreCase = true)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(text = "设置")
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                SettingsSectionTitle(
                    title = "分应用代理",
                    description = "黑名单模式会排除选中应用；白名单模式只代理选中应用。修改后下次连接生效。",
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilterChip(
                        selected = appProxyMode == AppProxyMode.BLACKLIST,
                        onClick = { settingsViewModel.setAppProxyMode(AppProxyMode.BLACKLIST) },
                        label = { Text(text = "黑名单") },
                    )
                    FilterChip(
                        selected = appProxyMode == AppProxyMode.WHITELIST,
                        onClick = { settingsViewModel.setAppProxyMode(AppProxyMode.WHITELIST) },
                        label = { Text(text = "白名单") },
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = searchText,
                    onValueChange = { searchText = it },
                    label = { Text(text = "搜索应用名或包名") },
                    singleLine = true,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "已选择 ${selectedPackages.size} 个应用",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            items(
                items = filteredApps,
                key = { app -> app.packageName },
            ) { app ->
                InstalledAppRow(
                    app = app,
                    checked = app.packageName in selectedPackages,
                    onCheckedChange = { settingsViewModel.toggleApp(app.packageName) },
                )
            }

            item {
                SettingsSectionTitle(
                    title = "开机自启",
                    description = "开启后会在系统启动完成时尝试恢复上次选中节点。Android 高版本可能限制后台启动前台服务，若系统拦截，需要手动打开应用连接。",
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(text = if (bootStartEnabled) "已开启" else "已关闭")
                    Switch(
                        checked = bootStartEnabled,
                        onCheckedChange = settingsViewModel::setBootStartEnabled,
                    )
                }
            }

            item {
                SettingsSectionTitle(
                    title = "自定义 DNS",
                    description = "可输入一个或多个 DNS，使用逗号、空格或换行分隔。会同时写入 Xray DNS 与 Android VPN DNS，下次连接生效。",
                )
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = dnsText,
                    onValueChange = { dnsText = it },
                    label = { Text(text = "例如 1.1.1.1, 8.8.8.8") },
                    minLines = 2,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = { settingsViewModel.saveCustomDns(dnsText) }) {
                    Text(text = "保存 DNS")
                }
                dnsFeedback.error?.let { error ->
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                dnsFeedback.message?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionTitle(
    title: String,
    description: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun InstalledAppRow(
    app: InstalledAppInfo,
    checked: Boolean,
    onCheckedChange: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AndroidView(
            modifier = Modifier.size(40.dp),
            factory = { context ->
                ImageView(context).apply {
                    scaleType = ImageView.ScaleType.CENTER_INSIDE
                }
            },
            update = { imageView ->
                imageView.setImageDrawable(app.icon)
            },
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        ) {
            Text(text = app.label, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = app.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Checkbox(
            checked = checked,
            onCheckedChange = { onCheckedChange() },
        )
    }
}
