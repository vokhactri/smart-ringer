package dev.trivk.smartringer.schedule

import dev.trivk.smartringer.model.RingerMode
import dev.trivk.smartringer.model.RingerSchedule
import dev.trivk.smartringer.model.RingerTimer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class AutomationSelectorTest {
    private val zone = ZoneId.of("Asia/Ho_Chi_Minh")

    @Test
    fun `active timer overrides an overlapping schedule`() {
        val now = epoch("2026-08-10T10:00:00")
        val schedule = schedule("work", "09:00", "17:00")
        val timer = RingerTimer(
            id = "focus",
            mode = RingerMode.SILENT,
            startedAtMillis = now - 60_000,
            endsAtMillis = now + 60_000,
        )

        val selected = AutomationSelector.select(listOf(schedule), timer, now, zone)

        assertEquals("timer:focus", selected?.id)
        assertEquals(RingerMode.SILENT, selected?.mode)
    }

    @Test
    fun `expired timer falls back to active schedule`() {
        val now = epoch("2026-08-10T10:00:00")
        val timer = RingerTimer(
            id = "expired",
            mode = RingerMode.SILENT,
            startedAtMillis = now - 120_000,
            endsAtMillis = now - 60_000,
        )

        val selected = AutomationSelector.select(listOf(schedule("work", "09:00", "17:00")), timer, now, zone)

        assertEquals("schedule:work", selected?.id)
    }

    @Test
    fun `disabled schedule is never selected`() {
        val now = epoch("2026-08-10T10:00:00")

        assertNull(AutomationSelector.select(listOf(schedule("work", "09:00", "17:00", false)), null, now, zone))
    }

    private fun schedule(id: String, start: String, end: String, enabled: Boolean = true) = RingerSchedule(
        id = id,
        name = "Work",
        days = setOf(DayOfWeek.MONDAY),
        mode = RingerMode.VIBRATE,
        startTime = LocalTime.parse(start),
        endTime = LocalTime.parse(end),
        enabled = enabled,
    )

    private fun epoch(value: String) = LocalDateTime.parse(value).atZone(zone).toInstant().toEpochMilli()
}
