package com.myproxy.app.core

import android.util.Log
import com.myproxy.app.BuildConfig

object AppLog {
    // release 包默认关闭普通调试流水，只保留告警和错误。
    fun d(tag: String, message: String) {
        if (BuildConfig.VERBOSE_LOGGING) {
            Log.d(tag, message)
        }
    }

    fun i(tag: String, message: String) {
        if (BuildConfig.VERBOSE_LOGGING) {
            Log.i(tag, message)
        }
    }

    fun w(tag: String, message: String, error: Throwable? = null) {
        if (error == null || !BuildConfig.VERBOSE_LOGGING) {
            Log.w(tag, message)
        } else {
            Log.w(tag, message, error)
        }
    }

    fun e(tag: String, message: String, error: Throwable? = null) {
        if (error == null || !BuildConfig.VERBOSE_LOGGING) {
            Log.e(tag, message)
        } else {
            Log.e(tag, message, error)
        }
    }
}
