package com.tudorc.anemoi.util

import kotlin.math.*
import java.util.Random

enum class ObfuscationMode {
    PRECISE, GRID
}

data class ObfuscatedLocation(
    val latObf: Double,
    val lonObf: Double,
    val metadata: ObfuscationMetadata
)

data class ObfuscationMetadata(
    val mode: ObfuscationMode,
    val gridKm: Double,
    val cellId: String?,
    val snappedLat: Double?,
    val snappedLon: Double?,
    val jitterApplied: Boolean
)

/**
 * Obfuscates a location by snapping it to a grid and optionally adding jitter.
 *
 * Privacy Trade-offs:
 * - Grid snapping reduces precision, making it harder to pinpoint the exact location.
 * - Larger grid sizes provide better privacy but may result in less accurate weather data.
 * - Jitter helps prevent linkability by making multiple requests from the same grid cell
 *   look slightly different, though still within the same cell.
 *
 * Note: The conversion from km to degrees is approximate and varies with latitude.
 */
fun obfuscateLocation(
    lat: Double,
    lon: Double,
    mode: ObfuscationMode,
    gridKm: Double,
    seed: Long? = null,
    jitter: Boolean = true,
    jitterFraction: Double = 0.25
): ObfuscatedLocation {
    if (mode == ObfuscationMode.PRECISE) {
        return ObfuscatedLocation(
            lat,
            lon,
            ObfuscationMetadata(mode, gridKm, null, null, null, false)
        )
    }

    // 1) Convert desired grid size (km) into degree steps
    val degreesPerKmLat = 1.0 / 110.574
    val deltaLatDeg = gridKm * degreesPerKmLat

    val latRad = lat * PI / 180.0
    val cosLat = max(abs(cos(latRad)), 0.1)
    val degreesPerKmLon = 1.0 / (111.320 * cosLat)
    val deltaLonDeg = gridKm * degreesPerKmLon

    // Normalize longitude to [-180, 180)
    var normalizedLon = lon
    while (normalizedLon < -180.0) normalizedLon += 360.0
    while (normalizedLon >= 180.0) normalizedLon -= 360.0

    // 2) Snap lat/lon to a grid cell
    val originLat = -90.0
    val originLon = -180.0

    val i = floor((lat - originLat) / deltaLatDeg).toLong()
    val j = floor((normalizedLon - originLon) / deltaLonDeg).toLong()

    val latMin = originLat + i * deltaLatDeg
    val latMax = latMin + deltaLatDeg
    val lonMin = originLon + j * deltaLonDeg
    val lonMax = lonMin + deltaLonDeg

    // 3) Choose representative point (center)
    val latCenter = (latMin + latMax) / 2.0
    val lonCenter = (lonMin + lonMax) / 2.0

    // 4) Add jitter INSIDE cell
    var latObf = latCenter
    var lonObf = lonCenter
    var jitterApplied = false

    if (jitter) {
        val random = if (seed != null) Random(seed) else Random()
        
        val jitterLat = (random.nextDouble() * 2.0 - 1.0) * jitterFraction * deltaLatDeg
        val jitterLon = (random.nextDouble() * 2.0 - 1.0) * jitterFraction * deltaLonDeg
        
        val epsilon = 1e-9
        latObf = (latCenter + jitterLat).coerceIn(latMin + epsilon, latMax - epsilon)
        lonObf = (lonCenter + jitterLon).coerceIn(lonMin + epsilon, lonMax - epsilon)
        jitterApplied = true
    }

    // Round to 6 decimal places to avoid returning original precision
    latObf = round(latObf * 1e6) / 1e6
    lonObf = round(lonObf * 1e6) / 1e6

    val cellId = "grid:${gridKm}:$i:$j"

    return ObfuscatedLocation(
        latObf,
        lonObf,
        ObfuscationMetadata(
            mode = mode,
            gridKm = gridKm,
            cellId = cellId,
            snappedLat = round(latCenter * 1e6) / 1e6,
            snappedLon = round(lonCenter * 1e6) / 1e6,
            jitterApplied = jitterApplied
        )
    )
}
