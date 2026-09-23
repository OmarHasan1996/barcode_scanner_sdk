package com.enoc.sdk.scanner.core.decoder

import android.graphics.Rect
import com.enoc.sdk.scanner.core.core.RowRuns
import com.enoc.sdk.scanner.core.model.BarcodeResult
import com.enoc.sdk.scanner.core.utils.Logger
import com.google.mlkit.vision.text.Text

data class PlateCandidate(
    val plateNumber: String,
    val isHighConfidence: Boolean
)

/**
 * Decodes high-accuracy Dubai/UAE vehicle plates by precisely matching
 * OCR text output from ML Kit vision analysis to valid plate number sequences.
 */
class VehiclePlateDecoder : Decoder {

    companion object {
        // Common non-plate text and noise words to exclude from OCR matches
        private val NOISE_KEYWORDS = setOf(
            "PHONE", "TEL", "DATE", "TIME", "PRICE", "TOTAL", "WWW", "HTTP", "COM",
            "SCANNER", "DEBUG", "LATITUDE", "LONGITUDE", "SPEED", "KM/H", "MPH",
            "DIE", "MOPU", "BODFAAQUUNU", "FILE", "EDIT", "VIEW", "TOOLS", "WINDOW",
            "HELP", "PREVIEW", "SEARCH", "CAMERAX", "LIFECYCLE", "AGENT", "HEAD", "CHANGES",
            "OBJECT", "JOHABI", "UDHABI", "DUBAL", "DOBABI", "OHABI"
        )

        // Valid Dubai / UAE plate prefixes
        private val VALID_PREFIXES = setOf("DUBAI", "DXB", "UAE", "SHARJAH", "SHJ", "ABUDHABI", "AD", "Abu Dhabi", "AJMAN", "AJ", "RAK", "FUJAIRAH", "FUJ", "UAQ")
        
        // Single letter codes that are prone to OCR cursor/border noise when paired with a 1-digit number
        private val NOISE_SINGLE_LETTER_CODES = setOf("I", "O", "Q", "L", "T")
    }

    override fun decode(rowRuns: RowRuns): BarcodeResult? {
        // 1D row run scanning is not used for 2D vehicle license plates.
        return null
    }

    /**
     * Extracts and validates vehicle plate candidate from ML Kit's structured Text object,
     * filtering out text lines located outside the central Viewfinder ROI box.
     */
    fun extractPlateCandidateFromVisionText(visionText: Text, roiRect: Rect? = null): PlateCandidate? {
        val validLines = mutableListOf<String>()

        for (block in visionText.textBlocks) {
            val blockTextUpper = block.text.uppercase()
            val blockHasCityPrefix = VALID_PREFIXES.any { blockTextUpper.contains(it) }

            for (line in block.lines) {
                val box = line.boundingBox
                // Filter out lines whose center is outside the viewfinder ROI box
                if (roiRect != null && box != null) {
                    if (!roiRect.contains(box.centerX(), box.centerY())) {
                        Logger.d("[STEP 5 - OUTSIDE ROI] Filtered out text outside viewfinder: \"${line.text.trim()}\"")
                        continue
                    }
                }

                val lineText = line.text.trim()
                val candidate = parseLineForCandidate(lineText)
                if (candidate != null) return candidate

                val elements = line.elements
                    .filter { elem ->
                        val elemBox = elem.boundingBox
                        roiRect == null || elemBox == null || roiRect.contains(elemBox.centerX(), elemBox.centerY())
                    }
                    .map { it.text.trim() }
                    .filter { it.isNotEmpty() }

                val elementMatch = parseElementsForCandidate(elements, blockHasCityPrefix)
                if (elementMatch != null) return elementMatch

                validLines.add(lineText)
            }
        }

        if (validLines.isNotEmpty()) {
            return extractPlateCandidate(validLines.joinToString("\n"))
        }

        return null
    }

    /**
     * Extracts plate string representation for compatibility or full raw text parsing.
     */
    fun extractPlateNumber(rawText: String): String? {
        return extractPlateCandidate(rawText)?.plateNumber
    }

    /**
     * Parses raw OCR text detected by ML Kit and extracts valid license plate candidate.
     */
    fun extractPlateCandidate(rawText: String): PlateCandidate? {
        if (rawText.isBlank()) {
            Logger.d("[STEP 5 - REJECT] Raw OCR text is blank")
            return null
        }

        val lines = rawText.split("\n", "\r").asSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        Logger.d("[STEP 5 - EVAL] Evaluating OCR raw text with ${lines.size} line(s): \"${rawText.replace("\n", " | ")}\"")

        val hasCityPrefix = VALID_PREFIXES.any { rawText.uppercase().contains(it) }

        // Phase 1: Try line-by-line matching
        for (line in lines) {
            val candidate = parseLineForCandidate(line)
            if (candidate != null) return candidate
        }

        // Phase 2: Try line element aggregation
        val tokens = lines.flatMap { line ->
            line.split(Regex("""[\s/\-]+""")).map { it.trim() }.filter { it.isNotEmpty() }
        }

        val multiLineMatch = parseElementsForCandidate(tokens, hasCityPrefix)
        if (multiLineMatch != null) return multiLineMatch

        Logger.d("[STEP 5 - NO MATCH] No valid vehicle plate sequence found in raw text")
        return null
    }

    private fun parseLineForCandidate(line: String): PlateCandidate? {
        val upperLine = line.uppercase()
        if (NOISE_KEYWORDS.any { upperLine.contains(it) }) {
            Logger.d("[STEP 5 - NOISE SKIPPED] Line contains noise keyword: \"$line\"")
            return null
        }

        val hasCityPrefix = VALID_PREFIXES.any { upperLine.contains(it) }

        // 1. Match explicit Code + Number on same line (e.g. "D 87550", "DUBAI D 87550", "DXB A 1234", "D-87550")
        // High confidence: Requires uppercase 1 or 2 letter code + 1-5 digits
        val codeNumberRegex = Regex("""(?:DUBAI|DXB|UAE|SHARJAH|ABUDHABI|AD|AJMAN|AJ|RAK|FUJAIRAH|FUJ|UAQ)?\s*\b([A-Z]{1,2})\b\s*[-/\s]?\s*\b(\d{1,5})\b""", RegexOption.IGNORE_CASE)
        val codeMatch = codeNumberRegex.find(line)
        if (codeMatch != null) {
            val code = codeMatch.groupValues[1].uppercase()
            val number = codeMatch.groupValues[2]
            if ((code !in NOISE_KEYWORDS) && (code !in VALID_PREFIXES) && (number.toIntOrNull() != null)) {
                // Reject cursor noise combinations like "I-1" unless city prefix is explicitly present
                if (code in NOISE_SINGLE_LETTER_CODES && number.length == 1 && !hasCityPrefix) {
                    Logger.d("[STEP 5 - NOISE REJECT] Rejected single-letter noise combination: \"$code-$number\"")
                } else {
                    val plate = "$code-$number"
                    Logger.i("[STEP 6 - PLATE DETECTED] Matched Code+Number pattern (High Confidence): \"$plate\" (line: \"$line\")")
                    return PlateCandidate(plateNumber = plate, isHighConfidence = true)
                }
            }
        }

        // 2. Match standalone 3 to 5 digit plate number sequence (e.g., "87550", "92802", "12345")
        // Require at least 3 digits for standalone numbers to avoid matching random 1-2 digit numbers in environment
        val digitsRegex = Regex("""\b(\d{3,5})\b""")
        val digitMatches = digitsRegex.findAll(line).toList()
        for (match in digitMatches) {
            val numStr = match.groupValues[1]
            val isStandalone = (line.length <= 15) || (!line.contains(Regex("""\d{6,}""")))
            if (isStandalone && numStr.toIntOrNull() != null) {
                Logger.i("[STEP 6 - PLATE DETECTED] Matched standalone sequence (Low Confidence): \"$numStr\" (line: \"$line\")")
                return PlateCandidate(plateNumber = numStr, isHighConfidence = false)
            }
        }

        return null
    }

    private fun parseElementsForCandidate(elements: List<String>, hasCityPrefix: Boolean = false): PlateCandidate? {
        var codeToken: String? = null
        var numberToken: String? = null

        for (token in elements) {
            val clean = token.uppercase().trim()
            if (clean in NOISE_KEYWORDS || clean in VALID_PREFIXES) continue

            // Check if token is a 1-2 letter code (e.g. "D", "A", "AA")
            if (codeToken == null && clean.matches(Regex("""^[A-Z]{1,2}$"""))) {
                codeToken = clean
            }
            // Check if token is digits (1-5 digits if code is present, or 3-5 digits if standalone)
            else if (numberToken == null) {
                if (codeToken != null && clean.matches(Regex("""^\d{1,5}$"""))) {
                    numberToken = clean
                } else if (codeToken == null && clean.matches(Regex("""^\d{3,5}$"""))) {
                    numberToken = clean
                }
            }

            if (codeToken != null && numberToken != null) {
                // Reject cursor noise combinations like "I-1" or "O-1" unless city prefix is present
                if (codeToken in NOISE_SINGLE_LETTER_CODES && numberToken.length == 1 && !hasCityPrefix) {
                    Logger.d("[STEP 5 - NOISE REJECT] Rejected single-letter noise combination: \"$codeToken-$numberToken\"")
                    codeToken = null
                    numberToken = null
                    continue
                }

                val plate = "$codeToken-$numberToken"
                Logger.i("[STEP 6 - PLATE DETECTED] Matched combined tokens (High Confidence): \"$plate\"")
                return PlateCandidate(plateNumber = plate, isHighConfidence = true)
            }
        }

        return null
    }
}
