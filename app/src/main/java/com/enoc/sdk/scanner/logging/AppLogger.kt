package com.enoc.sdk.scanner.logging

import android.util.Log

enum class LogLevel {
    DEBUG, INFO, WARN, ERROR
}

interface AppLogger {
    fun d(feature: String, message: String, metadata: Map<String, String> = emptyMap())
    fun i(feature: String, message: String, metadata: Map<String, String> = emptyMap())
    fun w(feature: String, message: String, metadata: Map<String, String> = emptyMap(), throwable: Throwable? = null)
    fun e(feature: String, message: String, metadata: Map<String, String> = emptyMap(), throwable: Throwable? = null)
}

class AppLoggerImpl(
    private val isProduction: Boolean = false
) : AppLogger {

    companion object {
        private const val GLOBAL_TAG = "ENOC_SCANNER_SDK"
        
        // PII pattern to redact vehicle plates, e.g., DXB-1234
        private val PLATE_PATTERN = Regex("(?i)DXB-\\d+|A-\\d+|[A-Z]-\\d+")
    }

    override fun d(feature: String, message: String, metadata: Map<String, String>) {
        if (isProduction) return // SEC-005 & LOG-004: Debug messages compiled/stripped out of Prod
        logToPlatform(LogLevel.DEBUG, feature, message, metadata)
    }

    override fun i(feature: String, message: String, metadata: Map<String, String>) {
        logToPlatform(LogLevel.INFO, feature, message, metadata)
    }

    override fun w(feature: String, message: String, metadata: Map<String, String>, throwable: Throwable?) {
        if (isProduction) {
            // LOG-004: Forward to remote sink in Prod (e.g. Sentry/Crashlytics stub)
            sendToRemoteSink(LogLevel.WARN, feature, message, metadata, throwable)
        }
        logToPlatform(LogLevel.WARN, feature, message, metadata, throwable)
    }

    override fun e(feature: String, message: String, metadata: Map<String, String>, throwable: Throwable?) {
        if (isProduction) {
            // LOG-004: Forward to remote sink in Prod (e.g. Sentry/Crashlytics stub)
            sendToRemoteSink(LogLevel.ERROR, feature, message, metadata, throwable)
        }
        logToPlatform(LogLevel.ERROR, feature, message, metadata, throwable)
    }

    private fun logToPlatform(
        level: LogLevel,
        feature: String,
        message: String,
        metadata: Map<String, String>,
        throwable: Throwable? = null
    ) {
        val sanitizedMessage = redactSensitiveData(message)
        val structuredFields = metadata.toMutableMap().apply {
            put("feature", feature)
            put("level", level.name)
            // Example machine-queryable metadata (LOG-002)
            if (!containsKey("requestId")) put("requestId", "REQ-SYS-AUTO")
        }.map { "${it.key}=\"${redactSensitiveData(it.value)}\"" }.joinToString(", ")

        val fullLogLine = "[$structuredFields] message=\"$sanitizedMessage\""

        when (level) {
            LogLevel.DEBUG -> Log.d(GLOBAL_TAG, fullLogLine)
            LogLevel.INFO -> Log.i(GLOBAL_TAG, fullLogLine)
            LogLevel.WARN -> Log.w(GLOBAL_TAG, fullLogLine, throwable)
            LogLevel.ERROR -> Log.e(GLOBAL_TAG, fullLogLine, throwable)
        }
    }

    private fun redactSensitiveData(input: String): String {
        // SEC-005 & LOG-003: Explicitly redact vehicle plate numbers and PII
        return input.replace(PLATE_PATTERN, "[REDACTED_PLATE]")
    }

    private fun sendToRemoteSink(
        level: LogLevel,
        feature: String,
        message: String,
        metadata: Map<String, String>,
        throwable: Throwable?
    ) {
        // Production log routing sink stub representing Crashlytics / Sentry routing
        if (level == LogLevel.ERROR && feature.isNotEmpty() && message.isNotEmpty() && metadata.isEmpty() && throwable != null) {
            Log.v("SINK", "Remote upload triggered")
        }
    }
}
