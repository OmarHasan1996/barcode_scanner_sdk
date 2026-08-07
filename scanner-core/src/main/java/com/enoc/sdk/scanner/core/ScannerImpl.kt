package com.enoc.sdk.scanner.core

import android.os.Bundle
import com.enoc.sdk.scanner.core.model.BarcodeFormat

class ScannerImpl : Scanner {
    private var config = Bundle()
    private val enabledFormats = mutableSetOf<BarcodeFormat>()
    private var activeListener: OnScanListener? = null

    override val scanLibVersion: String = "1.0.0-enoc-core"

    override fun initScanner(config: Bundle) {
        this.config = config
    }

    override fun setConfiguration(config: Bundle) {
        this.config = config
    }

    override fun enableAllCodeType(enable: Boolean) {
        if (enable) {
            enabledFormats.addAll(BarcodeFormat.entries)
        } else {
            enabledFormats.clear()
        }
    }

    override fun setCodeTypeOn(codeType: Scanner.CodeType) {
        enabledFormats.add(when (codeType) {
            Scanner.CodeType.CODE128 -> BarcodeFormat.CODE_128
            Scanner.CodeType.EAN13 -> BarcodeFormat.EAN_13
            Scanner.CodeType.UPCA -> BarcodeFormat.UPC_A
        })
    }

    override fun safeGetVersion(): String = scanLibVersion

    override fun startScan(timeoutSecond: Int, listener: OnScanListener) {
        this.activeListener = listener
        // In this architecture, startScan signals the manager that camera is ready.
        // The actual scan results are produced by ScannerView -> BarcodeAnalyzer.
    }

    override fun stopScan() {
        activeListener = null
    }

    override fun release() {
        activeListener = null
        enabledFormats.clear()
    }

    /**
     * Internal helper to get the currently enabled formats.
     */
    fun getEnabledFormats(): Set<BarcodeFormat> {
        return if (enabledFormats.isEmpty()) {
            setOf(BarcodeFormat.CODE_128, BarcodeFormat.EAN_13, BarcodeFormat.UPC_A)
        } else {
            enabledFormats.toSet()
        }
    }

    /**
     * Internal helper to get current configuration.
     */
    fun getConfig(): Bundle = config
    
    /**
     * Internal helper to dispatch results back to the listener.
     */
    fun dispatchResult(result: Int, data: String?) {
        activeListener?.onScanResult(result, data?.toByteArray())
    }
}
