package com.tudorc.anemoi.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherRequestPolicyTest {
    private val thresholds = WeatherFreshnessThresholds(
        currentMs = 5 * 60 * 1000L,
        hourlyMs = 20 * 60 * 1000L,
        dailyMs = 2 * 60 * 60 * 1000L
    )

    private val gateConfig = RequestGateConfig(
        locationMinRequestIntervalMs = 60 * 1000L,
        globalRequestWindowMs = 60 * 1000L,
        globalRequestLimitPerWindow = 30
    )

    @Test
    fun resolveDatasets_missingData_isIncluded() {
        val now = 1_000_000L
        val result = WeatherRequestPolicy.resolveDatasetsToRefresh(
            DatasetRefreshInput(
                force = false,
                hasCurrent = false,
                hasHourly = true,
                hasDaily = true,
                currentUpdatedAtMs = now,
                hourlyUpdatedAtMs = now,
                dailyUpdatedAtMs = now,
                nowMs = now,
                thresholds = thresholds
            )
        )

        assertEquals(setOf(WeatherDataset.CURRENT), result)
    }

    @Test
    fun resolveDatasets_justUnderThreshold_isExcluded() {
        val now = 10_000_000L
        val result = WeatherRequestPolicy.resolveDatasetsToRefresh(
            DatasetRefreshInput(
                force = false,
                hasCurrent = true,
                hasHourly = true,
                hasDaily = true,
                currentUpdatedAtMs = now - thresholds.currentMs + 1,
                hourlyUpdatedAtMs = now - thresholds.hourlyMs + 1,
                dailyUpdatedAtMs = now - thresholds.dailyMs + 1,
                nowMs = now,
                thresholds = thresholds
            )
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun resolveDatasets_justOverThreshold_isIncluded() {
        val now = 12_000_000L
        val result = WeatherRequestPolicy.resolveDatasetsToRefresh(
            DatasetRefreshInput(
                force = false,
                hasCurrent = true,
                hasHourly = true,
                hasDaily = true,
                currentUpdatedAtMs = now - thresholds.currentMs - 1,
                hourlyUpdatedAtMs = now - thresholds.hourlyMs + 1,
                dailyUpdatedAtMs = now - thresholds.dailyMs + 1,
                nowMs = now,
                thresholds = thresholds
            )
        )

        assertEquals(setOf(WeatherDataset.CURRENT), result)
    }

    @Test
    fun resolveDatasets_mixedStates_returnsExpectedSet() {
        val now = 4_000_000L
        val result = WeatherRequestPolicy.resolveDatasetsToRefresh(
            DatasetRefreshInput(
                force = false,
                hasCurrent = false,
                hasHourly = true,
                hasDaily = true,
                currentUpdatedAtMs = 0L,
                hourlyUpdatedAtMs = now - thresholds.hourlyMs + 10,
                dailyUpdatedAtMs = now - thresholds.dailyMs - 10,
                nowMs = now,
                thresholds = thresholds
            )
        )

        assertEquals(setOf(WeatherDataset.CURRENT, WeatherDataset.DAILY), result)
    }

    @Test
    fun rateLimit_sameLocationWithinWindow_blocksSecondRequest() {
        val now = 1_000_000L
        val decision = WeatherRequestPolicy.evaluateRequestGate(
            nowMs = now,
            snapshot = RequestGateSnapshot(
                backoffUntilMs = 0L,
                locationLastRequestAtMs = now - 30_000L,
                globalRequestCount = 1,
                globalOldestRequestAtMs = now - 30_000L
            ),
            config = gateConfig
        )

        assertFalse(decision.isAllowed)
        assertTrue(decision.reasons.contains("per-location limit"))
    }

    @Test
    fun rateLimit_globalCap_blocksWhenThirtyInWindow() {
        val now = 2_000_000L
        val decision = WeatherRequestPolicy.evaluateRequestGate(
            nowMs = now,
            snapshot = RequestGateSnapshot(
                backoffUntilMs = 0L,
                locationLastRequestAtMs = null,
                globalRequestCount = 30,
                globalOldestRequestAtMs = now - 20_000L
            ),
            config = gateConfig
        )

        assertFalse(decision.isAllowed)
        assertTrue(decision.reasons.contains("global limit"))
    }

    @Test
    fun rateLimit_multipleGates_chooseLongestNextAllowedTime() {
        val now = 2_500_000L
        val decision = WeatherRequestPolicy.evaluateRequestGate(
            nowMs = now,
            snapshot = RequestGateSnapshot(
                backoffUntilMs = now + 10_000L,
                locationLastRequestAtMs = now - 30_000L,
                globalRequestCount = 30,
                globalOldestRequestAtMs = now - 55_000L
            ),
            config = gateConfig
        )

        assertFalse(decision.isAllowed)
        assertTrue(decision.reasons.contains("backoff"))
        assertTrue(decision.reasons.contains("per-location limit"))
        assertTrue(decision.reasons.contains("global limit"))
        assertEquals(now + 30_000L, decision.nextAllowedAtMs)
    }

    @Test
    fun rateLimit_pruningKeepsWindowBounded() {
        val now = 3_000_000L
        val timestamps = listOf(
            now - 90_000L,
            now - 59_000L,
            now - 1_000L
        )

        val pruned = WeatherRequestPolicy.pruneTimestampsWithinWindow(
            timestamps = timestamps,
            nowMs = now,
            windowMs = 60_000L
        )

        assertEquals(listOf(now - 59_000L, now - 1_000L), pruned)
    }

    @Test
    fun backoff_progressionFollowsExpectedSteps() {
        val steps = longArrayOf(5_000L, 15_000L, 60_000L, 300_000L)

        assertEquals(5_000L, WeatherRequestPolicy.backoffDelayForFailure(1, steps))
        assertEquals(15_000L, WeatherRequestPolicy.backoffDelayForFailure(2, steps))
        assertEquals(60_000L, WeatherRequestPolicy.backoffDelayForFailure(3, steps))
        assertEquals(300_000L, WeatherRequestPolicy.backoffDelayForFailure(4, steps))
        assertEquals(300_000L, WeatherRequestPolicy.backoffDelayForFailure(5, steps))
    }

    @Test
    fun backoff_successReset_restartsFromFirstStep() {
        val steps = longArrayOf(5_000L, 15_000L, 60_000L, 300_000L)
        var failureCount = 0

        failureCount += 1
        WeatherRequestPolicy.backoffDelayForFailure(failureCount, steps)
        failureCount += 1
        WeatherRequestPolicy.backoffDelayForFailure(failureCount, steps)

        failureCount = 0 // reset after success
        failureCount += 1

        assertEquals(5_000L, WeatherRequestPolicy.backoffDelayForFailure(failureCount, steps))
    }

    @Test
    fun backoff_gateBlocksUntilRetryTime() {
        val now = 4_000_000L
        val decision = WeatherRequestPolicy.evaluateRequestGate(
            nowMs = now,
            snapshot = RequestGateSnapshot(
                backoffUntilMs = now + 15_000L,
                locationLastRequestAtMs = null,
                globalRequestCount = 0,
                globalOldestRequestAtMs = null
            ),
            config = gateConfig
        )

        assertFalse(decision.isAllowed)
        assertTrue(decision.reasons.contains("backoff"))
        assertEquals(now + 15_000L, decision.nextAllowedAtMs)
    }

    @Test
    fun backoff_isIndependentPerLocation() {
        val now = 5_000_000L

        val blocked = WeatherRequestPolicy.evaluateRequestGate(
            nowMs = now,
            snapshot = RequestGateSnapshot(
                backoffUntilMs = now + 60_000L,
                locationLastRequestAtMs = null,
                globalRequestCount = 0,
                globalOldestRequestAtMs = null
            ),
            config = gateConfig
        )

        val allowed = WeatherRequestPolicy.evaluateRequestGate(
            nowMs = now,
            snapshot = RequestGateSnapshot(
                backoffUntilMs = 0L,
                locationLastRequestAtMs = null,
                globalRequestCount = 0,
                globalOldestRequestAtMs = null
            ),
            config = gateConfig
        )

        assertFalse(blocked.isAllowed)
        assertTrue(allowed.isAllowed)
    }
}
