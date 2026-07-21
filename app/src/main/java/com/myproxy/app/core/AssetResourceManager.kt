package com.myproxy.app.core

import android.content.Context
import java.io.File
import libv2ray.Libv2ray

object AssetResourceManager {
    private const val TAG = "AssetResourceManager"
    private const val XRAY_DIR_NAME = "xray"
    private const val MIN_RESOURCE_BYTES = 1024L
    private val requiredAssets = listOf("geoip.dat", "geosite.dat")

    fun prepareCoreEnvironment(context: Context, force: Boolean = false): File {
        val appContext = context.applicationContext
        val xrayDir = File(appContext.filesDir, XRAY_DIR_NAME)

        try {
            if (!xrayDir.exists() && !xrayDir.mkdirs()) {
                error("无法创建 Xray 资源目录：${xrayDir.absolutePath}")
            }

            requiredAssets.forEach { assetName ->
                copyAssetIfNeeded(appContext, assetName, File(xrayDir, assetName), force)
            }

            // AAR 真实导出方法：设置核心资源目录；第二个参数为可选 key，本项目不写入密钥。
            Libv2ray.initCoreEnv(xrayDir.absolutePath, "")
            AppLog.i(TAG, "Xray 资源环境已初始化：${xrayDir.absolutePath}")
            return xrayDir
        } catch (error: Throwable) {
            AppLog.e(TAG, "Xray 资源环境初始化失败。", error)
            throw error
        }
    }

    private fun copyAssetIfNeeded(
        context: Context,
        assetName: String,
        targetFile: File,
        force: Boolean,
    ) {
        val sourceSize = getAssetSize(context, assetName)
        val targetSize = targetFile.length()

        if (!force && targetFile.exists() && targetSize > MIN_RESOURCE_BYTES && targetSize == sourceSize) {
            AppLog.i(TAG, "资源文件已存在，跳过复制：$assetName")
            return
        }

        try {
            targetFile.parentFile?.mkdirs()
            val tempFile = File(targetFile.parentFile, "$assetName.tmp")

            context.assets.open(assetName).use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            if (tempFile.length() <= MIN_RESOURCE_BYTES) {
                tempFile.delete()
                error("资源文件过小，复制失败：$assetName")
            }

            if (sourceSize != null && tempFile.length() != sourceSize) {
                tempFile.delete()
                error("资源文件大小不一致，复制失败：$assetName")
            }

            if (targetFile.exists() && !targetFile.delete()) {
                tempFile.delete()
                error("无法覆盖旧资源文件：$assetName")
            }

            if (!tempFile.renameTo(targetFile)) {
                tempFile.delete()
                error("无法保存资源文件：$assetName")
            }

            AppLog.i(TAG, "资源文件已复制：$assetName")
        } catch (error: Throwable) {
            AppLog.e(TAG, "准备资源文件失败：$assetName", error)
            throw error
        }
    }

    private fun getAssetSize(context: Context, assetName: String): Long? {
        return try {
            context.assets.open(assetName).use { input ->
                input.available().toLong()
            }
        } catch (error: Throwable) {
            AppLog.w(TAG, "读取资源文件大小失败，将直接尝试复制：$assetName", error)
            null
        }
    }
}
