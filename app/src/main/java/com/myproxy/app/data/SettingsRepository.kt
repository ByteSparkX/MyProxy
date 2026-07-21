package com.myproxy.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.myproxy.app.model.RoutingMode
import java.net.Inet6Address
import java.net.InetAddress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

class SettingsRepository private constructor(
    context: Context,
) {
    private val dataStore = context.applicationContext.settingsDataStore

    // 当前选中的节点 id，只保存本地数据库主键，不保存节点密码或订阅信息。
    val selectedNodeId: Flow<Long?> = dataStore.data.map { preferences ->
        preferences[KEY_SELECTED_NODE_ID]?.takeIf { it > 0L }
    }

    // 默认使用规则模式，兼顾国内访问速度和代理流量消耗。
    val routingMode: Flow<RoutingMode> = dataStore.data.map { preferences ->
        RoutingMode.fromValue(preferences[KEY_ROUTING_MODE])
    }

    val appProxyMode: Flow<AppProxyMode> = dataStore.data.map { preferences ->
        AppProxyMode.fromValue(preferences[KEY_APP_PROXY_MODE])
    }

    val selectedAppPackages: Flow<Set<String>> = dataStore.data.map { preferences ->
        preferences[KEY_SELECTED_APP_PACKAGES].orEmpty()
    }

    val bootStartEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[KEY_BOOT_START_ENABLED] ?: false
    }

    val customDns: Flow<String?> = dataStore.data.map { preferences ->
        preferences[KEY_CUSTOM_DNS]?.takeIf(String::isNotBlank)
    }

    suspend fun getSelectedNodeId(): Long? = selectedNodeId.first()

    suspend fun getRoutingMode(): RoutingMode = routingMode.first()

    suspend fun getAppProxySettings(): AppProxySettings {
        return AppProxySettings(
            mode = appProxyMode.first(),
            packageNames = selectedAppPackages.first(),
        )
    }

    suspend fun isBootStartEnabled(): Boolean = bootStartEnabled.first()

    suspend fun getCustomDnsServers(): List<String> = parseDnsServers(customDns.first())

    suspend fun setSelectedNodeId(nodeId: Long) {
        dataStore.edit { preferences ->
            preferences[KEY_SELECTED_NODE_ID] = nodeId
        }
    }

    suspend fun clearSelectedNodeId() {
        dataStore.edit { preferences ->
            preferences.remove(KEY_SELECTED_NODE_ID)
        }
    }

    suspend fun setRoutingMode(mode: RoutingMode) {
        dataStore.edit { preferences ->
            preferences[KEY_ROUTING_MODE] = mode.value
        }
    }

    suspend fun setAppProxyMode(mode: AppProxyMode) {
        dataStore.edit { preferences ->
            preferences[KEY_APP_PROXY_MODE] = mode.value
        }
    }

    suspend fun setSelectedAppPackages(packageNames: Set<String>) {
        dataStore.edit { preferences ->
            preferences[KEY_SELECTED_APP_PACKAGES] = packageNames
        }
    }

    suspend fun setBootStartEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_BOOT_START_ENABLED] = enabled
        }
    }

    suspend fun setCustomDns(value: String) {
        dataStore.edit { preferences ->
            val normalized = value.trim()
            if (normalized.isBlank()) {
                preferences.remove(KEY_CUSTOM_DNS)
            } else {
                preferences[KEY_CUSTOM_DNS] = normalized
            }
        }
    }

    companion object {
        private val KEY_SELECTED_NODE_ID = longPreferencesKey("selected_node_id")
        private val KEY_ROUTING_MODE = stringPreferencesKey("routing_mode")
        private val KEY_APP_PROXY_MODE = stringPreferencesKey("app_proxy_mode")
        private val KEY_SELECTED_APP_PACKAGES = stringSetPreferencesKey("selected_app_packages")
        private val KEY_BOOT_START_ENABLED = booleanPreferencesKey("boot_start_enabled")
        private val KEY_CUSTOM_DNS = stringPreferencesKey("custom_dns")

        @Volatile
        private var instance: SettingsRepository? = null

        fun getInstance(context: Context): SettingsRepository {
            return instance ?: synchronized(this) {
                instance ?: SettingsRepository(context.applicationContext)
                    .also { instance = it }
            }
        }
    }
}

enum class AppProxyMode(val value: String) {
    BLACKLIST("blacklist"),
    WHITELIST("whitelist"),
    ;

    companion object {
        fun fromValue(value: String?): AppProxyMode {
            return entries.firstOrNull { it.value == value } ?: BLACKLIST
        }
    }
}

data class AppProxySettings(
    val mode: AppProxyMode = AppProxyMode.BLACKLIST,
    val packageNames: Set<String> = emptySet(),
)

fun parseDnsServers(value: String?): List<String> {
    return splitDnsServers(value)
        .filter(::isValidNumericDnsAddress)
}

fun findInvalidDnsServers(value: String?): List<String> {
    return splitDnsServers(value)
        .filterNot(::isValidNumericDnsAddress)
}

private fun splitDnsServers(value: String?): List<String> {
    return value
        ?.split(",", "\n", " ", "\t")
        ?.map(String::trim)
        ?.filter(String::isNotBlank)
        ?.distinct()
        ?: emptyList()
}

private fun isValidNumericDnsAddress(value: String): Boolean {
    if (value.matches(Regex("^(?:\\d{1,3}\\.){3}\\d{1,3}$"))) {
        return value.split('.').all { part ->
            val octet = part.toIntOrNull() ?: return@all false
            octet in 0..255
        }
    }

    if (':' !in value || value.any { char -> !char.isDigit() && char.lowercaseChar() !in 'a'..'f' && char !in ":." }) {
        return false
    }

    return runCatching { InetAddress.getByName(value) is Inet6Address }.getOrDefault(false)
}
