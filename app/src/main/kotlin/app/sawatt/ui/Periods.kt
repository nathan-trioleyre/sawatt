package app.sawatt.ui

import app.sawatt.core.history.HistoryStats
import app.sawatt.core.history.PeriodKey
import app.sawatt.core.history.PeriodKind
import app.sawatt.core.workout.WorkoutRecord
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.ChronoField
import java.time.temporal.IsoFields
import java.util.Locale

/** One bar of a period chart: a day of a week or month, or a month of a year. */
data class Bucket(val label: String, val seconds: Int, val isCurrent: Boolean)

fun currentPeriod(kind: PeriodKind, zone: ZoneId = ZoneId.systemDefault()): PeriodKey =
    HistoryStats.keyOf(System.currentTimeMillis(), kind, zone)

fun recordsIn(records: List<WorkoutRecord>, key: PeriodKey, zone: ZoneId = ZoneId.systemDefault()): List<WorkoutRecord> =
    records.filter { HistoryStats.keyOf(it.startEpochMs, key.kind, zone) == key }.sortedByDescending { it.startEpochMs }

private fun weekStart(key: PeriodKey): LocalDate =
    // 4 January always belongs to week 1 of its ISO week-based year.
    LocalDate.of(key.year, 1, 4)
        .with(IsoFields.WEEK_OF_WEEK_BASED_YEAR, key.number.toLong())
        .with(ChronoField.DAY_OF_WEEK, DayOfWeek.MONDAY.value.toLong())

/** Riding time per day (week, month) or per month (year). */
fun buckets(
    records: List<WorkoutRecord>,
    key: PeriodKey,
    zone: ZoneId = ZoneId.systemDefault(),
    today: LocalDate = LocalDate.now(zone),
): List<Bucket> {
    val dates = recordsIn(records, key, zone).map { Instant.ofEpochMilli(it.startEpochMs).atZone(zone).toLocalDate() to it.summary.durationSec }
    val locale = Locale.getDefault()
    return when (key.kind) {
        PeriodKind.WEEK -> {
            val start = weekStart(key)
            (0L until 7L).map { i ->
                val day = start.plusDays(i)
                Bucket(day.dayOfWeek.getDisplayName(TextStyle.NARROW, locale), dates.filter { it.first == day }.sumOf { it.second }, day == today)
            }
        }
        PeriodKind.MONTH -> {
            val month = YearMonth.of(key.year, key.number)
            (1..month.lengthOfMonth()).map { d ->
                val day = month.atDay(d)
                Bucket(d.toString(), dates.filter { it.first == day }.sumOf { it.second }, day == today)
            }
        }
        PeriodKind.YEAR -> Month.entries.map { m ->
            Bucket(
                m.getDisplayName(TextStyle.NARROW, locale),
                dates.filter { it.first.year == key.year && it.first.month == m }.sumOf { it.second },
                today.year == key.year && today.month == m,
            )
        }
    }
}

/** "12 – 18 October", "October 2026" or "2026". */
fun periodTitle(key: PeriodKey): String {
    val locale = Locale.getDefault()
    fun month(m: Month) = m.getDisplayName(TextStyle.FULL, locale).replaceFirstChar { it.titlecase(locale) }
    return when (key.kind) {
        PeriodKind.WEEK -> {
            val start = weekStart(key)
            val end = start.plusDays(6)
            if (start.month == end.month) "${start.dayOfMonth} – ${end.dayOfMonth} ${month(end.month)}"
            else "${start.dayOfMonth} ${month(start.month).take(3)} – ${end.dayOfMonth} ${month(end.month).take(3)}"
        }
        PeriodKind.MONTH -> "${month(Month.of(key.number))} ${key.year}"
        PeriodKind.YEAR -> key.year.toString()
    }
}
