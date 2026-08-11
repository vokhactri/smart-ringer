package dev.trivk.smartringer.schedule

import dev.trivk.smartringer.model.RingerMode
import dev.trivk.smartringer.model.RingerSchedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class ScheduleTimingTest {
    private val zone = ZoneId.of("Asia/Ho_Chi_Minh")

    @Test
    fun `schedule is active between start and end on selected day`() {
        val schedule = schedule(start = "09:00", end = "17:00", days = setOf(DayOfWeek.MONDAY))
        val now = dateTime("2026-08-10T12:00:00")

        assertEquals(dateTime("2026-08-10T09:00:00"), ScheduleTiming.activeStart(schedule, now))
    }

    @Test
    fun `overnight schedule remains active after midnight`() {
        val schedule = schedule(start = "22:00", end = "07:00", days = setOf(DayOfWeek.MONDAY))
        val now = dateTime("2026-08-11T02:00:00")

        assertEquals(dateTime("2026-08-10T22:00:00"), ScheduleTiming.activeStart(schedule, now))
    }

    @Test
    fun `schedule is inactive outside selected interval`() {
        val schedule = schedule(start = "09:00", end = "17:00", days = setOf(DayOfWeek.MONDAY))

        assertNull(ScheduleTiming.activeStart(schedule, dateTime("2026-08-11T12:00:00")))
    }

    @Test
    fun `next overnight end belongs to day after selected start`() {
        val schedule = schedule(start = "22:00", end = "07:00", days = setOf(DayOfWeek.MONDAY))
        val now = dateTime("2026-08-10T21:00:00")

        assertEquals(
            dateTime("2026-08-11T07:00:00"),
            ScheduleTiming.nextBoundary(schedule, Boundary.END, now),
        )
    }

    private fun schedule(start: String, end: String, days: Set<DayOfWeek>) = RingerSchedule(
        name = "Test",
        days = days,
        mode = RingerMode.VIBRATE,
        startTime = LocalTime.parse(start),
        endTime = LocalTime.parse(end),
    )

    private fun dateTime(value: String) = ZonedDateTime.of(java.time.LocalDateTime.parse(value), zone)
}
