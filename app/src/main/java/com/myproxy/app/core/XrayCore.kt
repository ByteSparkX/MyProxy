package com.myproxy.app.core

import android.content.Context
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import libv2ray.CoreController
import libv2ray.Libv2ray

object XrayCore {
    private const val TAG = "XrayCore"
    private const val CORE_TUN_FD_UNUSED = 0

    private val stateMutex = Mutex()
    private val running = AtomicBoolean(false)
    private val callback = XrayCoreCallback()
    private val processFinder = XrayProcessFinderStub()
    @Volatile
    private var appContext: Context? = null
    private var controller: CoreController? = null

    fun initialize(context: Context) {
        // 只保存 applicationContext，避免持有 Activity 或 Service 实例造成泄漏。
        appContext = context.applicationContext
    }

    suspend fun start(configJson: String) = withContext(Dispatchers.IO) {
        stateMutex.withLock {
            if (running.get() || controller?.getIsRunning() == true) {
                AppLog.i(TAG, "Xray core 已在运行，跳过重复启动。")
                return@withLock
            }

            if (configJson.isBlank()) {
                error("Xray 配置内容为空，已取消启动。")
            }

            runCatching {
                AppLog.i(TAG, "准备启动内核。")
                // 启动前确保 geo 资源已复制，并完成 AAR 核心环境初始化。
                AssetResourceManager.prepareCoreEnvironment(requireAppContext())

                val coreController = Libv2ray.newCoreController(callback)
                coreController.registerProcessFinder(processFinder)
                controller = coreController
                AppLog.i(TAG, "配置设置成功。")

                // 核心仅开放本地 SOCKS 入站，TUN fd 由独立 tun2socks 桥接层持有。
                coreController.startLoop(configJson, CORE_TUN_FD_UNUSED)
                running.set(true)
                AppLog.i(TAG, "内核启动成功。")
            }.onFailure { error ->
                controller?.let { failedController ->
                    runCatching { failedController.stopLoop() }
                }
                running.set(false)
                controller = null
                // 第三方异常文本可能包含配置片段，只记录异常类型。
                AppLog.e(TAG, "内核启动失败：${error::class.java.simpleName}")
                throw error
            }
        }
    }

    suspend fun start(context: Context, configJson: String) {
        // 兼容需要现场传入 Context 的调用点；统一转到 start(configJson)。
        initialize(context)
        start(configJson)
    }

    suspend fun stop() = withContext(Dispatchers.IO) {
        stateMutex.withLock {
            val coreController = controller
            if (coreController == null || (!running.get() && !coreController.getIsRunning())) {
                AppLog.i(TAG, "Xray core 未运行，跳过停止。")
                running.set(false)
                controller = null
                return@withLock
            }

            runCatching {
                coreController.stopLoop()
                AppLog.i(TAG, "停止成功。")
            }.onFailure { error ->
                AppLog.e(TAG, "Xray core 停止失败。", error)
                throw error
            }.also {
                running.set(false)
                controller = null
            }
        }
    }

    fun isRunning(): Boolean {
        return running.get() || runCatching { controller?.getIsRunning() == true }.getOrDefault(false)
    }

    private fun requireAppContext(): Context {
        return appContext ?: error("XrayCore 尚未初始化，请先调用 initialize(context) 或 start(context, configJson)。")
    }
}
