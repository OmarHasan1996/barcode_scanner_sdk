package com.enoc.sdk.scanner.core.decoder

import com.enoc.sdk.scanner.core.core.RowRuns
import com.enoc.sdk.scanner.core.model.BarcodeFormat
import com.enoc.sdk.scanner.core.model.BarcodeResult

/**
 * Tries each requested format's 1D barcode decoder against a row in turn, returning the
 * first successful decode.
 */
class CompositeDecoder(formats: Set<BarcodeFormat>) {

    private val decoders: List<Decoder> = formats.mapNotNull {
        when (it) {
            BarcodeFormat.EAN_13 -> EAN13Decoder()
            BarcodeFormat.UPC_A -> UPCADecoder()
            BarcodeFormat.CODE_128 -> Code128Decoder()
            BarcodeFormat.VEHICLE_PLATE -> null // 2D Vehicle plate scanning is handled by VehiclePlateAnalyzer
        }
    }

    fun decode(rowRuns: RowRuns): BarcodeResult? {
        for (decoder in decoders) {
            decoder.decode(rowRuns)?.let { return it }
        }
        return null
    }
}
