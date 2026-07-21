package com.myproxy.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
        topBar = {
            TopAppBar(
                title = {
                    Text(text = "导入节点")
                },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text(text = "返回")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            ShareLinkImportSection(
                value = shareLink,
                onValueChange = { shareLink = it },
                loading = state.shareLoading,
                message = state.shareMessage,
                error = state.shareError,
                onImport = { importViewModel.importShareLink(shareLink) },
            )

            HorizontalDivider()

            ScanImportSection(
                scannerVisible = scannerVisible,
                loading = state.scanLoading,
                message = state.scanMessage ?: galleryMessage,
                error = state.scanError,
                onOpenScanner = { openScanner() },
                onCloseScanner = { scannerVisible = false },
                onGalleryPlaceholder = {
                    galleryMessage = "相册二维码识别入口已预留，后续接入图片解码后复用同一解析流程"
                },
                onCodeFound = { rawValue ->
                    scannerVisible = false
                    galleryMessage = null
                    importViewModel.importScannedText(rawValue)
                },
            )

            HorizontalDivider()

            SubscriptionImportSection(
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

@Composable
private fun ShareLinkImportSection(
    value: String,
    onValueChange: (String) -> Unit,
    loading: Boolean,
    message: String?,
    error: String?,
    onImport: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(text = "粘贴分享链接", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = value,
            onValueChange = onValueChange,
            label = { Text(text = "vmess://、vless://、trojan://、ss://") },
            minLines = 3,
        )
        Button(
            enabled = !loading,
            onClick = onImport,
        ) {
            Text(text = if (loading) "解析中" else "解析并导入")
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
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(text = "扫描二维码", style = MaterialTheme.typography.titleMedium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                enabled = !loading,
                onClick = onOpenScanner,
            ) {
                Text(text = if (loading) "导入中" else "打开相机")
            }
            OutlinedButton(onClick = onGalleryPlaceholder) {
                Text(text = "相册识别")
            }
        }

        if (scannerVisible) {
            CameraQrScanner(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 280.dp, max = 360.dp),
                onCodeFound = onCodeFound,
            )
            OutlinedButton(onClick = onCloseScanner) {
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
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(text = "添加订阅", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = value,
            onValueChange = onValueChange,
            label = { Text(text = "订阅 URL") },
            singleLine = true,
        )
        Button(
            enabled = !loading,
            onClick = onImport,
        ) {
            Text(text = if (loading) "导入中" else "拉取并导入")
        }
        ImportResultMessage(loading = loading, message = message, error = error)
    }
}

@Composable
private fun ImportResultMessage(
    loading: Boolean,
    message: String?,
    error: String?,
) {
    if (loading) {
        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp),
        )
    }

    error?.let {
        Text(
            text = it,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }

    message?.let {
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = it,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
