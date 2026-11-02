package com.looper.base.stats.model

import java.time.DayOfWeek
import java.time.LocalDate

enum class StatsPeriod(val days: Long?) {
    LAST_7_DAYS(days = 7),
    LAST_30_DAYS(days = 30),
    LAST_90_DAYS(days = 90),
    THIS_YEAR(days = null),
}

data class StatsRange(
    val from: Long,
    val to: Long,
)

data class TrackStat(
    val mediaItemId: String,
    val title: String,
    val artist: String?,
    val plays: Int,
    val listenedMs: Long,
)

data class NamedStat(
    val name: String,
    val plays: Int,
    val listenedMs: Long,
)

data class DayStat(
    val date: LocalDate,
    val listenedMs: Long,
)

data class ListeningFingerprint(
    val concentration: Float,
    val consistency: Float,
    val discovery: Float,
    val diversity: Float,
    val replay: Float,
)

enum class ListenerArchetype { DEVOTEE, REGULAR, EXPLORER, OMNIVORE, DEEP_DIVER }

data class PeriodStats(
    val range: StatsRange,
    val totalListenedMs: Long,
    val plays: Int,
    val distinctTracks: Int,
    val distinctArtists: Int,
    val newTracks: Int,
    val newArtists: Int,
    val activeDays: Int,
    val periodDays: Int,
    val busiestDay: DayStat?,
    val listenedByHour: List<Long>,
    val listenedByWeekday: Map<DayOfWeek, Long>,
    val listenedByDay: List<DayStat>,
    val topTracks: List<TrackStat>,
    val topArtists: List<NamedStat>,
    val topAlbums: List<NamedStat>,
    val fingerprint: ListeningFingerprint,
) {
    val averagePerDayMs: Long
        get() = if (periodDays > 0) totalListenedMs / periodDays else 0L

    val busiestHour: Int?
        get() = listenedByHour.withIndex()
            .filter {
                it.value > 0L
            }
            .maxByOrNull {
                it.value
            }
            ?.index

    val isEmpty: Boolean
        get() = plays == 0
}

data class PeriodComparison(
    val current: PeriodStats,
    val previous: PeriodStats,
) {
    val listenedChange: Float?
        get() = change(current = current.totalListenedMs, previous = previous.totalListenedMs)

    val playsChange: Float?
        get() = change(current = current.plays.toLong(), previous = previous.plays.toLong())

    private fun change(current: Long, previous: Long): Float? =
        if (previous > 0L) {
            (current - previous).toFloat() / previous
        } else {
            null
        }
}

data class MonthlyRecap(
    val year: Int,
    val month: Int,
    val stats: PeriodStats,
)

data class WrappedSummary(
    val year: Int,
    val stats: PeriodStats,
    val archetype: ListenerArchetype?,
    val topMonth: MonthlyRecap?,
)

data class ReceiptLine(
    val position: Int,
    val title: String,
    val artist: String?,
    val plays: Int,
    val listenedMs: Long,
)

data class ListeningReceipt(
    val range: StatsRange,
    val issuedAt: Long,
    val lines: List<ReceiptLine>,
    val totalListenedMs: Long,
    val totalPlays: Int,
)
