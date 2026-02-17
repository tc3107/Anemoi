package com.tudorc.anemoi

import com.tudorc.anemoi.util.ObfuscationMode
import com.tudorc.anemoi.util.obfuscateLocation
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class LocationUtilsTest {

    @Test
    fun `mode=PRECISE returns exact input`() {
        val lat = 45.0
        val lon = 90.0
        val result = obfuscateLocation(lat, lon, ObfuscationMode.PRECISE, 10.0)
        
        assertEquals(lat, result.latObf, 0.0)
        assertEquals(lon, result.lonObf, 0.0)
        assertEquals(ObfuscationMode.PRECISE, result.metadata.mode)
    }

    @Test
    fun `same input, grid size, and seed returns same output`() {
        val lat = 45.0
        val lon = 90.0
        val gridKm = 5.0
        val seed = 12345L
        
        val res1 = obfuscateLocation(lat, lon, ObfuscationMode.GRID, gridKm, seed)
        val res2 = obfuscateLocation(lat, lon, ObfuscationMode.GRID, gridKm, seed)
        
        assertEquals(res1.latObf, res2.latObf, 0.0)
        assertEquals(res1.lonObf, res2.lonObf, 0.0)
        assertEquals(res1.metadata.cellId, res2.metadata.cellId)
    }

    @Test
    fun `obfuscated output falls within cell bounds`() {
        val lat = 45.0
        val lon = 90.0
        val gridKm = 10.0
        val result = obfuscateLocation(lat, lon, ObfuscationMode.GRID, gridKm, seed = 1L)
        
        // Approximate degrees per km
        val dLat = gridKm * (1.0 / 110.574)
        
        // Check if it's within 1.5x the cell size (since we snap to center + jitter)
        // More precisely, the distance from original should be roughly within grid size
        assertTrue("Lat should be within range", abs(result.latObf - lat) < dLat)
    }

    @Test
    fun `increasing grid_km increases expected error magnitude`() {
        val lat = 45.0
        val lon = 90.0
        
        val resSmall = obfuscateLocation(lat, lon, ObfuscationMode.GRID, 1.0, seed = 1L)
        val resLarge = obfuscateLocation(lat, lon, ObfuscationMode.GRID, 50.0, seed = 1L)
        
        val errorSmall = abs(resSmall.latObf - lat) + abs(resSmall.lonObf - lon)
        val errorLarge = abs(resLarge.latObf - lat) + abs(resLarge.lonObf - lon)
        
        assertTrue("Larger grid should generally have larger error", errorLarge > errorSmall)
    }

    @Test
    fun `different cells yield different cell_id`() {
        val gridKm = 10.0
        val res1 = obfuscateLocation(45.0, 90.0, ObfuscationMode.GRID, gridKm)
        val res2 = obfuscateLocation(45.1, 90.1, ObfuscationMode.GRID, gridKm)
        
        // Depending on where the grid lines fall, 0.1 degree (approx 11km) might be different cells
        // Let's use a larger gap to be sure
        val res3 = obfuscateLocation(46.0, 91.0, ObfuscationMode.GRID, gridKm)
        
        assertNotEquals(res1.metadata.cellId, res3.metadata.cellId)
    }
}
