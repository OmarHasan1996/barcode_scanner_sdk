package com.enoc.sdk.scanner.core.decoder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class VehiclePlateDecoderTest {

    private lateinit var decoder: VehiclePlateDecoder

    @Before
    fun setUp() {
        decoder = VehiclePlateDecoder()
    }

    @Test
    fun extractPlateNumber_emptyText_returnsNull() {
        assertNull(decoder.extractPlateNumber(""))
        assertNull(decoder.extractPlateNumber("   "))
    }

    @Test
    fun extractPlateNumber_blankBackgroundOrNoiseText_returnsNull() {
        assertNull(decoder.extractPlateNumber("PHONE NUMBER 0501234567"))
        assertNull(decoder.extractPlateNumber("DATE 2026-09-23 TIME 08:05:34"))
        assertNull(decoder.extractPlateNumber("LATITUDE 25.2048 LONGITUDE 55.2708"))
        assertNull(decoder.extractPlateNumber("DEBUG SCANNER NOISE"))
        assertNull(decoder.extractPlateNumber("die MOpu So01 | Bodfaaqunu ajed"))
        assertNull(decoder.extractPlateNumber("I\n1"))
        assertNull(decoder.extractPlateNumber("O 1"))
    }

    @Test
    fun extractPlateNumber_standalone5Digits_returnsDigits() {
        val result = decoder.extractPlateNumber("80473")
        assertEquals("80473", result)
    }

    @Test
    fun extractPlateNumber_dubaiPlateD87550_returnsD87550() {
        val result1 = decoder.extractPlateNumber("D 87550")
        assertEquals("D-87550", result1)

        val rawOcr = """
            D 87550
            DUBAI دبي
        """.trimIndent()
        val result2 = decoder.extractPlateNumber(rawOcr)
        assertEquals("D-87550", result2)
    }

    @Test
    fun extractPlateNumber_codeAndDigits_returnsFormattedPlate() {
        val result1 = decoder.extractPlateNumber("DUBAI A 80473")
        assertEquals("A-80473", result1)

        val result2 = decoder.extractPlateNumber("DXB B 1234")
        assertEquals("B-1234", result2)

        val result3 = decoder.extractPlateNumber("Code K 92802")
        assertEquals("K-92802", result3)
    }

    @Test
    fun extractPlateNumber_multilineTextWithPlate_extractsPlate() {
        val rawOcr = """
            UNITED ARAB EMIRATES
            DUBAI
            A 80473
        """.trimIndent()

        val result = decoder.extractPlateNumber(rawOcr)
        assertEquals("A-80473", result)
    }
}
