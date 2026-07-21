package com.myproxy.app.data

import androidx.room.TypeConverter
import com.myproxy.app.model.ProtocolType
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

class Converters {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @TypeConverter
    fun protocolToString(protocol: ProtocolType): String {
        return protocol.name
    }

    @TypeConverter
    fun stringToProtocol(value: String): ProtocolType {
        return ProtocolType.valueOf(value)
    }

    @TypeConverter
    fun stringListToJson(value: List<String>): String {
        // ALPN 列表保存为 JSON，避免手动拼接导致转义问题。
        return json.encodeToString(ListSerializer(String.serializer()), value)
    }

    @TypeConverter
    fun jsonToStringList(value: String): List<String> {
        return if (value.isBlank()) {
            emptyList()
        } else {
            json.decodeFromString(ListSerializer(String.serializer()), value)
        }
    }

    @TypeConverter
    fun stringMapToJson(value: Map<String, String>): String {
        // 扩展字段保存为 JSON；调用方不得放入真实密码、订阅链接等敏感内容。
        return json.encodeToString(
            MapSerializer(String.serializer(), String.serializer()),
            value,
        )
    }

    @TypeConverter
    fun jsonToStringMap(value: String): Map<String, String> {
        return if (value.isBlank()) {
            emptyMap()
        } else {
            json.decodeFromString(
                MapSerializer(String.serializer(), String.serializer()),
                value,
            )
        }
    }
}
