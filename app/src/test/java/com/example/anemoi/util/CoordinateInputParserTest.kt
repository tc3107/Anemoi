package com.example.anemoi.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CoordinateInputParserTest {

    @Test
    fun parse_decimalCommaSeparated() {
        val result = CoordinateInputParser.parse("60.39299, 5.32415")
        assertNotNull(result)
        assertEquals(60.39299, result!!.lat, 1e-6)
        assertEquals(5.32415, result.lon, 1e-6)
    }

    @Test
    fun parse_decimalSpaceSeparated() {
        val result = CoordinateInputParser.parse("-33.8688 151.2093")
        assertNotNull(result)
        assertEquals(-33.8688, result!!.lat, 1e-6)
        assertEquals(151.2093, result.lon, 1e-6)
    }

    @Test
    fun parse_decimalWithHemisphereLetters() {
        val result = CoordinateInputParser.parse("33.8688 S 151.2093 E")
        assertNotNull(result)
        assertEquals(-33.8688, result!!.lat, 1e-6)
        assertEquals(151.2093, result.lon, 1e-6)
    }

    @Test
    fun parse_dmsFormat() {
        val result = CoordinateInputParser.parse("60°23'34\"N 5°19'26\"E")
        assertNotNull(result)
        assertEquals(60.392777, result!!.lat, 1e-5)
        assertEquals(5.323888, result.lon, 1e-5)
    }

    @Test
    fun parse_googleMapsQUrl() {
        val result = CoordinateInputParser.parse("https://maps.google.com/?q=60.39299,5.32415")
        assertNotNull(result)
        assertEquals(60.39299, result!!.lat, 1e-6)
        assertEquals(5.32415, result.lon, 1e-6)
    }

    @Test
    fun parse_googleMapsAtUrl() {
        val result = CoordinateInputParser.parse("https://www.google.com/maps/@60.39299,5.32415,12z")
        assertNotNull(result)
        assertEquals(60.39299, result!!.lat, 1e-6)
        assertEquals(5.32415, result.lon, 1e-6)
    }

    @Test
    fun parse_queryStringLatLon() {
        val result = CoordinateInputParser.parse("lat=60.39299&lon=5.32415")
        assertNotNull(result)
        assertEquals(60.39299, result!!.lat, 1e-6)
        assertEquals(5.32415, result.lon, 1e-6)
    }

    @Test
    fun parse_swapsWhenFirstLooksLikeLongitude() {
        val result = CoordinateInputParser.parse("151.2093, -33.8688")
        assertNotNull(result)
        assertEquals(-33.8688, result!!.lat, 1e-6)
        assertEquals(151.2093, result.lon, 1e-6)
    }

    @Test
    fun parse_invalidRangeReturnsNull() {
        assertNull(CoordinateInputParser.parse("95.0, 200.0"))
    }
}
