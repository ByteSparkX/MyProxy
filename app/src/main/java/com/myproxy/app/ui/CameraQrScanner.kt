package com.myproxy.app.ui

import android.annotation.SuppressLint
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.myproxy.app.core.AppLog
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

private const val TAG = "CameraQrScanner"

@Composable
fun CameraQrScanner(
    modifier: Modifier = Modifier,
    onCodeFound: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val hasEmittedResult = remember { AtomicBoolean(false) }
    val isDisposed = remember { AtomicBoolean(false) }
    val cameraProviderRef = remember { AtomicReference<ProcessCameraProvider?>(null) }
    val currentOnCodeFound by rememberUpdatedState(onCodeFound)
    val scanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build(),
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            isDisposed.set(true)
            cameraProviderRef.getAndSet(null)?.unbindAll()
            scanner.close()
            cameraExecutor.shutdown()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            PreviewView(viewContext).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                val previewView = this
                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                cameraProviderFuture.addListener(
                    {
                        if (isDisposed.get()) return@addListener
                        val cameraProvider = runCatching { cameraProviderFuture.get() }
                            .getOrElse { error ->
                                AppLog.e(TAG, "获取 CameraX Provider 失败。", error)
                                return@addListener
                            }
                        cameraProviderRef.set(cameraProvider)
                        val preview = Preview.Builder().build().also { useCase ->
                            useCase.setSurfaceProvider(previewView.surfaceProvider)
                        }
                        val analysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                            .also { useCase ->
                                useCase.setAnalyzer(cameraExecutor) { imageProxy ->
                                    analyzeQrCode(
                                        imageProxy = imageProxy,
                                        scanner = scanner,
                                        hasEmittedResult = hasEmittedResult,
                                        onCodeFound = { rawValue -> currentOnCodeFound(rawValue) },
                                    )
                                }
                            }

                        runCatching {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                analysis,
                            )
                        }.onFailure { error ->
                            AppLog.e(TAG, "绑定 CameraX 扫码用例失败。", error)
                        }
                    },
                    ContextCompat.getMainExecutor(context),
                )
            }
        },
    )
}

@SuppressLint("UnsafeOptInUsageError")
private fun analyzeQrCode(
    imageProxy: ImageProxy,
    scanner: com.google.mlkit.vision.barcode.BarcodeScanner,
    hasEmittedResult: AtomicBoolean,
    onCodeFound: (String) -> Unit,
) {
    val mediaImage = imageProxy.image
    if (mediaImage == null || hasEmittedResult.get()) {
        imageProxy.close()
        return
    }

    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
    scanner.process(image)
        .addOnSuccessListener { barcodes ->
            val rawValue = barcodes.firstNotNullOfOrNull { barcode ->
                barcode.rawValue?.takeIf(String::isNotBlank)
            }
            if (rawValue != null && hasEmittedResult.compareAndSet(false, true)) {
                // 不打印二维码内容，避免泄露节点链接。
                AppLog.i(TAG, "识别到二维码内容。")
                onCodeFound(rawValue)
            }
        }
        .addOnFailureListener { error ->
            AppLog.e(TAG, "二维码识别失败。", error)
        }
        .addOnCompleteListener {
            imageProxy.close()
        }
}
