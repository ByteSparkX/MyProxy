package com.myproxy.app.core

import android.content.Context
import hev.sockstun.TProxyService
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

object Tun2SocksManager {
    private const val TAG = "Tun2SocksManager"
    private const val DEFAULT_TASK_STACK_SIZE = 86_016

    private val stateMutex = Mutex()
    private val running = AtomicBoolean(false)

    suspend fun start(
        context: Context,
        tunFd: Int,
        socksHost: String = "127.0.0.1",
        socksPort: Int = 10808,
        mtu: Int = 1500,
        dnsServer: String = "1.1.1.1",
    ) = withContext(Dispatchers.IO) {
        stateMutex.withLock {
            if (running.get()) {
                AppLog.i(TAG, "tun2socks 已在运行，跳过重复启动。")
                return@withLock
            }

            require(tunFd > 0) { "TUN fd 无效，无法启动 tun2socks。" }

            val configFile = writeConfig(context, socksHost, socksPort, mtu, dnsServer)
            runCatching {
                // HevSocks5Tunnel 使用真实 TUN fd，把系统流量桥接到本地 SOCKS 入站。
                TProxyService.startService(configFile.absolutePath, tunFd)
                running.set(true)
                AppLog.i(TAG, "tun2socks 启动成功，SOCKS=$socksHost:$socksPort，DNS=$dnsServer。")
            }.onFailure { error ->
                // native 启动若只完成了一部分，也主动回收其全局状态。
                runCatching { TProxyService.stopService() }
                running.set(false)
                AppLog.e(TAG, "tun2socks 启动失败。", error)
                throw error
            }
        }
    }

    suspend fun stop() = withContext(Dispatchers.IO) {
        stateMutex.withLock {
            if (!running.get()) {
                AppLog.i(TAG, "tun2socks 未运行，跳过停止。")
                return@withLock
            }

            runCatching {
                TProxyService.stopService()
                AppLog.i(TAG, "tun2socks 已停止。")
            }.onFailure { error ->
                AppLog.e(TAG, "tun2socks 停止失败。", error)
                throw error
            }.also {
                running.set(false)
            }
        }
    }

    fun isRunning(): Boolean = running.get()

    fun getStats(): Tun2SocksStats? {
        if (!running.get()) return null

        return runCatching {
            val stats = TProxyService.getStats()
            require(stats.size >= 4) { "tun2socks stats 长度异常" }
            // native 顺序来自 hev_socks5_tunnel_stats：tx_packets、tx_bytes、rx_packets、rx_bytes。
            Tun2SocksStats(
                txPackets = stats[0].coerceAtLeast(0L),
                txBytes = stats[1].coerceAtLeast(0L),
                rxPackets = stats[2].coerceAtLeast(0L),
                rxBytes = stats[3].coerceAtLeast(0L),
            )
        }.onFailure { error ->
            AppLog.w(TAG, "读取 tun2socks 统计失败。", error)
        }.getOrNull()
    }

    private fun writeConfig(
        context: Context,
        socksHost: String,
        socksPort: Int,
        mtu: Int,
        dnsServer: String,
    ): File {
        val configFile = File(context.cacheDir, "tun2socks.conf")
        val config = """
            misc:
              task-stack-size: $DEFAULT_TASK_STACK_SIZE
              log-level: info
            tunnel:
              mtu: $mtu
            socks5:
              port: $socksPort
              address: '$socksHost'
              udp: 'udp'
        """.trimIndent() + "\n"

        configFile.writeText(config, Charsets.UTF_8)
        // DNS 由 VpnService.Builder 注入系统；这里记录同一参数，方便排查链路配置。
        AppLog.i(TAG, "tun2socks 配置已写入：${configFile.absolutePath}，DNS=$dnsServer")
        return configFile
    }
}

data class Tun2SocksStats(
    val txPackets: Long,
    val txBytes: Long,
    val rxPackets: Long,
    val rxBytes: Long,
)
