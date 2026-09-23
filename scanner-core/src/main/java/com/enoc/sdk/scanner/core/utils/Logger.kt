package com.enoc.sdk.scanner.core.utils

import android.util.Log

internal object Logger {
    var isLogEnabled: Boolean = true

    fun d(tag: String, message: String) {
        if (isLogEnabled) {
            try { Log.d(tag, message) } catch (_: Throwable) {}
        }
    }

    fun i(tag: String, message: String) {
        if (isLogEnabled) {
            try { Log.i(tag, message) } catch (_: Throwable) {}
        }
    }

    fun w(tag: String, message: String) {
        if (isLogEnabled) {
            try { Log.w(tag, message) } catch (_: Throwable) {}
        }
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (isLogEnabled) {
            try {
                if (throwable != null) {
                    Log.e(tag, message, throwable)
                } else {
                    Log.e(tag, message)
                }
            } catch (_: Throwable) {}
        }
    }
}
