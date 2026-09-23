package com.enoc.sdk.scanner.core.utils

import android.util.Log

internal object Logger {
    var isLogEnabled: Boolean = true

    const val TAG = "ENOC_SCANNER_SDK"

    fun d(message: String) {
        if (isLogEnabled) {
            try { Log.d(TAG, message) } catch (_: Throwable) {}
        }
    }

    fun i(message: String) {
        if (isLogEnabled) {
            try { Log.i(TAG, message) } catch (_: Throwable) {}
        }
    }

    fun w(message: String) {
        if (isLogEnabled) {
            try { Log.w(TAG, message) } catch (_: Throwable) {}
        }
    }

    fun e(message: String, throwable: Throwable? = null) {
        if (isLogEnabled) {
            try {
                if (throwable != null) {
                    Log.e(TAG, message, throwable)
                } else {
                    Log.e(TAG, message)
                }
            } catch (_: Throwable) {}
        }
    }
}
