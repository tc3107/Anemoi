package com.example.anemoi.util

import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import kotlin.math.abs

data class ParsedCoordinate(
    val lat: Double,
    val lon: Double
)

object CoordinateInputParser {
    private const val numberPattern = "[+-]?\\d{1,3}(?:\\.\\d+)?"

    private val latParamRegex = Regex("(?i)(?:^|[?&\\s])lat(?:itude)?\\s*=\\s*($numberPattern)")
    private val lonParamRegex = Regex("(?i)(?:^|[?&\\s])(?:lon|lng|longitude)\\s*=\\s*($numberPattern)")
    private val decimalHemisphereRegex = Regex("(?i)($numberPattern)\\s*([NSEW])")
    private val dmsRegex = Regex(
        "(?i)(\\d{1,3})\\D+(\\d{1,2})\\D+(\\d{1,2}(?:\\.\\d+)?)\\D*([NSEW])"
    )
    private val decimalPairRegex = Regex("($numberPattern)[,\\s]+($numberPattern)")

    fun parse(input: String): ParsedCoordinate? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null

        val decoded = runCatching {
            URLDecoder.decode(trimmed, StandardCharsets.UTF_8.name())
        }.getOrDefault(trimmed)

        return parseQueryParams(decoded)
            ?: parseDms(decoded)
            ?: parseDecimalHemisphere(decoded)
            ?: parseDecimalPair(decoded)
    }

    private fun parseQueryParams(input: String): ParsedCoordinate? {
        val lat = latParamRegex.find(input)?.groupValues?.getOrNull(1)?.toDoubleOrNull() ?: return null
        val lon = lonParamRegex.find(input)?.groupValues?.getOrNull(1)?.toDoubleOrNull() ?: return null
        return normalizeAndValidate(lat, lon)
    }

    private fun parseDms(input: String): ParsedCoordinate? {
        val matches = dmsRegex.findAll(input).toList()
        if (matches.size < 2) return null

        var lat: Double? = null
        var lon: Double? = null
        for (match in matches) {
            val degrees = match.groupValues[1].toDoubleOrNull() ?: continue
            val minutes = match.groupValues[2].toDoubleOrNull() ?: continue
            val seconds = match.groupValues[3].toDoubleOrNull() ?: continue
            if (minutes !in 0.0..59.9999 || seconds !in 0.0..59.9999) continue

            val hemisphere = match.groupValues[4].uppercase(Locale.US)
            val decimal = degrees + (minutes / 60.0) + (seconds / 3600.0)
            when (hemisphere) {
                "N" -> lat = decimal
                "S" -> lat = -decimal
                "E" -> lon = decimal
                "W" -> lon = -decimal
            }
        }

        return if (lat != null && lon != null) normalizeAndValidate(lat, lon) else null
    }

    private fun parseDecimalHemisphere(input: String): ParsedCoordinate? {
        // Prevent false parsing of DMS seconds as decimal hemisphere values.
        if (input.any { it == '°' || it == 'º' || it == '\'' || it == '"' || it == '′' || it == '″' }) {
            return null
        }

        val matches = decimalHemisphereRegex.findAll(input).toList()
        if (matches.size < 2) return null

        var lat: Double? = null
        var lon: Double? = null
        for (match in matches) {
            val value = match.groupValues[1].toDoubleOrNull() ?: continue
            val hemisphere = match.groupValues[2].uppercase(Locale.US)
            when (hemisphere) {
                "N" -> lat = abs(value)
                "S" -> lat = -abs(value)
                "E" -> lon = abs(value)
                "W" -> lon = -abs(value)
            }
        }

        return if (lat != null && lon != null) normalizeAndValidate(lat, lon) else null
    }

    private fun parseDecimalPair(input: String): ParsedCoordinate? {
        val match = decimalPairRegex.find(input) ?: return null
        val first = match.groupValues[1].toDoubleOrNull() ?: return null
        val second = match.groupValues[2].toDoubleOrNull() ?: return null
        return normalizeAndValidate(first, second)
    }

    private fun normalizeAndValidate(first: Double, second: Double): ParsedCoordinate? {
        var lat = first
        var lon = second
        if (abs(lat) > 90.0 && abs(lon) <= 90.0) {
            val temp = lat
            lat = lon
            lon = temp
        }

        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
        return ParsedCoordinate(lat = lat, lon = lon)
    }
}
