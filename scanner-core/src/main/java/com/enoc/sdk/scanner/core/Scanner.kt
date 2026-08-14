package com.enoc.sdk.scanner.core

import android.os.Bundle

/**
 * High-level API for interacting with the Scanner SDK.
 */
interface Scanner {
    companion object {
        const val SCANNER_PLAY_BEEP = "scanner_play_beep"
        const val SCANNER_CONTINUE_SCAN = "scanner_continue_scan"
        const val SCANNER_DECODE_MODE = "scanner_decode_mode"
        const val SCANNER_IS_BACK_CAMERA = "scanner_is_back_camera"
        const val SCANNER_IS_TORCH_ON = "scanner_is_torch_on"
        const val SCANNER_RESOLUTION_WIDTH = "scanner_resolution_width"
        const val SCANNER_RESOLUTION_HEIGHT = "scanner_resolution_height"
        const val SCANNER_IS_LOG_ENABLE = "scanner_is_log_enable"
        const val SCANNER_ZOOM_ENABLE = "scanner_zoom_enable"

        const val DECODE_MODE_ALL = 0
        const val DECODE_MODE_SINGLE = 1

        const val SCANNER_SUCCESS = 0
        const val SCANNER_TIMEOUT = -1
        const val SCANNER_EXIT = -2
        const val SCANNER_PARAM_INVALID = -3

        /**
         * Standard provider for getting the current Scanner instance.
         */
        @JvmStatic
        fun getInstance(): Scanner = ScannerSdk.getScanner()
    }

    enum class CodeType {
        CODE128, EAN13, UPCA
    }

    val scanLibVersion: String

    fun initScanner(config: Bundle)
    fun setConfiguration(config: Bundle)
    fun enableAllCodeType(enable: Boolean)
    fun setCodeTypeOn(codeType: CodeType)
    fun safeGetVersion(): String
    
    fun startScan(timeoutSecond: Int, listener: OnScanListener)
    fun stopScan()
    fun release()
}
