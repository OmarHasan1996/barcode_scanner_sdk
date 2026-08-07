package com.enoc.sdk.scanner.core

import android.content.Context

/**
 * Main entry point for the Scanner SDK.
 * This SDK provides high-performance barcode scanning without external dependencies.
 */
object ScannerSdk {
    private var isInitialized = false
    private var scannerInstance: ScannerImpl? = null

    /**
     * Initialize the SDK.
     */
    fun init(context: Context) {
        if (isInitialized) return
        isInitialized = true
    }

    /**
     * Get a scanner instance.
     */
    fun getScanner(): Scanner {
        if (scannerInstance == null) {
            scannerInstance = ScannerImpl()
        }
        return scannerInstance!!
    }

    fun isInitialized(): Boolean = isInitialized
}
