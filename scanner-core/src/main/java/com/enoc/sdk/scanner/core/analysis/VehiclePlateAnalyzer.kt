package com.enoc.sdk.scanner.core.analysis

import android.graphics.Rect
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.enoc.sdk.scanner.core.decoder.PlateCandidate
import com.enoc.sdk.scanner.core.decoder.VehiclePlateDecoder
import com.enoc.sdk.scanner.core.model.BarcodeFormat
import com.enoc.sdk.scanner.core.model.BarcodeResult
import com.enoc.sdk.scanner.core.utils.Logger
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Dedicated 2D Vehicle License Plate Analyzer powered by ML Kit OCR.
 */
class VehiclePlateAnalyzer(
    private val continueScan: Boolean = false,
    private val onResult: (BarcodeResult) -> Unit
) : ImageAnalysis.Analyzer {

    private val vehiclePlateDecoder = VehiclePlateDecoder()

    // On-device ML Kit Text Recognizer for fast 2D plate detection
    private val textRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    private val isMlKitProcessing = AtomicBoolean(false)
    private val frameCounter = AtomicLong(0)

    private var lastCandidatePlate: String? = null
    private var candidateMatchCount: Int = 0

    @Volatile
    private var hasScanned = false

    fun reset() {
        Logger.d("[ANALYZER RESET] VehiclePlateAnalyzer reset")
        hasScanned = false
        isMlKitProcessing.set(false)
        frameCounter.set(0)
        lastCandidatePlate = null
        candidateMatchCount = 0
    }

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        if (hasScanned) {
            imageProxy.close()
            return
        }

        val frameNum = frameCounter.incrementAndGet()

        if (isMlKitProcessing.get()) {
            Logger.d("[STEP 2 - FRAME SKIPPED] Frame #$frameNum skipped (ML Kit is busy processing previous frame)")
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        isMlKitProcessing.set(true)

        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        val rotatedWidth = if (rotationDegrees == 90 || rotationDegrees == 270) mediaImage.height else mediaImage.width
        val rotatedHeight = if (rotationDegrees == 90 || rotationDegrees == 270) mediaImage.width else mediaImage.height

        // Calculate Viewfinder ROI rectangle (85% width, 35% height, centered)
        val roiWidth = (rotatedWidth * 0.85).toInt()
        val roiHeight = (rotatedHeight * 0.35).toInt()
        val roiLeft = (rotatedWidth - roiWidth) / 2
        val roiTop = (rotatedHeight - roiHeight) / 2
        val roiRight = roiLeft + roiWidth
        val roiBottom = roiTop + roiHeight
        val viewfinderRect = Rect(roiLeft, roiTop, roiRight, roiBottom)

        Logger.d("[STEP 2 - FRAME CAPTURED] Frame #$frameNum captured (${mediaImage.width}x${mediaImage.height}, rotation=$rotationDegrees°, ROI: ${viewfinderRect.toShortString()})")

        val inputImage = InputImage.fromMediaImage(mediaImage, rotationDegrees)
        Logger.d("[STEP 3 - MLKIT DISPATCH] Dispatching Frame #$frameNum to ML Kit Text Recognizer")

        textRecognizer.process(inputImage)
            .addOnSuccessListener { visionText ->
                val rawText = visionText.text
                if (rawText.isBlank()) {
                    Logger.d("[STEP 4 - OCR OUTPUT] Frame #$frameNum: ML Kit detected NO text")
                } else {
                    Logger.i("[STEP 4 - OCR OUTPUT] Frame #$frameNum: ML Kit raw output: \"${rawText.replace("\n", " | ")}\"")
                }

                if (!hasScanned && rawText.isNotBlank()) {
                    val candidate = vehiclePlateDecoder.extractPlateCandidateFromVisionText(visionText, viewfinderRect)
                    if (candidate != null) {
                        val verifiedPlate = verifyCandidate(candidate)
                        if (verifiedPlate != null) {
                            Logger.i("[STEP 7 - RESULT DISPATCH] Frame #$frameNum SUCCESS: Vehicle plate detected: \"$verifiedPlate\"")
                            if (!continueScan) {
                                hasScanned = true
                            }
                            onResult(BarcodeResult(text = verifiedPlate, format = BarcodeFormat.VEHICLE_PLATE))
                        }
                    } else {
                        // Reset pending low confidence candidate if frame yields no plate
                        lastCandidatePlate = null
                        candidateMatchCount = 0
                    }
                }
            }
            .addOnFailureListener { e ->
                Logger.e("[STEP 4 - OCR ERROR] Frame #$frameNum ML Kit failure: ${e.message}", e)
            }
            .addOnCompleteListener {
                isMlKitProcessing.set(false)
                imageProxy.close()
            }
    }

    private fun verifyCandidate(candidate: PlateCandidate): String? {
        if (candidate.isHighConfidence) {
            lastCandidatePlate = null
            candidateMatchCount = 0
            return candidate.plateNumber
        }

        if (candidate.plateNumber == lastCandidatePlate) {
            candidateMatchCount++
            if (candidateMatchCount >= 1) {
                Logger.i("[STEP 6 - VERIFIED] Low confidence candidate \"${candidate.plateNumber}\" confirmed across consecutive frames")
                lastCandidatePlate = null
                candidateMatchCount = 0
                return candidate.plateNumber
            }
        } else {
            lastCandidatePlate = candidate.plateNumber
            candidateMatchCount = 0
            Logger.d("[STEP 6 - PENDING] Low confidence candidate \"${candidate.plateNumber}\" pending consecutive frame confirmation")
        }

        return null
    }

    fun close() {
        try {
            textRecognizer.close()
        } catch (_: Exception) {}
    }
}
