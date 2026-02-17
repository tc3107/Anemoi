package com.tudorc.anemoi.viewmodel

internal enum class WeatherDataset {
    CURRENT,
    HOURLY,
    DAILY
}

internal data class WeatherFreshnessThresholds(
    val currentMs: Long,
    val hourlyMs: Long,
    val dailyMs: Long
)

internal data class DatasetRefreshInput(
    val force: Boolean,
    val hasCurrent: Boolean,
    val hasHourly: Boolean,
    val hasDaily: Boolean,
    val currentUpdatedAtMs: Long,
    val hourlyUpdatedAtMs: Long,
    val dailyUpdatedAtMs: Long,
    val nowMs: Long,
    val thresholds: WeatherFreshnessThresholds
)

internal data class RequestGateConfig(
    val locationMinRequestIntervalMs: Long,
    val globalRequestWindowMs: Long,
    val globalRequestLimitPerWindow: Int
)

internal data class RequestGateSnapshot(
    val backoffUntilMs: Long,
    val locationLastRequestAtMs: Long?,
    val globalRequestCount: Int,
    val globalOldestRequestAtMs: Long?
)

internal data class RequestGateDecision(
    val isAllowed: Boolean,
    val nextAllowedAtMs: Long,
    val reasons: List<String>
)

internal object WeatherRequestPolicy {
    fun resolveDatasetsToRefresh(input: DatasetRefreshInput): Set<WeatherDataset> {
        val datasets = linkedSetOf<WeatherDataset>()

        val currentNeedsRefresh = input.force ||
            !input.hasCurrent ||
            isPastThreshold(input.currentUpdatedAtMs, input.thresholds.currentMs, input.nowMs)
        if (currentNeedsRefresh) {
            datasets += WeatherDataset.CURRENT
        }

        val hourlyNeedsRefresh = input.force ||
            !input.hasHourly ||
            isPastThreshold(input.hourlyUpdatedAtMs, input.thresholds.hourlyMs, input.nowMs)
        if (hourlyNeedsRefresh) {
            datasets += WeatherDataset.HOURLY
        }

        val dailyNeedsRefresh = input.force ||
            !input.hasDaily ||
            isPastThreshold(input.dailyUpdatedAtMs, input.thresholds.dailyMs, input.nowMs)
        if (dailyNeedsRefresh) {
            datasets += WeatherDataset.DAILY
        }

        return datasets
    }

    fun evaluateRequestGate(
        nowMs: Long,
        snapshot: RequestGateSnapshot,
        config: RequestGateConfig
    ): RequestGateDecision {
        val locationAllowedAt = snapshot.locationLastRequestAtMs
            ?.let { it + config.locationMinRequestIntervalMs }
            ?: 0L

        val globalAllowedAt = if (
            snapshot.globalRequestCount >= config.globalRequestLimitPerWindow &&
            snapshot.globalOldestRequestAtMs != null
        ) {
            snapshot.globalOldestRequestAtMs + config.globalRequestWindowMs
        } else {
            0L
        }

        val backoffAllowedAt = snapshot.backoffUntilMs
        val nextAllowedAt = maxOf(backoffAllowedAt, locationAllowedAt, globalAllowedAt)

        if (nextAllowedAt <= nowMs) {
            return RequestGateDecision(
                isAllowed = true,
                nextAllowedAtMs = nowMs,
                reasons = emptyList()
            )
        }

        val reasons = buildList {
            if (backoffAllowedAt > nowMs) add("backoff")
            if (locationAllowedAt > nowMs) add("per-location limit")
            if (globalAllowedAt > nowMs) add("global limit")
        }

        return RequestGateDecision(
            isAllowed = false,
            nextAllowedAtMs = nextAllowedAt,
            reasons = reasons
        )
    }

    fun backoffDelayForFailure(
        failureCount: Int,
        backoffStepsMs: LongArray
    ): Long {
        if (failureCount <= 0) return 0L
        val stepIndex = (failureCount - 1).coerceAtMost(backoffStepsMs.lastIndex)
        return backoffStepsMs[stepIndex]
    }

    fun pruneTimestampsWithinWindow(
        timestamps: Collection<Long>,
        nowMs: Long,
        windowMs: Long
    ): List<Long> {
        return timestamps.filter { nowMs - it < windowMs }
    }

    private fun isPastThreshold(updatedAtMs: Long, thresholdMs: Long, nowMs: Long): Boolean {
        return updatedAtMs <= 0L || nowMs - updatedAtMs > thresholdMs
    }
}

