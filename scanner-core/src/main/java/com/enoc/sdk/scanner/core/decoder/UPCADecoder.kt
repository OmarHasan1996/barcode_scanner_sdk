package com.enoc.sdk.scanner.core.decoder

import com.enoc.sdk.scanner.core.model.BarcodeFormat
import com.enoc.sdk.scanner.core.model.BarcodeResult

/**
 * UPC-A is structurally identical to EAN-13 with an implied leading digit of 0
 * (all-L parity on the left 6 digits). We reuse the EAN-13 state machine and
 * just re-tag/reformat the result as 12-digit UPC-A when the leading digit is 0.
 */
class UPCADecoder : EAN13Decoder() {

    override fun finalizeResult(ean13: String): BarcodeResult? {
        if (!ean13.startsWith("0")) return null // not a valid UPC-A encoding
        return BarcodeResult(text = ean13.substring(1), format = BarcodeFormat.UPC_A)
    }
}
