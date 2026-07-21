package com.myproxy.app.model

/** 决定流量使用代理节点还是本地网络。 */
enum class RoutingMode(val value: String) {
    RULE("rule"),
    GLOBAL("global"),
    DIRECT("direct"),
    ;

    companion object {
        fun fromValue(value: String?): RoutingMode {
            return entries.firstOrNull { it.value == value } ?: RULE
        }
    }
}
