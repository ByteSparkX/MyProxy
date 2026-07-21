package com.myproxy.app.ui

import android.content.Context

suspend fun runDebugCoreToggle(context: Context): String {
    // Release 不包含阶段测试配置，正式入口始终使用用户选中的节点。
    return "测试入口仅用于 Debug 构建"
}

fun isDebugCoreRunning(): Boolean = false
