package com.enoc.sdk.scanner.core

import android.content.Context

/**
 * Global manager for the Scanner SDK lifecycle.
 */
object SDKManager {
    /**
     * Initialize the SDK and notify when ready.
     */
    fun init(context: Context, onReady: () -> Unit) {
        ScannerSdk.init(context)
        onReady()
    }
}
