package com.tudorc.anemoi.util

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

object PerformanceProfiler {
    private const val maxSectionSamples = 1200
    private const val maxFrameSamples = 2400
    private const val profilerCategory = "profiler"
    private const val profilerMeasureOverheadName = "Profiler/Internal/MeasureBookkeeping"
    private const val profilerRecordOverheadName = "Profiler/Internal/Record"
    private const val profilerFrameRecordOverheadName = "Profiler/Internal/FrameRecord"
    private const val profilerSnapshotOverheadName = "Profiler/Internal/Snapshot"

    private val enabled = AtomicBoolean(false)
    private val lock = Any()
    private val sections = ConcurrentHashMap<String, SectionBuffer>()
    private val frameBuffer = RingBuffer(maxFrameSamples)

    data class SectionSnapshot(
        val name: String,
        val category: String,
        val totalNs: Long,
        val averageNs: Long,
        val p95Ns: Long,
        val maxNs: Long,
        val sampleCount: Int,
        val sharePercent: Float
    )

    data class CategorySnapshot(
        val category: String,
        val totalNs: Long,
        val sharePercent: Float
    )

    data class FrameSnapshot(
        val sampleCount: Int,
        val averageFrameMs: Float,
        val p95FrameMs: Float,
        val maxFrameMs: Float,
        val jank16Percent: Float,
        val jank33Percent: Float
    ) {
        companion object {
            val Empty = FrameSnapshot(
                sampleCount = 0,
                averageFrameMs = 0f,
                p95FrameMs = 0f,
                maxFrameMs = 0f,
                jank16Percent = 0f,
                jank33Percent = 0f
            )
        }
    }

    data class Snapshot(
        val windowMs: Long,
        val sections: List<SectionSnapshot>,
        val categories: List<CategorySnapshot>,
        val frame: FrameSnapshot,
        val totalSectionNs: Long
    ) {
        companion object {
            val Empty = Snapshot(
                windowMs = 0L,
                sections = emptyList(),
                categories = emptyList(),
                frame = FrameSnapshot.Empty,
                totalSectionNs = 0L
            )
        }
    }

    fun setEnabled(value: Boolean) {
        enabled.set(value)
    }

    fun reset() {
        synchronized(lock) {
            sections.clear()
            frameBuffer.clear()
        }
    }

    fun <T> measure(
        name: String,
        category: String = "general",
        block: () -> T
    ): T {
        if (!enabled.get()) return block()
        val startNs = System.nanoTime()
        return try {
            block()
        } finally {
            val afterBlockNs = System.nanoTime()
            record(name = name, durationNs = (afterBlockNs - startNs), category = category)
            val bookkeepingNs = System.nanoTime() - afterBlockNs
            recordInternal(
                name = profilerMeasureOverheadName,
                durationNs = bookkeepingNs
            )
        }
    }

    fun record(name: String, durationNs: Long, category: String = "general") {
        if (!enabled.get() || durationNs <= 0L) return
        val startNs = System.nanoTime()
        val nowNs = startNs
        synchronized(lock) {
            val section = sections.getOrPut(name) {
                SectionBuffer(category = category, capacity = maxSectionSamples)
            }
            section.add(timestampNs = nowNs, durationNs = durationNs)
        }
        val overheadNs = System.nanoTime() - startNs
        recordInternal(
            name = profilerRecordOverheadName,
            durationNs = overheadNs
        )
    }

    fun recordFrameDuration(durationNs: Long) {
        if (!enabled.get() || durationNs <= 0L) return
        val startNs = System.nanoTime()
        val nowNs = startNs
        synchronized(lock) {
            frameBuffer.add(timestampNs = nowNs, valueNs = durationNs)
        }
        val overheadNs = System.nanoTime() - startNs
        recordInternal(
            name = profilerFrameRecordOverheadName,
            durationNs = overheadNs
        )
    }

    fun snapshot(
        windowMs: Long = 10_000L,
        topN: Int = 12
    ): Snapshot {
        if (windowMs <= 0L) return Snapshot.Empty
        if (!enabled.get()) return Snapshot.Empty
        val startNs = System.nanoTime()
        val nowNs = System.nanoTime()
        val windowNs = windowMs * 1_000_000L

        val result = synchronized(lock) {
            val rawSectionStats = sections.mapNotNull { (name, section) ->
                section.snapshot(name = name, nowNs = nowNs, windowNs = windowNs)
            }
            val totalSectionNs = rawSectionStats.sumOf { it.totalNs }
            val rankedSections = rawSectionStats
                .sortedByDescending { it.totalNs }
                .take(topN.coerceAtLeast(1))
                .map { raw ->
                    val share = if (totalSectionNs > 0L) {
                        (raw.totalNs.toDouble() / totalSectionNs.toDouble() * 100.0).toFloat()
                    } else {
                        0f
                    }
                    SectionSnapshot(
                        name = raw.name,
                        category = raw.category,
                        totalNs = raw.totalNs,
                        averageNs = raw.averageNs,
                        p95Ns = raw.p95Ns,
                        maxNs = raw.maxNs,
                        sampleCount = raw.sampleCount,
                        sharePercent = share
                    )
                }

            val categoryTotals = mutableMapOf<String, Long>()
            rawSectionStats.forEach { item ->
                categoryTotals[item.category] = (categoryTotals[item.category] ?: 0L) + item.totalNs
            }
            val rankedCategories = categoryTotals.entries
                .sortedByDescending { it.value }
                .take(8)
                .map { entry ->
                    val share = if (totalSectionNs > 0L) {
                        (entry.value.toDouble() / totalSectionNs.toDouble() * 100.0).toFloat()
                    } else {
                        0f
                    }
                    CategorySnapshot(
                        category = entry.key,
                        totalNs = entry.value,
                        sharePercent = share
                    )
                }

            Snapshot(
                windowMs = windowMs,
                sections = rankedSections,
                categories = rankedCategories,
                frame = frameBuffer.frameSnapshot(nowNs = nowNs, windowNs = windowNs),
                totalSectionNs = totalSectionNs
            )
        }
        val overheadNs = System.nanoTime() - startNs
        recordInternal(
            name = profilerSnapshotOverheadName,
            durationNs = overheadNs
        )
        return result
    }

    private fun recordInternal(name: String, durationNs: Long) {
        if (!enabled.get() || durationNs <= 0L) return
        val nowNs = System.nanoTime()
        synchronized(lock) {
            val section = sections.getOrPut(name) {
                SectionBuffer(category = profilerCategory, capacity = maxSectionSamples)
            }
            section.add(timestampNs = nowNs, durationNs = durationNs)
        }
    }

    private data class RawSectionSnapshot(
        val name: String,
        val category: String,
        val totalNs: Long,
        val averageNs: Long,
        val p95Ns: Long,
        val maxNs: Long,
        val sampleCount: Int
    )

    private class SectionBuffer(
        private val category: String,
        capacity: Int
    ) {
        private val ring = RingBuffer(capacity)

        fun add(timestampNs: Long, durationNs: Long) {
            ring.add(timestampNs = timestampNs, valueNs = durationNs)
        }

        fun snapshot(name: String, nowNs: Long, windowNs: Long): RawSectionSnapshot? {
            val values = ring.valuesInWindow(nowNs = nowNs, windowNs = windowNs)
            if (values.isEmpty()) return null

            val sorted = values.sorted()
            val sampleCount = sorted.size
            val totalNs = sorted.sum()
            val averageNs = if (sampleCount > 0) totalNs / sampleCount else 0L
            val p95Ns = percentile(sorted, 0.95)
            val maxNs = sorted.lastOrNull() ?: 0L

            return RawSectionSnapshot(
                name = name,
                category = category,
                totalNs = totalNs,
                averageNs = averageNs,
                p95Ns = p95Ns,
                maxNs = maxNs,
                sampleCount = sampleCount
            )
        }
    }

    private class RingBuffer(
        private val capacity: Int
    ) {
        private val timestampsNs = LongArray(capacity)
        private val valuesNs = LongArray(capacity)
        private var nextIndex = 0
        private var count = 0

        fun add(timestampNs: Long, valueNs: Long) {
            timestampsNs[nextIndex] = timestampNs
            valuesNs[nextIndex] = valueNs
            nextIndex = (nextIndex + 1) % capacity
            if (count < capacity) {
                count++
            }
        }

        fun valuesInWindow(nowNs: Long, windowNs: Long): List<Long> {
            if (count == 0) return emptyList()
            val values = ArrayList<Long>(count)
            for (i in 0 until count) {
                val ts = timestampsNs[i]
                if (ts == 0L) continue
                if ((nowNs - ts) <= windowNs) {
                    values.add(valuesNs[i])
                }
            }
            return values
        }

        fun frameSnapshot(nowNs: Long, windowNs: Long): FrameSnapshot {
            val frameDurations = valuesInWindow(nowNs = nowNs, windowNs = windowNs)
            if (frameDurations.isEmpty()) return FrameSnapshot.Empty

            val sorted = frameDurations.sorted()
            val sampleCount = sorted.size
            val totalNs = sorted.sum()
            val avgMs = totalNs.toFloat() / sampleCount.toFloat() / 1_000_000f
            val p95Ms = percentile(sorted, 0.95).toFloat() / 1_000_000f
            val maxMs = (sorted.lastOrNull() ?: 0L).toFloat() / 1_000_000f
            val jank16 = sorted.count { it > 16_666_667L }
            val jank33 = sorted.count { it > 33_333_333L }

            return FrameSnapshot(
                sampleCount = sampleCount,
                averageFrameMs = avgMs,
                p95FrameMs = p95Ms,
                maxFrameMs = maxMs,
                jank16Percent = (jank16.toDouble() / sampleCount.toDouble() * 100.0).toFloat(),
                jank33Percent = (jank33.toDouble() / sampleCount.toDouble() * 100.0).toFloat()
            )
        }

        fun clear() {
            nextIndex = 0
            count = 0
            timestampsNs.fill(0L)
            valuesNs.fill(0L)
        }
    }

    private fun percentile(sorted: List<Long>, fraction: Double): Long {
        if (sorted.isEmpty()) return 0L
        val clampedFraction = fraction.coerceIn(0.0, 1.0)
        val index = ((sorted.size - 1) * clampedFraction).roundToInt().coerceIn(0, sorted.lastIndex)
        return sorted[index]
    }
}
