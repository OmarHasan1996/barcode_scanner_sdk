package com.enoc.sdk.scanner.core.utils

import android.util.Log

internal object Logger {
    var isLogEnabled: Boolean = true

    fun d(tag: String, message: String) {
        if (isLogEnabled) Log.d(tag, message)
    }

    fun i(tag: String, message: String) {
        if (isLogEnabled) Log.i(tag, message)
    }

    fun w(tag: String, message: String) {
        if (isLogEnabled) Log.w(tag, message)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (isLogEnabled) {
            if (throwable != null) {
                Log.e(tag, message, throwable)
            } else {
                Log.e(tag, message)
            }
        }
    }
}
