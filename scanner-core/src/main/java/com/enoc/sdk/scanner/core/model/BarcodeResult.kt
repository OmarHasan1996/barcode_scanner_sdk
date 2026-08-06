package com.enoc.sdk.scanner.core.model

/**
 * Result of a successful decode.
 *
 * @param text decoded barcode value (digits for EAN/UPC, ASCII payload for Code128)
 * @param format the symbology that matched
 * @param rowY the pixel row (in analyzer coordinates) the scan succeeded on, useful for debugging/overlay
 */
data class BarcodeResult(
    val text: String,
    val format: BarcodeFormat,
    val rowY: Int = -1
)
