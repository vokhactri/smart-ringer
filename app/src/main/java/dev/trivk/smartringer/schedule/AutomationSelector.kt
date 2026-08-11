package dev.trivk.smartringer.schedule

import dev.trivk.smartringer.model.RingerMode
import dev.trivk.smartringer.model.RingerSchedule
import dev.trivk.smartringer.model.RingerTimer
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

internal object AutomationSelector {
    fun select(
        schedules: List<RingerSchedule>,
        timer: RingerTimer?,
        nowMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): ActiveAutomation? {
        if (timer != null && nowMillis in timer.startedAtMillis until timer.endsAtMillis) {
            return ActiveAutomation(
                id = "timer:${timer.id}",
                name = "Timer",
                mode = timer.mode,
                endsAtMillis = timer.endsAtMillis,
            )
        }

        val now = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), zoneId)
        return schedules
            .asSequence()
            .filter(RingerSchedule::enabled)
            .mapNotNull { schedule -> ScheduleTiming.activeStart(schedule, now)?.let { schedule to it } }
            .maxByOrNull { it.second }
            ?.let { (schedule, start) ->
                val end = start
                    .plusDays(if (schedule.endTime <= schedule.startTime) 1 else 0)
                    .withHour(schedule.endTime.hour)
                    .withMinute(schedule.endTime.minute)
                    .withSecond(0)
                    .withNano(0)
                ActiveAutomation(
                    id = "schedule:${schedule.id}",
                    name = schedule.name,
                    mode = schedule.mode,
                    endsAtMillis = end.toInstant().toEpochMilli(),
                )
            }
    }
}

internal data class ActiveAutomation(
    val id: String,
    val name: String,
    val mode: RingerMode,
    val endsAtMillis: Long,
)

