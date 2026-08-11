package com.enoc.sdk.scanner

import android.content.Context
import android.os.Bundle
import com.enoc.sdk.scanner.core.SDKManager
import com.enoc.sdk.scanner.core.OnScanListener
import com.enoc.sdk.scanner.core.Scanner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.suspendCancellableCoroutine
import java.nio.charset.Charset
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.milliseconds
import android.util.Log

sealed class ScanOutcome {
    data class Success(val value: String) : ScanOutcome()
    data class Error(val code: Int, val message: String) : ScanOutcome()
}

class ScannerManager(private val appContext: Context) {
    companion object {
        private const val TAG = "ScannerManager"
        private const val CHARSET_NAME = "UTF-8"
        private const val SCAN_DEBOUNCE_MS = 1500L
        private const val INIT_TIMEOUT_MS = 5000L
    }

    private var scanner: Scanner? = null
    private var initialized = false
    private val initMutex = Mutex()

    private var lastScanValue: String? = null
    private var lastScanTimeMs = 0L

    val config = Bundle().apply {
        putBoolean(Scanner.SCANNER_PLAY_BEEP, true)
        putBoolean(Scanner.SCANNER_CONTINUE_SCAN, false)
        putInt(Scanner.SCANNER_DECODE_MODE, Scanner.DECODE_MODE_ALL)
        putBoolean(Scanner.SCANNER_IS_BACK_CAMERA, true)
        putBoolean(Scanner.SCANNER_IS_TORCH_ON, false)
    }

    suspend fun ensureInitialized() = initMutex.withLock {
        if (initialized) return@withLock

        try {
            val s = withTimeoutOrNull(INIT_TIMEOUT_MS.milliseconds) {
                getScanner()
            } ?: throw Exception("Scanner SDK initialization timed out")

            Log.i(TAG, "Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")

            try {
                s.initScanner(config)
                s.setConfiguration(config)
                s.enableAllCodeType(false)
                s.setCodeTypeOn(Scanner.CodeType.CODE128)
            } catch (e: Exception) {
                Log.w(TAG, "Scanner configuration failed, but continuing: ${e.message}")
            }

            scanner = s
            initialized = true
            Log.i(TAG, "Scanner initialized. Lib version: ${s.safeGetVersion()}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize scanner: ${e.message}")
            initialized = false
            scanner = null
        }
    }

    private fun Scanner.safeGetVersion(): String =
        try { this.scanLibVersion } catch (_: Exception) { "unknown" }

    fun scanOnce(timeoutSecond: Int = 3): Flow<ScanOutcome> = callbackFlow {
        ensureInitialized()
        val scanner = scanner
        if (!initialized || scanner == null) {
            trySend(ScanOutcome.Error(Scanner.SCANNER_PARAM_INVALID, "Scanner not initialized"))
            close()
            return@callbackFlow
        }

        try {
            scanner.startScan(timeoutSecond, OnScanListener { result, data ->
                try {
                    if (result != Scanner.SCANNER_SUCCESS) {
                        Log.d(TAG, "Scan failed or timed out. Result code: $result")
                        trySend(ScanOutcome.Error(result, describeError(result)))
                        close()
                        return@OnScanListener
                    }

                    val decoded = data?.let { decode(it) }
                    if (decoded == null) {
                        trySend(ScanOutcome.Error(Scanner.SCANNER_PARAM_INVALID, "Empty or invalid payload"))
                        close()
                        return@OnScanListener
                    }

                    val now = System.currentTimeMillis()
                    val isDuplicate = decoded == lastScanValue && (now - lastScanTimeMs) < SCAN_DEBOUNCE_MS

                    if (isDuplicate) {
                        Log.d(TAG, "Duplicate scan ignored: $decoded")
                        close()
                        return@OnScanListener
                    }

                    lastScanValue = decoded
                    lastScanTimeMs = now

                    trySend(ScanOutcome.Success(decoded))
                    Log.i(TAG, "Scanning success")
                    close()
                } catch (e: Exception) {
                    Log.e(TAG, "OnScanListener callback threw $e")
                    trySend(ScanOutcome.Error(Scanner.SCANNER_PARAM_INVALID, e.message ?: "Callback error"))
                    close()
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "startScan() threw $e")
            trySend(ScanOutcome.Error(Scanner.SCANNER_PARAM_INVALID, e.message ?: "Unknown error"))
            close()
        }

        awaitClose {
            // Standard cleanup
        }
    }

    private fun describeError(code: Int): String = when (code) {
        Scanner.SCANNER_EXIT -> "Scan cancelled"
        Scanner.SCANNER_TIMEOUT -> "Scan timed out"
        Scanner.SCANNER_PARAM_INVALID -> "Invalid scanner configuration"
        else -> "Scan failed (code $code)"
    }

    private fun decode(data: ByteArray): String? =
        try {
            String(data, Charset.forName(CHARSET_NAME)).trim()
        } catch (_: Exception) {
            try { String(data).trim() } catch (_: Exception) { null }
        }

    fun stopScan() {
        scanner?.stopScan()
    }

    fun release() {
        scanner?.release()
        scanner = null
        initialized = false
        lastScanValue = null
    }

    private suspend fun getScanner(): Scanner =
        suspendCancellableCoroutine { cont ->
            try {
                SDKManager.init(appContext) {
                    try {
                        if (cont.isActive) cont.resume(Scanner.getInstance())
                    } catch (e: Exception) {
                        Log.e(TAG, "Scanner.getInstance() failed: ${e.message}")
                        if (cont.isActive) cont.resumeWith(Result.failure(e))
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "SDKManager.init failed: ${e.message}")
                if (cont.isActive) cont.resumeWith(Result.failure(e))
            }
        }

    fun isInitialized() : Boolean = initialized
}
