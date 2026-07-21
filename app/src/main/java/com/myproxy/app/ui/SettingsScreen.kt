package com.myproxy.app.ui

import android.widget.ImageView
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(text = "设置") },
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
        ) {
            item {
                SettingsGroupLabel(title = "通用")
            }
            item {
                SettingsGroupSurface {
                    SettingSwitchRow(
                        title = "开机后恢复连接",
                        description = "受系统后台启动限制时，需要手动打开应用",
                        checked = bootStartEnabled,
                        onCheckedChange = settingsViewModel::setBootStartEnabled,
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    DnsSettingBlock(
                        value = dnsText,
                        onValueChange = { dnsText = it },
                        error = dnsFeedback.error,
                        message = dnsFeedback.message,
                        onSave = { settingsViewModel.saveCustomDns(dnsText) },
                    )
                }
            }

            item {
                SettingsGroupLabel(
                    title = "分应用代理",
                    description = "黑名单排除选中应用，白名单仅代理选中应用；下次连接生效",
                )
            }
            item {
                SettingsGroupSurface {
                    Column(modifier = Modifier.padding(16.dp)) {
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
                            placeholder = { Text(text = "搜索应用名或包名") },
                            leadingIcon = {
                                Icon(imageVector = Icons.Filled.Search, contentDescription = null)
                            },
                            singleLine = true,
                            shape = MaterialTheme.shapes.medium,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "已选择 ${selectedPackages.size} 个应用",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (filteredApps.isEmpty()) {
                item {
                    SettingsGroupSurface {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = if (searchText.isBlank()) "正在读取已安装应用" else "未找到匹配应用",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else {
                itemsIndexed(
                    items = filteredApps,
                    key = { _, app -> app.packageName },
                ) { index, app ->
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 640.dp),
                            color = MaterialTheme.colorScheme.surface,
                        ) {
                            Column {
                                InstalledAppRow(
                                    app = app,
                                    checked = app.packageName in selectedPackages,
                                    onCheckedChange = { settingsViewModel.toggleApp(app.packageName) },
                                )
                                if (index < filteredApps.lastIndex) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(start = 68.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun SettingsGroupLabel(
    title: String,
    description: String? = null,
) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 640.dp)
                .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!description.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SettingsGroupSurface(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 640.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            content()
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 76.dp)
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun DnsSettingBlock(
    value: String,
    onValueChange: (String) -> Unit,
    error: String?,
    message: String?,
    onSave: () -> Unit,
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(text = "自定义 DNS", style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "支持多个 IPv4 或 IPv6 地址，使用逗号、空格或换行分隔",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(10.dp))
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(text = "例如 1.1.1.1, 8.8.8.8") },
            minLines = 2,
            shape = MaterialTheme.shapes.medium,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Button(
            modifier = Modifier.defaultMinSize(minHeight = 46.dp),
            onClick = onSave,
            shape = MaterialTheme.shapes.medium,
        ) {
            Text(text = "保存 DNS")
        }
        error?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        message?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
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
            .clickable(onClick = onCheckedChange)
            .padding(horizontal = 14.dp, vertical = 10.dp),
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
            Text(
                text = app.label,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
            )
            Text(
                text = app.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Checkbox(
            checked = checked,
            onCheckedChange = { onCheckedChange() },
        )
    }
}
