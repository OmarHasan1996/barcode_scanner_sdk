package com.enoc.sdk.scanner.core

import android.content.Context

/**
 * Main entry point for the Scanner SDK.
 * This SDK provides high-performance barcode scanning without external dependencies.
 */
object ScannerSdk {
    private var isInitialized = false

    /**
     * Initialize the SDK.
     */
    fun init(context: Context) {
        if (isInitialized) return
        // Perform any necessary setup here
        isInitialized = true
    }

    fun isInitialized(): Boolean = isInitialized
}
