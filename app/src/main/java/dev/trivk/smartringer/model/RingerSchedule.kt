package dev.trivk.smartringer.model

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime
import java.util.UUID

enum class RingerMode {
    VIBRATE,
    DO_NOT_DISTURB,
    SILENT,
}

data class RingerSchedule(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val days: Set<DayOfWeek>,
    val mode: RingerMode,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val enabled: Boolean = true,
)

data class RingerTimer(
    val id: String = UUID.randomUUID().toString(),
    val mode: RingerMode,
    val startedAtMillis: Long,
    val endsAtMillis: Long,
) {
    val duration: Duration get() = Duration.ofMillis(endsAtMillis - startedAtMillis)
}

data class AppSettings(
    val automationEnabled: Boolean = true,
    val use24HourTime: Boolean = false,
)
