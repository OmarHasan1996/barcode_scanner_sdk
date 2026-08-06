package com.enoc.sdk.scanner.core.decoder

import com.enoc.sdk.scanner.core.core.RowRuns
import com.enoc.sdk.scanner.core.model.BarcodeResult

/**
 * A single-symbology decoder. Given the run-lengths of one scanned row,
 * try to find and decode a barcode of this decoder's format.
 * Returns null if no valid barcode of this type is present in the row.
 */
interface Decoder {
    fun decode(rowRuns: RowRuns): BarcodeResult?
}
