package dev.trivk.smartringer.schedule

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import dev.trivk.smartringer.model.RingerMode
import dev.trivk.smartringer.model.RingerSchedule
import java.time.ZonedDateTime

class RingerScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun rescheduleAll(schedules: List<RingerSchedule>) {
        schedules.forEach(::cancel)
        schedules.filter(RingerSchedule::enabled).forEach { schedule ->
            scheduleBoundary(schedule, Boundary.START)
            scheduleBoundary(schedule, Boundary.END)
        }
    }

    fun cancel(schedule: RingerSchedule) {
        Boundary.entries.forEach { boundary -> alarmManager.cancel(pendingIntent(schedule.id, boundary)) }
    }

    fun applyCurrentState(schedules: List<RingerSchedule>, now: ZonedDateTime = ZonedDateTime.now()) {
        val active = schedules
            .asSequence()
            .filter(RingerSchedule::enabled)
            .mapNotNull { schedule -> ScheduleTiming.activeStart(schedule, now)?.let { schedule to it } }
            .maxByOrNull { it.second }
            ?.first

        val audioManager = context.getSystemService(AudioManager::class.java)
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        runCatching {
            when (active?.mode) {
                RingerMode.VIBRATE -> {
                    restoreInterruptions(notificationManager)
                    audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                }
                RingerMode.SILENT -> if (notificationManager.isNotificationPolicyAccessGranted) {
                    restoreInterruptions(notificationManager)
                    audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
                }
                RingerMode.DO_NOT_DISTURB -> if (notificationManager.isNotificationPolicyAccessGranted) {
                    notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
                }
                null -> {
                    restoreInterruptions(notificationManager)
                    audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
                }
            }
        }
    }

    private fun restoreInterruptions(manager: NotificationManager) {
        if (manager.isNotificationPolicyAccessGranted) {
            manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
        }
    }

    private fun scheduleBoundary(schedule: RingerSchedule, boundary: Boundary) {
        val trigger = ScheduleTiming.nextBoundary(schedule, boundary, ZonedDateTime.now()) ?: return
        val operation = pendingIntent(schedule.id, boundary)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger.toInstant().toEpochMilli(), operation)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger.toInstant().toEpochMilli(), operation)
        }
    }

    private fun pendingIntent(scheduleId: String, boundary: Boundary): PendingIntent {
        val intent = Intent(context, RingerAlarmReceiver::class.java)
            .setAction("dev.trivk.smartringer.${boundary.name}")
            .putExtra("scheduleId", scheduleId)
        val requestCode = ("$scheduleId:${boundary.name}").hashCode()
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
