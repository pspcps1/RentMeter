package com.example.engine

import com.example.data.model.MeterType
import java.util.Locale
import kotlin.math.abs

data class ParsedMeterReading(
    val rawText: String,
    val cleanedDigits: String,
    val value: Double?,
    val formattedValue: String,
    val isSuspicious: Boolean,
    val warningMessage: String?
)

/**
 * Pure Kotlin parser & sanity-checker for Digital 7-segment LCD and Analog Drum meter OCR outputs.
 * Handles common character confusions, analog red tenth-wheel decimal insertion, and serial-number traps.
 */
object MeterReadingParser {

    fun parse(
        rawOcrText: String,
        meterType: MeterType,
        analogHasRedDecimal: Boolean,
        previousReading: Double
    ): ParsedMeterReading {
        val candidates = extractDigitCandidates(rawOcrText)
        val bestCandidate = selectBestCandidate(candidates, previousReading, meterType, analogHasRedDecimal)

        if (bestCandidate == null) {
            return ParsedMeterReading(
                rawText = rawOcrText,
                cleanedDigits = "",
                value = null,
                formattedValue = "",
                isSuspicious = true,
                warningMessage = "Could not detect meter digits clearly. Tap to enter or adjust crop."
            )
        }

        val finalValue = applyMeterFormat(bestCandidate, meterType, analogHasRedDecimal)
        val validation = validateReading(finalValue, previousReading)

        val formatted = if (finalValue != null) {
            if (finalValue % 1.0 == 0.0 && !(meterType == MeterType.ANALOG_DRUM && analogHasRedDecimal)) {
                finalValue.toLong().toString()
            } else {
                String.format(Locale.US, "%.1f", finalValue)
            }
        } else ""

        return ParsedMeterReading(
            rawText = rawOcrText,
            cleanedDigits = bestCandidate,
            value = finalValue,
            formattedValue = formatted,
            isSuspicious = validation != null,
            warningMessage = validation
        )
    }

    internal fun normalizeOcrChars(token: String): String {
        val sb = StringBuilder()
        for (ch in token.trim()) {
            when (ch) {
                in '0'..'9' -> sb.append(ch)
                '.', ',' -> sb.append('.')
                'O', 'o', 'D', 'Q' -> sb.append('0')
                'I', 'l', '|', '!' -> sb.append('1')
                'Z', 'z' -> sb.append('2')
                'S', 's' -> sb.append('5')
                'G', 'b' -> sb.append('6')
                'B' -> sb.append('8')
                'g', 'q' -> sb.append('9')
            }
        }
        // Collapse multiple dots, keeping at most the last decimal point if sensible
        val raw = sb.toString()
        val dotCount = raw.count { it == '.' }
        return if (dotCount <= 1) {
            raw.trim('.')
        } else {
            val lastDot = raw.lastIndexOf('.')
            val before = raw.substring(0, lastDot).replace(".", "")
            val after = raw.substring(lastDot + 1).replace(".", "")
            "$before.$after".trim('.')
        }
    }

    internal fun extractDigitCandidates(rawOcrText: String): List<String> {
        if (rawOcrText.isBlank()) return emptyList()
        val results = mutableListOf<String>()
        // Split by lines first (meter display line vs stray text)
        val lines = rawOcrText.lines()
        for (line in lines) {
            // Try merging spaces inside a single line (e.g. "0 9 7 3 3 0" -> "097330")
            val compactLine = normalizeOcrChars(line.replace(" ", ""))
            if (compactLine.count { it.isDigit() } in 2..9) {
                results.add(compactLine)
            }
            // Also check individual whitespace-separated tokens
            for (token in line.split(Regex("\\s+"))) {
                val cleaned = normalizeOcrChars(token)
                if (cleaned.count { it.isDigit() } in 2..9) {
                    results.add(cleaned)
                }
            }
        }
        return results.distinct()
    }

    private fun selectBestCandidate(
        candidates: List<String>,
        previousReading: Double,
        meterType: MeterType,
        analogHasRedDecimal: Boolean
    ): String? {
        if (candidates.isEmpty()) return null
        // Score candidates: prefer 4-7 digits (standard meter dials), and prefer readings >= previousReading with reasonable delta
        return candidates.maxByOrNull { cand ->
            val digitCount = cand.count { it.isDigit() }
            var score = 0
            if (digitCount in 4..7) score += 50 else if (digitCount in 2..8) score += 20
            val parsed = applyMeterFormat(cand, meterType, analogHasRedDecimal)
            if (parsed != null) {
                score += 10
                if (previousReading > 0.0) {
                    val delta = parsed - previousReading
                    when {
                        delta in 0.0..2000.0 -> score += 100
                        delta > 2000.0 && delta <= 5000.0 -> score += 40
                        delta < 0.0 -> score -= 20
                        else -> score -= 50 // Likely serial number (e.g. 1834628 or 3999888)
                    }
                } else {
                    // Initial reading: avoid 8+ digit serial numbers
                    if (digitCount in 4..6) score += 30
                }
            }
            score - abs(digitCount - 6)
        }
    }

    internal fun applyMeterFormat(
        cleaned: String,
        meterType: MeterType,
        analogHasRedDecimal: Boolean
    ): Double? {
        if (cleaned.isBlank()) return null
        if (meterType == MeterType.ANALOG_DRUM && analogHasRedDecimal) {
            if (!cleaned.contains('.') && cleaned.length >= 2) {
                val whole = cleaned.dropLast(1)
                val tenth = cleaned.last()
                return "$whole.$tenth".toDoubleOrNull()
            }
        }
        return cleaned.toDoubleOrNull()
    }

    fun validateReading(current: Double?, previous: Double): String? {
        if (current == null) {
            return "Unable to parse a valid reading. Please enter manually."
        }
        if (previous > 0.0 && current < previous) {
            return "Detected reading (${formatShort(current)}) is lower than previous (${formatShort(previous)}). Tap to verify."
        }
        if (previous > 0.0 && (current - previous) > 5000.0) {
            return "Unusually high jump (+${formatShort(current - previous)} units). Check for serial number or decimal wheel."
        }
        if (previous == 0.0 && current > 999999.0) {
            return "Reading looks like a serial number (> 6 digits). Please verify."
        }
        return null
    }

    private fun formatShort(v: Double): String =
        if (v % 1.0 == 0.0) v.toLong().toString() else String.format(Locale.US, "%.1f", v)
}
