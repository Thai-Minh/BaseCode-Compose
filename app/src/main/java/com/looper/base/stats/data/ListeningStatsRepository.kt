package com.looper.base.stats.data

import com.looper.base.database.dao.ListenRow
import com.looper.base.database.dao.PlaybackEventDao
import com.looper.base.stats.model.DayStat
import com.looper.base.stats.model.ListenerArchetype
import com.looper.base.stats.model.ListeningFingerprint
import com.looper.base.stats.model.ListeningReceipt
import com.looper.base.stats.model.MonthlyRecap
import com.looper.base.stats.model.NamedStat
import com.looper.base.stats.model.PeriodComparison
import com.looper.base.stats.model.PeriodStats
import com.looper.base.stats.model.ReceiptLine
import com.looper.base.stats.model.StatsPeriod
import com.looper.base.stats.model.StatsRange
import com.looper.base.stats.model.TrackStat
import com.looper.base.stats.model.WrappedSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.max

class ListeningStatsRepository(
    private val playbackEventDao: PlaybackEventDao,
) {

    private companion object {
        const val DAY_MS = 24L * 60L * 60L * 1000L
        const val TOP_COUNT = 10
        const val MIN_PLAYS_FOR_ARCHETYPE = 20
        const val CONCENTRATION_SHARE = 0.1
    }

    private val zone: ZoneId
        get() = ZoneId.systemDefault()

    fun observeTotalListenedMs(): Flow<Long> =
        playbackEventDao.observeTotalListened(from = 0L, to = Long.MAX_VALUE)

    fun range(period: StatsPeriod, now: Long = System.currentTimeMillis()): StatsRange {
        val from = when (val days = period.days) {
            null -> LocalDate.now(zone).withDayOfYear(1).atStartOfDay(zone).toInstant()
                .toEpochMilli()

            else -> LocalDate.now(zone).minusDays(days - 1).atStartOfDay(zone).toInstant()
                .toEpochMilli()
        }
        return StatsRange(from = from, to = now)
    }

    fun previousRange(range: StatsRange): StatsRange {
        val length = range.to - range.from
        return StatsRange(from = range.from - length, to = range.from)
    }

    suspend fun compare(period: StatsPeriod): PeriodComparison {
        val current = range(period = period)
        return PeriodComparison(
            current = stats(range = current),
            previous = stats(range = previousRange(range = current)),
        )
    }

    suspend fun stats(range: StatsRange): PeriodStats {
        val rows = playbackEventDao.getRows(from = range.from, to = range.to)
        val firstListens = playbackEventDao.firstListenPerItem().associate {
            it.mediaItemId to it.startedAt
        }
        return withContext(Dispatchers.Default) {
            compute(range = range, rows = rows, firstListens = firstListens)
        }
    }

    suspend fun monthlyRecaps(year: Int): List<MonthlyRecap> =
        (1..12).mapNotNull { month ->
            val start = YearMonth.of(year, month)
            val from = start.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
            if (from > System.currentTimeMillis()) {
                return@mapNotNull null
            }
            val to = start.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
                .coerceAtMost(maximumValue = System.currentTimeMillis())
            val stats = stats(range = StatsRange(from = from, to = to))
            if (stats.isEmpty) {
                null
            } else {
                MonthlyRecap(year = year, month = month, stats = stats)
            }
        }

    suspend fun wrapped(year: Int): WrappedSummary {
        val from = LocalDate.of(year, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli()
        val to = LocalDate.of(year + 1, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli()
            .coerceAtMost(maximumValue = System.currentTimeMillis())
        val stats = stats(range = StatsRange(from = from, to = to))
        return WrappedSummary(
            year = year,
            stats = stats,
            archetype = archetype(stats = stats),
            topMonth = monthlyRecaps(year = year).maxByOrNull {
                it.stats.totalListenedMs
            },
        )
    }

    suspend fun receipt(range: StatsRange, lineCount: Int = TOP_COUNT): ListeningReceipt {
        val stats = stats(range = range)
        return ListeningReceipt(
            range = range,
            issuedAt = System.currentTimeMillis(),
            lines = stats.topTracks.take(n = lineCount).mapIndexed { index, track ->
                ReceiptLine(
                    position = index + 1,
                    title = track.title,
                    artist = track.artist,
                    plays = track.plays,
                    listenedMs = track.listenedMs,
                )
            },
            totalListenedMs = stats.totalListenedMs,
            totalPlays = stats.plays,
        )
    }

    fun archetype(stats: PeriodStats): ListenerArchetype? {
        if (stats.plays < MIN_PLAYS_FOR_ARCHETYPE) {
            return null
        }
        val fingerprint = stats.fingerprint
        return mapOf(
            ListenerArchetype.DEVOTEE to fingerprint.concentration,
            ListenerArchetype.REGULAR to fingerprint.consistency,
            ListenerArchetype.EXPLORER to fingerprint.discovery,
            ListenerArchetype.OMNIVORE to fingerprint.diversity,
            ListenerArchetype.DEEP_DIVER to fingerprint.replay,
        ).maxBy {
            it.value
        }.key
    }

    private fun compute(
        range: StatsRange,
        rows: List<ListenRow>,
        firstListens: Map<String, Long>
    ): PeriodStats {
        val periodDays =
            max(a = 1, b = ceil(x = (range.to - range.from).toDouble() / DAY_MS).toInt())
        val byHour = LongArray(size = 24)
        val byWeekday = DayOfWeek.entries.associateWith {
            0L
        }.toMutableMap()
        val byDay = sortedMapOf<LocalDate, Long>()
        rows.forEach { row ->
            val time = Instant.ofEpochMilli(row.startedAt).atZone(zone)
            byHour[time.hour] += row.listenedMs
            byWeekday[time.dayOfWeek] = (byWeekday[time.dayOfWeek] ?: 0L) + row.listenedMs
            byDay[time.toLocalDate()] = (byDay[time.toLocalDate()] ?: 0L) + row.listenedMs
        }

        val topTracks = rows.groupBy { it.mediaItemId }
            .map { (mediaItemId, plays) ->
                TrackStat(
                    mediaItemId = mediaItemId,
                    title = plays.first().title,
                    artist = plays.first().artist,
                    plays = plays.size,
                    listenedMs = plays.sumOf {
                        it.listenedMs
                    },
                )
            }
            .sortedWith(comparator = compareByDescending<TrackStat> {
                it.plays
            }.thenByDescending {
                it.listenedMs
            })

        val artistRows = rows.filter {
            !it.artist.isNullOrBlank()
        }.groupBy {
            it.artist!!.trim()
        }
        val topArtists = named(groups = artistRows)
        val topAlbums =
            named(groups = rows.filter {
                !it.album.isNullOrBlank()
            }.groupBy {
                it.album!!.trim()
            })

        val newTrackIds = topTracks.map {
            it.mediaItemId
        }.filter {
            (firstListens[it] ?: Long.MAX_VALUE) >= range.from
        }.toSet()
        val newArtists = artistRows.count { (_, plays) ->
            plays.all {
                it.mediaItemId in newTrackIds
            }
        }
        val busiest = byDay.maxByOrNull {
            it.value
        }?.let {
            DayStat(date = it.key, listenedMs = it.value)
        }

        return PeriodStats(
            range = range,
            totalListenedMs = rows.sumOf {
                it.listenedMs
            },
            plays = rows.size,
            distinctTracks = topTracks.size,
            distinctArtists = artistRows.size,
            newTracks = newTrackIds.size,
            newArtists = newArtists,
            activeDays = byDay.size,
            periodDays = periodDays,
            busiestDay = busiest,
            listenedByHour = byHour.toList(),
            listenedByWeekday = byWeekday,
            listenedByDay = byDay.map {
                DayStat(date = it.key, listenedMs = it.value)
            },
            topTracks = topTracks.take(n = TOP_COUNT),
            topArtists = topArtists.take(n = TOP_COUNT),
            topAlbums = topAlbums.take(n = TOP_COUNT),
            fingerprint = fingerprint(
                rows = rows,
                tracks = topTracks,
                artistRows = artistRows,
                newTracks = newTrackIds.size,
                activeDays = byDay.size,
                periodDays = periodDays,
            ),
        )
    }

    private fun named(groups: Map<String, List<ListenRow>>): List<NamedStat> =
        groups.map { (name, plays) ->
            NamedStat(name = name, plays = plays.size, listenedMs = plays.sumOf {
                it.listenedMs
            })
        }.sortedWith(comparator = compareByDescending<NamedStat> {
            it.listenedMs
        }.thenByDescending {
            it.plays
        })

    private fun fingerprint(
        rows: List<ListenRow>,
        tracks: List<TrackStat>,
        artistRows: Map<String, List<ListenRow>>,
        newTracks: Int,
        activeDays: Int,
        periodDays: Int,
    ): ListeningFingerprint {
        if (rows.isEmpty()) {
            return ListeningFingerprint(
                concentration = 0f,
                consistency = 0f,
                discovery = 0f,
                diversity = 0f,
                replay = 0f
            )
        }
        val total = rows.sumOf {
            it.listenedMs
        }.coerceAtLeast(minimumValue = 1L)
        val topCount = max(a = 1, b = ceil(x = tracks.size * CONCENTRATION_SHARE).toInt())
        val topListened =
            tracks.sortedByDescending {
                it.listenedMs
            }.take(n = topCount).sumOf {
                it.listenedMs
            }
        val artistCount = artistRows.size
        val diversity = if (artistCount <= 1) {
            0.0
        } else {
            val plays = artistRows.values.sumOf {
                it.size
            }.toDouble()
            val entropy = artistRows.values.sumOf {
                val share = it.size / plays
                -share * ln(x = share)
            }
            entropy / ln(x = artistCount.toDouble())
        }
        return ListeningFingerprint(
            concentration = (topListened.toFloat() / total).coerceIn(
                minimumValue = 0f,
                maximumValue = 1f
            ),
            consistency = (activeDays.toFloat() / periodDays).coerceIn(
                minimumValue = 0f,
                maximumValue = 1f
            ),
            discovery = (newTracks.toFloat() / tracks.size).coerceIn(
                minimumValue = 0f,
                maximumValue = 1f
            ),
            diversity = diversity.toFloat().coerceIn(minimumValue = 0f, maximumValue = 1f),
            replay = (1f - tracks.size.toFloat() / rows.size).coerceIn(
                minimumValue = 0f,
                maximumValue = 1f
            ),
        )
    }
}
