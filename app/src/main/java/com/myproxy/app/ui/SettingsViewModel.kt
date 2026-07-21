package com.myproxy.app.ui

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.myproxy.app.data.AppProxyMode
import com.myproxy.app.data.SettingsRepository
import com.myproxy.app.data.findInvalidDnsServers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val settingsRepository = SettingsRepository.getInstance(application)
    private val mutableInstalledApps = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
    private val mutableDnsFeedback = MutableStateFlow(DnsSaveFeedback())

    val installedApps: StateFlow<List<InstalledAppInfo>> = mutableInstalledApps
    val dnsFeedback: StateFlow<DnsSaveFeedback> = mutableDnsFeedback
    val appProxyMode: StateFlow<AppProxyMode> = settingsRepository.appProxyMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppProxyMode.BLACKLIST)
    val selectedAppPackages: StateFlow<Set<String>> = settingsRepository.selectedAppPackages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())
    val bootStartEnabled: StateFlow<Boolean> = settingsRepository.bootStartEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val customDns: StateFlow<String?> = settingsRepository.customDns
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        loadInstalledApps()
    }

    fun setAppProxyMode(mode: AppProxyMode) {
        viewModelScope.launch {
            settingsRepository.setAppProxyMode(mode)
        }
    }

    fun toggleApp(packageName: String) {
        viewModelScope.launch {
            val current = selectedAppPackages.value
            val next = if (packageName in current) current - packageName else current + packageName
            settingsRepository.setSelectedAppPackages(next)
        }
    }

    fun setBootStartEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setBootStartEnabled(enabled)
        }
    }

    fun saveCustomDns(value: String) {
        val invalidServers = findInvalidDnsServers(value)
        if (invalidServers.isNotEmpty()) {
            mutableDnsFeedback.value = DnsSaveFeedback(error = "DNS 必须是有效的 IPv4 或 IPv6 地址")
            return
        }

        viewModelScope.launch {
            settingsRepository.setCustomDns(value)
            mutableDnsFeedback.value = DnsSaveFeedback(message = "DNS 设置已保存，下次连接生效")
        }
    }

    private fun loadInstalledApps() {
        viewModelScope.launch {
            mutableInstalledApps.value = withContext(Dispatchers.IO) {
                val packageManager = getApplication<Application>().packageManager
                val launchIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                packageManager.queryIntentActivities(launchIntent, PackageManager.MATCH_DEFAULT_ONLY)
                    .mapNotNull { resolveInfo ->
                        val packageName = resolveInfo.activityInfo?.packageName ?: return@mapNotNull null
                        InstalledAppInfo(
                            label = resolveInfo.loadLabel(packageManager).toString(),
                            packageName = packageName,
                            icon = resolveInfo.loadIcon(packageManager),
                        )
                    }
                    .distinctBy { it.packageName }
                    .sortedBy { it.label.lowercase() }
            }
        }
    }
}

data class InstalledAppInfo(
    val label: String,
    val packageName: String,
    val icon: Drawable,
)

data class DnsSaveFeedback(
    val message: String? = null,
    val error: String? = null,
)
