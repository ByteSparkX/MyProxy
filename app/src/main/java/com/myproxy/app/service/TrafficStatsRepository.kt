package com.myproxy.app.service

import com.myproxy.app.core.Tun2SocksManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class TrafficStatsState(
    val uploadSpeed: Long = 0L,
    val downloadSpeed: Long = 0L,
    val uploadTotal: Long = 0L,
    val downloadTotal: Long = 0L,
)

object TrafficStatsRepository {
    private val mutableState = MutableStateFlow(TrafficStatsState())
    private var monitorJob: Job? = null

    val state: StateFlow<TrafficStatsState> = mutableState.asStateFlow()

    fun start(scope: CoroutineScope) {
        if (monitorJob?.isActive == true) return

        monitorJob = scope.launch {
            val baseline = readBytes()
            var previous = baseline

            while (isActive) {
                delay(1_000)
                val current = readBytes()
                val uploadTotal = (current.uploadBytes - baseline.uploadBytes).coerceAtLeast(0L)
                val downloadTotal = (current.downloadBytes - baseline.downloadBytes).coerceAtLeast(0L)

                mutableState.value = TrafficStatsState(
                    uploadSpeed = (current.uploadBytes - previous.uploadBytes).coerceAtLeast(0L),
                    downloadSpeed = (current.downloadBytes - previous.downloadBytes).coerceAtLeast(0L),
                    uploadTotal = uploadTotal,
                    downloadTotal = downloadTotal,
                )
                previous = current
            }
        }
    }

    fun stopAndReset() {
        monitorJob?.cancel()
        monitorJob = null
        mutableState.value = TrafficStatsState()
    }

    private suspend fun readBytes(): TrafficBytes = withContext(Dispatchers.IO) {
        val stats = Tun2SocksManager.getStats()
        // upload 使用 TUN TX 字节，download 使用 TUN RX 字节。
        TrafficBytes(
            uploadBytes = stats?.txBytes ?: 0L,
            downloadBytes = stats?.rxBytes ?: 0L,
        )
    }

    private data class TrafficBytes(
        val uploadBytes: Long,
        val downloadBytes: Long,
    )
}
