package com.enoc.sdk.scanner.core

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.media.AudioManager
import android.media.ToneGenerator
import com.enoc.sdk.scanner.core.model.BarcodeFormat
import com.enoc.sdk.scanner.core.utils.Logger
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.Executors

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
        applyLoggingConfig()
        // Pre-warm ML Kit text recognition client on background thread
        Executors.newSingleThreadExecutor().execute {
            try {
                TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            } catch (_: Exception) {}
        }
    }

    override fun setConfiguration(config: Bundle) {
        this.config = config
        applyLoggingConfig()
    }

    private fun applyLoggingConfig() {
        Logger.isLogEnabled = config.getBoolean(Scanner.SCANNER_IS_LOG_ENABLE, true)
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
            Scanner.CodeType.PLATE -> BarcodeFormat.VEHICLE_PLATE
        })
    }

    override fun safeGetVersion(): String = scanLibVersion

    override fun startScan(timeoutSecond: Int, listener: OnScanListener) {
        Logger.i("ENOC_PLATE_DEBUG", "[STEP 1 - START SCAN] Scanner session starting with enabled formats: $enabledFormats")
        // Validate parameters
        if (timeoutSecond <= 0) {
            listener.onScanResult(Scanner.SCANNER_PARAM_INVALID, "Timeout should be more than zero".toByteArray())
            return
        }

        val resWidth = config.getInt(Scanner.SCANNER_RESOLUTION_WIDTH, 1280)
        val resHeight = config.getInt(Scanner.SCANNER_RESOLUTION_HEIGHT, 720)
        if (resWidth <= 0 || resHeight <= 0) {
            listener.onScanResult(Scanner.SCANNER_PARAM_INVALID, "Scanner Resolution should be more than zero".toByteArray())
            return
        }

        stopScanInternal()
        this.activeListener = listener

        val task = Runnable {
            if (activeListener == listener) {
                dispatchResult(Scanner.SCANNER_TIMEOUT, null)
            }
        }
        timeoutRunnable = task
        mainHandler.postDelayed(task, timeoutSecond * 1000L)
    }

    override fun stopScan() {
        if (activeListener != null) {
            val listener = activeListener
            stopScanInternal()
            listener?.onScanResult(Scanner.SCANNER_EXIT, null)
        } else {
            stopScanInternal()
        }
    }

    private fun stopScanInternal() {
        timeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        timeoutRunnable = null
        activeListener = null
    }

    override fun release() {
        stopScanInternal()
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
        if (result == Scanner.SCANNER_SUCCESS) {
            playBeep()
            val listener = activeListener
            val continueScan = config.getBoolean(Scanner.SCANNER_CONTINUE_SCAN, false)
            if (!continueScan) {
                stopScanInternal()
            }
            listener?.onScanResult(result, data?.toByteArray())
        } else {
            val listener = activeListener
            stopScanInternal()
            listener?.onScanResult(result, data?.toByteArray())
        }
    }

    private fun playBeep() {
        if (config.getBoolean(Scanner.SCANNER_PLAY_BEEP, false)) {
            try {
                if (toneGenerator == null) {
                    toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
                }
                toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP)
            } catch (_: Exception) {
                // Ignore failure to play beep
            }
        }
    }
}
