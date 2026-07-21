package com.myproxy.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    onBack: () -> Unit,
    importViewModel: ImportViewModel = viewModel(),
) {
    val context = LocalContext.current
    val state by importViewModel.state.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var shareLink by rememberSaveable { mutableStateOf("") }
    var subscriptionUrl by rememberSaveable { mutableStateOf("") }
    var scannerVisible by rememberSaveable { mutableStateOf(false) }
    var galleryMessage by rememberSaveable { mutableStateOf<String?>(null) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            scannerVisible = true
        } else {
            Toast.makeText(context, "未授予相机权限，无法扫码", Toast.LENGTH_SHORT).show()
        }
    }

    fun openScanner() {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            scannerVisible = true
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(text = "导入节点") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                divider = {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                },
            ) {
                importTabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = {
                            selectedTab = index
                            if (index != 1) scannerVisible = false
                        },
                        text = { Text(text = title) },
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                contentAlignment = Alignment.TopCenter,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 640.dp)
                        .padding(16.dp),
                ) {
                    when (selectedTab) {
                        0 -> ShareLinkImportSection(
                            value = shareLink,
                            onValueChange = { shareLink = it },
                            loading = state.shareLoading,
                            message = state.shareMessage,
                            error = state.shareError,
                            onImport = { importViewModel.importShareLink(shareLink) },
                        )

                        1 -> ScanImportSection(
                            scannerVisible = scannerVisible,
                            loading = state.scanLoading,
                            message = state.scanMessage ?: galleryMessage,
                            error = state.scanError,
                            onOpenScanner = { openScanner() },
                            onCloseScanner = { scannerVisible = false },
                            onGalleryPlaceholder = {
                                galleryMessage = "相册二维码识别入口已预留"
                            },
                            onCodeFound = { rawValue ->
                                scannerVisible = false
                                galleryMessage = null
                                importViewModel.importScannedText(rawValue)
                            },
                        )

                        else -> SubscriptionImportSection(
                            value = subscriptionUrl,
                            onValueChange = { subscriptionUrl = it },
                            loading = state.subscriptionLoading,
                            message = state.subscriptionMessage,
                            error = state.subscriptionError,
                            onImport = { importViewModel.importSubscription(subscriptionUrl) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ShareLinkImportSection(
    value: String,
    onValueChange: (String) -> Unit,
    loading: Boolean,
    message: String?,
    error: String?,
    onImport: () -> Unit,
) {
    ImportSectionSurface(
        title = "粘贴分享链接",
        subtitle = "支持 VMess、VLESS、Trojan 和 Shadowsocks",
    ) {
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(text = "粘贴 vmess:// 或其他节点链接") },
            minLines = 4,
            shape = MaterialTheme.shapes.medium,
        )
        Spacer(modifier = Modifier.height(14.dp))
        Button(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 50.dp),
            enabled = !loading,
            onClick = onImport,
            shape = MaterialTheme.shapes.medium,
        ) {
            Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = null)
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = if (loading) "正在解析" else "解析并导入")
        }
        ImportResultMessage(loading = loading, message = message, error = error)
    }
}

@Composable
private fun ScanImportSection(
    scannerVisible: Boolean,
    loading: Boolean,
    message: String?,
    error: String?,
    onOpenScanner: () -> Unit,
    onCloseScanner: () -> Unit,
    onGalleryPlaceholder: () -> Unit,
    onCodeFound: (String) -> Unit,
) {
    ImportSectionSurface(
        title = "扫描二维码",
        subtitle = "扫描后会使用同一套安全解析流程",
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 50.dp),
                enabled = !loading,
                onClick = onOpenScanner,
                shape = MaterialTheme.shapes.medium,
            ) {
                Text(text = if (loading) "导入中" else "打开相机")
            }
            OutlinedButton(
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 50.dp),
                onClick = onGalleryPlaceholder,
                shape = MaterialTheme.shapes.medium,
            ) {
                Text(text = "相册识别")
            }
        }

        if (scannerVisible) {
            Spacer(modifier = Modifier.height(14.dp))
            CameraQrScanner(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 300.dp, max = 420.dp)
                    .clip(RoundedCornerShape(8.dp)),
                onCodeFound = onCodeFound,
            )
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onCloseScanner,
                shape = MaterialTheme.shapes.medium,
            ) {
                Icon(imageVector = Icons.Filled.Close, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "关闭相机")
            }
        }

        ImportResultMessage(loading = loading, message = message, error = error)
    }
}

@Composable
private fun SubscriptionImportSection(
    value: String,
    onValueChange: (String) -> Unit,
    loading: Boolean,
    message: String?,
    error: String?,
    onImport: () -> Unit,
) {
    ImportSectionSurface(
        title = "添加订阅",
        subtitle = "订阅会直接拉取并批量写入节点列表",
    ) {
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(text = "https://") },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
        )
        Spacer(modifier = Modifier.height(14.dp))
        Button(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 50.dp),
            enabled = !loading,
            onClick = onImport,
            shape = MaterialTheme.shapes.medium,
        ) {
            Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = null)
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = if (loading) "正在导入" else "拉取并导入")
        }
        ImportResultMessage(loading = loading, message = message, error = error)
    }
}

@Composable
private fun ImportSectionSurface(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
private fun ImportResultMessage(
    loading: Boolean,
    message: String?,
    error: String?,
) {
    if (loading) {
        Spacer(modifier = Modifier.height(14.dp))
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }

    val feedback = error ?: message
    if (feedback != null) {
        Spacer(modifier = Modifier.height(14.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = if (error != null) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.primaryContainer
            },
            shape = MaterialTheme.shapes.small,
        ) {
            Text(
                text = feedback,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = if (error != null) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onPrimaryContainer
                },
            )
        }
    }
}

private val importTabs = listOf("链接", "扫码", "订阅")
