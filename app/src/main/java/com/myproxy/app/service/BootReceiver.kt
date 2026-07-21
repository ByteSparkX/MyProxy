package com.myproxy.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.core.content.ContextCompat
import com.myproxy.app.core.AppLog
import com.myproxy.app.core.ConfigBuilder
import com.myproxy.app.data.NodeRepository
import com.myproxy.app.data.SettingsRepository
import com.myproxy.app.model.RoutingMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching {
                val appContext = context.applicationContext
                val settingsRepository = SettingsRepository.getInstance(appContext)
                if (!settingsRepository.isBootStartEnabled()) {
                    AppLog.i(TAG, "开机自启未开启，跳过恢复连接。")
                } else if (VpnService.prepare(appContext) != null) {
                    AppLog.w(TAG, "VPN 尚未授权，无法在开机后自动恢复。")
                } else {
                    val routingMode = settingsRepository.getRoutingMode()
                    val nodeId = settingsRepository.getSelectedNodeId()
                    val node = nodeId?.let { NodeRepository.getInstance(appContext).getById(it) }
                    if (routingMode != RoutingMode.DIRECT && node == null) {
                        AppLog.w(TAG, "没有可恢复的选中节点。")
                    } else {
                        val dnsServers = settingsRepository.getCustomDnsServers()
                        val configJson = if (routingMode == RoutingMode.DIRECT) {
                            ConfigBuilder.buildDirect(dnsServers)
                        } else {
                            ConfigBuilder.build(
                                node = requireNotNull(node),
                                dnsServers = dnsServers,
                                routingMode = routingMode,
                            )
                        }
                        ContextCompat.startForegroundService(
                            appContext,
                            Intent(appContext, MyVpnService::class.java).apply {
                                action = MyVpnService.ACTION_START
                                putExtra(MyVpnService.EXTRA_CONFIG_JSON, configJson)
                            },
                        )
                        AppLog.i(
                            TAG,
                            "已请求开机恢复连接：mode=$routingMode" +
                                node?.let { " remark=${it.remark} protocol=${it.protocol}" }.orEmpty(),
                        )
                    }
                }
            }.onFailure { error ->
                AppLog.e(TAG, "开机恢复连接失败。", error)
            }.also {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
