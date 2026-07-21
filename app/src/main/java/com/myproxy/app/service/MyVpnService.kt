package com.myproxy.app.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.myproxy.app.MainActivity
import com.myproxy.app.R
import com.myproxy.app.core.AppLog
import com.myproxy.app.core.Tun2SocksManager
import com.myproxy.app.core.XrayCore
import com.myproxy.app.data.AppProxyMode
import com.myproxy.app.data.AppProxySettings
import com.myproxy.app.data.SettingsRepository
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MyVpnService : VpnService() {
    private var tunFd: ParcelFileDescriptor? = null
    private var isForeground = false
    private var isStarting = false
    private val runtimeMutex = Mutex()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val settingsRepository by lazy { SettingsRepository.getInstance(applicationContext) }

    override fun onCreate() {
        super.onCreate()
        VpnServiceBridge.register(this)
        AppLog.i(TAG, "VPN 服务已创建。")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 启动顺序：先启动本地 SOCKS 入站，再建立 TUN，最后启动 tun2socks。
        return when (intent?.action) {
            ACTION_START -> {
                val configJson = intent.getStringExtra(EXTRA_CONFIG_JSON)
                if (configJson.isNullOrBlank()) {
                    AppLog.e(TAG, "启动 VPN 失败：缺少 Xray 配置。")
                    VpnConnectionState.setError("缺少连接配置，已取消连接")
                    stopSelf(startId)
                    return START_NOT_STICKY
                }
                startVpnRuntime(configJson)
                START_NOT_STICKY
            }
            ACTION_STOP -> {
                serviceScope.launch {
                    stopVpnRuntime(markDisconnected = true)
                    stopSelf(startId)
                }
                START_NOT_STICKY
            }
            else -> {
                // 不保留无法恢复配置的粘性服务，避免进程重建后出现空转状态。
                AppLog.w(TAG, "收到未知 VPN 服务 action：${intent?.action}")
                if (!isStarting && tunFd == null) {
                    VpnConnectionState.setDisconnected()
                    stopSelf(startId)
                }
                START_NOT_STICKY
            }
        }
    }

    override fun onRevoke() {
        // 用户或系统撤销 VPN 授权时，按反向顺序释放桥接链路。
        AppLog.i(TAG, "VPN 授权已撤销，准备清理桥接链路。")
        serviceScope.launch { stopVpnRuntime(markDisconnected = true) }
        super.onRevoke()
    }

    override fun onDestroy() {
        // 先取消主线程任务，避免 onDestroy 等待持锁协程时互相阻塞。
        serviceScope.cancel()
        runBlocking(Dispatchers.IO) {
            stopVpnRuntime(markDisconnected = true)
        }
        VpnServiceBridge.unregister(this)
        AppLog.i(TAG, "VPN 服务已销毁。")
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // 用户划掉界面不等于主动断开，前台 VPN 服务继续保护连接。
        AppLog.i(TAG, "应用界面已移出最近任务，VPN 服务继续运行。")
        super.onTaskRemoved(rootIntent)
    }

    private fun startVpnRuntime(configJson: String) {
        if (isStarting) {
            AppLog.i(TAG, "VPN 链路正在启动或已运行，跳过重复启动。")
            VpnConnectionState.setConnecting()
            return
        }

        if (tunFd != null && Tun2SocksManager.isRunning() && XrayCore.isRunning()) {
            AppLog.i(TAG, "VPN 链路已运行，跳过重复启动。")
            TrafficStatsRepository.start(serviceScope)
            VpnConnectionState.setConnected()
            return
        }

        isStarting = true
        VpnConnectionState.setConnecting()

        try {
            // startForegroundService 后必须尽快进入前台，核心初始化不能占用这段时限。
            startAsForegroundService(connected = false)
        } catch (error: Throwable) {
            isStarting = false
            VpnConnectionState.setError("无法启动前台 VPN 服务")
            stopSelf()
            return
        }

        serviceScope.launch {
            runtimeMutex.withLock {
                try {
                    // 若上一次异常只留下部分资源，先清理再重新建立完整链路。
                    if (tunFd != null || Tun2SocksManager.isRunning() || XrayCore.isRunning()) {
                        stopVpnRuntimeLocked(markDisconnected = false)
                        isStarting = true
                        startAsForegroundService(connected = false)
                    }

                    AppLog.i(TAG, "准备启动 XrayCore。")
                    XrayCore.start(applicationContext, configJson)

                    val dnsServers = settingsRepository.getCustomDnsServers().ifEmpty { listOf(DEFAULT_DNS) }
                    val appProxySettings = settingsRepository.getAppProxySettings()
                    val establishedFd = establishTun(
                        dnsServers = dnsServers,
                        appProxySettings = appProxySettings,
                    )

                    AppLog.i(TAG, "准备启动 tun2socks。")
                    Tun2SocksManager.start(
                        context = applicationContext,
                        tunFd = establishedFd.fd,
                        socksHost = SOCKS_HOST,
                        socksPort = SOCKS_PORT,
                        mtu = VPN_MTU,
                        dnsServer = dnsServers.first(),
                    )
                    // tun2socks 启动后读取 native 统计接口，每秒推送给 UI。
                    TrafficStatsRepository.start(serviceScope)
                    startAsForegroundService(connected = true)
                    AppLog.i(TAG, "VPN 链路启动成功。")
                    VpnConnectionState.setConnected()
                } catch (error: Throwable) {
                    // 不透传第三方异常文本，避免配置片段进入日志或界面。
                    AppLog.e(TAG, "VPN 链路启动失败：${error::class.java.simpleName}")
                    stopVpnRuntimeLocked(markDisconnected = false)
                    VpnConnectionState.setError(toSafeStartupMessage(error))
                    stopSelf()
                } finally {
                    isStarting = false
                }
            }
        }
    }

    private suspend fun stopVpnRuntime(markDisconnected: Boolean) {
        runtimeMutex.withLock {
            stopVpnRuntimeLocked(markDisconnected)
        }
    }

    private suspend fun stopVpnRuntimeLocked(markDisconnected: Boolean) {
        isStarting = false
        // 停止顺序必须反向执行：先停桥接，再关 TUN，最后停内核。
        TrafficStatsRepository.stopAndReset()
        runCatching { Tun2SocksManager.stop() }
            .onFailure { AppLog.e(TAG, "停止 tun2socks 失败。", it) }
        stopTun()
        runCatching { XrayCore.stop() }
            .onFailure { AppLog.e(TAG, "停止 XrayCore 失败。", it) }
        stopForegroundService()
        if (markDisconnected) {
            VpnConnectionState.setDisconnected()
        }
    }

    private fun establishTun(
        dnsServers: List<String>,
        appProxySettings: AppProxySettings,
    ): ParcelFileDescriptor {
        if (tunFd != null) {
            AppLog.i(TAG, "TUN 已建立，复用当前 fd=${tunFd?.fd}")
            return tunFd ?: error("TUN 状态异常")
        }

        // 建立 TUN 虚拟网卡；DNS 和分应用代理来自 DataStore，修改后下次连接生效。
        val builder = Builder()
            .setSession("我的代理")
            .addAddress(TUN_ADDRESS, TUN_PREFIX_LENGTH)
            .setMtu(VPN_MTU)
            .addRoute(DEFAULT_ROUTE, DEFAULT_ROUTE_PREFIX)

        dnsServers.forEach { dnsServer ->
            builder.addDnsServer(dnsServer)
        }

        applyAppProxySettings(builder, appProxySettings)

        val establishedFd = builder.establish()
            ?: error("VpnService.Builder.establish() 返回空 fd")

        tunFd = establishedFd
        AppLog.i(TAG, "TUN 建立成功，fd=${establishedFd.fd}")
        return establishedFd
    }

    private fun applyAppProxySettings(
        builder: Builder,
        settings: AppProxySettings,
    ) {
        // Android 不允许 allowed 与 disallowed 规则混用；本应用在两种模式下都避免进入 VPN。
        val targetPackages = settings.packageNames - packageName
        if (settings.mode == AppProxyMode.WHITELIST && targetPackages.isEmpty()) {
            error("白名单模式未选择可代理应用")
        }

        if (settings.mode == AppProxyMode.BLACKLIST) {
            builder.addDisallowedApplication(packageName)
        }

        var appliedCount = 0
        targetPackages.forEach { targetPackage ->
            runCatching {
                when (settings.mode) {
                    AppProxyMode.BLACKLIST -> builder.addDisallowedApplication(targetPackage)
                    AppProxyMode.WHITELIST -> builder.addAllowedApplication(targetPackage)
                }
                appliedCount += 1
            }.onFailure { error ->
                AppLog.w(TAG, "应用代理规则无效，已跳过包名：$targetPackage", error)
            }
        }

        if (settings.mode == AppProxyMode.WHITELIST && appliedCount == 0) {
            error("白名单模式没有可用应用，请重新选择")
        }

        AppLog.i(TAG, "已应用分应用代理规则：mode=${settings.mode} count=$appliedCount")
    }

    private fun stopTun() {
        val fd = tunFd ?: run {
            AppLog.i(TAG, "TUN 未建立，跳过关闭。")
            return
        }
        tunFd = null

        try {
            val rawFd = fd.fd
            fd.close()
            AppLog.i(TAG, "TUN 已关闭，fd=$rawFd")
        } catch (error: IOException) {
            AppLog.e(TAG, "关闭 TUN 失败。", error)
        }
    }

    private fun startAsForegroundService(connected: Boolean) {
        createNotificationChannel()
        if (!hasNotificationPermission()) {
            // Android 13+ 未授权通知时不要崩溃；前台服务仍尝试启动，由系统决定展示方式。
            AppLog.w(TAG, "通知权限未授权，前台服务通知可能不可见。")
        }

        val notification = buildNotification(connected)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            isForeground = true
            AppLog.i(TAG, if (connected) "前台服务通知已更新。" else "前台服务已启动。")
        }.onFailure { error ->
            AppLog.e(TAG, "启动前台服务失败。", error)
            throw error
        }
    }

    private fun stopForegroundService() {
        if (isForeground) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
            isForeground = false
            AppLog.i(TAG, "前台服务已停止。")
        }
        NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "我的代理",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "代理连接状态"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(connected: Boolean): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopVpnIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, MyVpnService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(if (connected) "我的代理 · 已连接" else "我的代理 · 正在连接")
            .setContentText(if (connected) "正在保护网络连接" else "正在建立安全连接")
            .setContentIntent(openAppIntent)
            .addAction(R.drawable.ic_notification, "断开", stopVpnIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun hasNotificationPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    private fun toSafeStartupMessage(error: Throwable): String {
        val safeMessage = error.message.orEmpty()
        return if (
            safeMessage.startsWith("白名单模式") ||
            safeMessage.startsWith("VpnService.Builder.establish") ||
            safeMessage.startsWith("TUN fd 无效")
        ) {
            safeMessage
        } else {
            "VPN 链路启动失败，请检查节点和网络设置"
        }
    }

    companion object {
        private const val TAG = "MyVpnService"
        private const val NOTIFICATION_CHANNEL_ID = "myproxy_vpn"
        private const val NOTIFICATION_ID = 1001
        private const val VPN_MTU = 1500
        private const val TUN_ADDRESS = "10.0.0.2"
        private const val TUN_PREFIX_LENGTH = 32
        private const val DEFAULT_ROUTE = "0.0.0.0"
        private const val DEFAULT_ROUTE_PREFIX = 0
        private const val DEFAULT_DNS = "1.1.1.1"
        private const val SOCKS_HOST = "127.0.0.1"
        private const val SOCKS_PORT = 10808
        const val ACTION_START = "com.myproxy.app.action.START_VPN"
        const val ACTION_STOP = "com.myproxy.app.action.STOP_VPN"
        const val EXTRA_CONFIG_JSON = "com.myproxy.app.extra.CONFIG_JSON"
    }
}
