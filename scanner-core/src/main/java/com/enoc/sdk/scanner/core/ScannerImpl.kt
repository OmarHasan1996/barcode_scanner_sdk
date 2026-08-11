package com.enoc.sdk.scanner.core

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.media.AudioManager
import android.media.ToneGenerator
import com.enoc.sdk.scanner.core.model.BarcodeFormat

class ScannerImpl : Scanner {
    private var config = Bundle()
    private val enabledFormats = mutableSetOf<BarcodeFormat>()
    private var activeListener: OnScanListener? = null
    private var toneGenerator: ToneGenerator? = null
    
    private val mainHandler = Handler(Looper.getMainLooper())
    private var timeoutRunnable: Runnable? = null

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
        stopScan()
        this.activeListener = listener
        
        if (timeoutSecond > 0) {
            val task = Runnable {
                if (activeListener == listener) {
                    dispatchResult(Scanner.SCANNER_TIMEOUT, null)
                }
            }
            timeoutRunnable = task
            mainHandler.postDelayed(task, timeoutSecond * 1000L)
        }
    }

    override fun stopScan() {
        timeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        timeoutRunnable = null
        activeListener = null
    }

    override fun release() {
        activeListener = null
        enabledFormats.clear()
        toneGenerator?.release()
        toneGenerator = null
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
        playBeep()
        activeListener?.onScanResult(result, data?.toByteArray())
        stopScan()
    }

    private fun playBeep() {
        if (config.getBoolean(Scanner.SCANNER_PLAY_BEEP, false)) {
            try {
                if (toneGenerator == null) {
                    toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
                }
                toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP)
            } catch (e: Exception) {
                // Ignore failure to play beep
            }
        }
    }
}
