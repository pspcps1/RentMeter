package com.example

import com.example.data.model.MeterType
import com.example.engine.MeterReadingParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MeterReadingParserTest {

    @Test
    fun analogDrumWithRedDecimal_insertsDecimalBeforeLastDigit() {
        val result = MeterReadingParser.parse(
            rawOcrText = "097330",
            meterType = MeterType.ANALOG_DRUM,
            analogHasRedDecimal = true,
            previousReading = 9650.0
        )
        assertEquals(9733.0, result.value!!, 0.01)
        assertEquals("9733.0", result.formattedValue)
        assertFalse(result.isSuspicious)
    }

    @Test
    fun analogDrumWithRedDecimal_doesNotDoubleDotIfDecimalAlreadyPresent() {
        val result = MeterReadingParser.parse(
            rawOcrText = "09733.0",
            meterType = MeterType.ANALOG_DRUM,
            analogHasRedDecimal = true,
            previousReading = 9650.0
        )
        assertEquals(9733.0, result.value!!, 0.01)
        assertFalse(result.isSuspicious)
    }

    @Test
    fun digitalLcd_selectsReadingOverSerialNumberTrap() {
        val result = MeterReadingParser.parse(
            rawOcrText = "BT3999888\n004250",
            meterType = MeterType.DIGITAL_LCD,
            analogHasRedDecimal = false,
            previousReading = 4110.0
        )
        assertEquals(4250.0, result.value!!, 0.01)
        assertFalse(result.isSuspicious)
    }

    @Test
    fun sanityCheck_flagsReadingLowerThanPrevious() {
        val result = MeterReadingParser.parse(
            rawOcrText = "003900",
            meterType = MeterType.DIGITAL_LCD,
            analogHasRedDecimal = false,
            previousReading = 4110.0
        )
        assertTrue(result.isSuspicious)
        assertTrue(result.warningMessage!!.contains("lower than previous"))
    }
}
