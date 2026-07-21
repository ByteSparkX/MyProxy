package com.myproxy.app.ui

import android.content.Context
import com.myproxy.app.core.AppLog
import com.myproxy.app.core.TestConfigs
import com.myproxy.app.core.XrayCore

private const val TAG = "DebugCoreTestEntry"

suspend fun runDebugCoreToggle(context: Context): String {
    return runCatching {
        if (XrayCore.isRunning()) {
            AppLog.i(TAG, "准备停止阶段二测试内核。")
            XrayCore.stop()
            "内核已停止"
        } else {
            AppLog.i(TAG, "准备启动阶段二测试内核。")
            // 此入口仅用于阶段二真机日志验证，阶段三不会通过它接管系统流量。
            XrayCore.start(context, TestConfigs.LOCAL_SOCKS_TO_FREEDOM)
            "内核已启动"
        }
    }.getOrElse { error ->
        AppLog.e(TAG, "阶段二测试内核切换失败。", error)
        error.message ?: "内核操作失败"
    }
}

fun isDebugCoreRunning(): Boolean = XrayCore.isRunning()
