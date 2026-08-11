package dev.trivk.smartringer.schedule

import dev.trivk.smartringer.model.RingerSchedule
import java.time.ZonedDateTime

internal enum class Boundary { START, END }

internal object ScheduleTiming {
    fun nextBoundary(
        schedule: RingerSchedule,
        boundary: Boundary,
        now: ZonedDateTime,
    ): ZonedDateTime? = (if (boundary == Boundary.END) -1..8 else 0..8)
        .asSequence()
        .map { now.toLocalDate().plusDays(it.toLong()) }
        .filter { it.dayOfWeek in schedule.days }
        .map { startDate ->
            val start = startDate.atTime(schedule.startTime).atZone(now.zone)
            when (boundary) {
                Boundary.START -> start
                Boundary.END -> startDate
                    .plusDays(if (schedule.endTime <= schedule.startTime) 1 else 0)
                    .atTime(schedule.endTime)
                    .atZone(now.zone)
            }
        }
        .firstOrNull { it.isAfter(now) }

    fun activeStart(schedule: RingerSchedule, now: ZonedDateTime): ZonedDateTime? =
        sequenceOf(now.toLocalDate(), now.toLocalDate().minusDays(1))
            .filter { it.dayOfWeek in schedule.days }
            .map { date -> date.atTime(schedule.startTime).atZone(now.zone) }
            .filter { start ->
                val end = start
                    .plusDays(if (schedule.endTime <= schedule.startTime) 1 else 0)
                    .withHour(schedule.endTime.hour)
                    .withMinute(schedule.endTime.minute)
                    .withSecond(0)
                    .withNano(0)
                !now.isBefore(start) && now.isBefore(end)
            }
            .maxOrNull()
}
