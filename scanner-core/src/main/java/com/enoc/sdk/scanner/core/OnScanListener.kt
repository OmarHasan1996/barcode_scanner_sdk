package com.enoc.sdk.scanner.core

/**
 * Listener for barcode scan events.
 */
fun interface OnScanListener {
    /**
     * Called when a scan operation completes.
     * @param result One of the Scanner result constants (SUCCESS, TIMEOUT, etc.)
     * @param data The raw barcode bytes if successful.
     */
    fun onScanResult(result: Int, data: ByteArray?)
}
