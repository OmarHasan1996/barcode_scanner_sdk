package com.enoc.sdk.scanner.core.decoder

import com.enoc.sdk.scanner.core.core.RowRuns
import com.enoc.sdk.scanner.core.model.BarcodeFormat
import com.enoc.sdk.scanner.core.model.BarcodeResult


/**
 * Tries each requested format's decoder against a row in turn, returning the
 * first successful decode. Order matters a little for performance (put your
 * most likely format first) but not for correctness.
 */
class CompositeDecoder(formats: Set<BarcodeFormat>) {

    private val decoders: List<Decoder> = formats.map {
        when (it) {
            BarcodeFormat.EAN_13 -> EAN13Decoder()
            BarcodeFormat.UPC_A -> UPCADecoder()
            BarcodeFormat.CODE_128 -> Code128Decoder()
        }
    }

    fun decode(rowRuns: RowRuns): BarcodeResult? {
        for (decoder in decoders) {
            decoder.decode(rowRuns)?.let { return it }
        }
        return null
    }
}
