package dev.trivk.smartringer.schedule

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import dev.trivk.smartringer.data.AppliedState
import dev.trivk.smartringer.data.ScheduleRepository
import dev.trivk.smartringer.model.RingerMode
import dev.trivk.smartringer.model.RingerSchedule
import dev.trivk.smartringer.model.RingerTimer
import java.time.ZonedDateTime

class RingerScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val repository = ScheduleRepository(context)
    private val notifications = AutomationNotifications(context)

    fun reconcile(reason: TriggerReason = TriggerReason.APP_RESUME) {
        val now = System.currentTimeMillis()
        val schedules = repository.load()
        var timer = repository.loadTimer()
        if (timer != null && timer.endsAtMillis <= now) {
            repository.saveTimer(null)
            timer = null
        }

        if (!repository.loadSettings().automationEnabled) {
            cancelAll(schedules)
            applySelection(null)
            AutomationRecovery.schedule(context)
            return
        }

        rescheduleAll(schedules, timer)
        val selected = AutomationSelector.select(schedules, timer, now)
        applySelection(selected)
        AutomationRecovery.schedule(context)
    }

    fun cancel(schedule: RingerSchedule) {
        Boundary.entries.forEach { boundary -> alarmManager.cancel(schedulePendingIntent(schedule.id, boundary)) }
    }

    fun cancelTimer() {
        alarmManager.cancel(timerPendingIntent())
    }

    fun canScheduleExactAlarms(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    private fun rescheduleAll(schedules: List<RingerSchedule>, timer: RingerTimer?) {
        schedules.forEach(::cancel)
        schedules.filter(RingerSchedule::enabled).forEach { schedule ->
            scheduleBoundary(schedule, Boundary.START)
            scheduleBoundary(schedule, Boundary.END)
        }
        cancelTimer()
        timer?.let(::scheduleTimerEnd)
    }

    private fun cancelAll(schedules: List<RingerSchedule>) {
        schedules.forEach(::cancel)
        cancelTimer()
    }

    private fun applySelection(selected: ActiveAutomation?) {
        val audioManager = context.getSystemService(AudioManager::class.java)
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val applied = repository.loadAppliedState()

        if (selected == null) {
            if (applied != null) {
                val restored = runCatching {
                    audioManager.ringerMode = applied.previousRingerMode
                    if (notificationManager.isNotificationPolicyAccessGranted) {
                        notificationManager.setInterruptionFilter(applied.previousInterruptionFilter)
                    }
                }.isSuccess
                if (restored) {
                    repository.clearAppliedState()
                    notifications.showEnded()
                } else {
                    notifications.showPermissionError("the previous ringer mode")
                }
            }
            return
        }

        if (applied == null) {
            repository.saveAppliedState(
                AppliedState(
                    ruleId = selected.id,
                    previousRingerMode = audioManager.ringerMode,
                    previousInterruptionFilter = notificationManager.currentInterruptionFilter,
                ),
            )
        } else if (applied.ruleId != selected.id) {
            repository.updateAppliedRule(selected.id)
        }

        val succeeded = runCatching {
            when (selected.mode) {
                RingerMode.VIBRATE -> {
                    restoreInterruptions(notificationManager)
                    audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                }
                RingerMode.SILENT -> {
                    check(notificationManager.isNotificationPolicyAccessGranted)
                    restoreInterruptions(notificationManager)
                    audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
                }
                RingerMode.DO_NOT_DISTURB -> {
                    check(notificationManager.isNotificationPolicyAccessGranted)
                    notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
                }
            }
        }.isSuccess

        if (succeeded) {
            notifications.showActive(selected.name, selected.mode, selected.endsAtMillis)
        } else {
            notifications.showPermissionError(selected.name)
        }
    }

    private fun restoreInterruptions(manager: NotificationManager) {
        if (manager.isNotificationPolicyAccessGranted) {
            manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
        }
    }

    private fun scheduleBoundary(schedule: RingerSchedule, boundary: Boundary) {
        val trigger = ScheduleTiming.nextBoundary(schedule, boundary, ZonedDateTime.now()) ?: return
        setAlarm(trigger.toInstant().toEpochMilli(), schedulePendingIntent(schedule.id, boundary))
    }

    private fun scheduleTimerEnd(timer: RingerTimer) {
        if (timer.endsAtMillis > System.currentTimeMillis()) {
            setAlarm(timer.endsAtMillis, timerPendingIntent())
        }
    }

    private fun setAlarm(triggerAtMillis: Long, operation: PendingIntent) {
        if (canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation)
        }
    }

    private fun schedulePendingIntent(scheduleId: String, boundary: Boundary): PendingIntent {
        val intent = Intent(context, RingerAlarmReceiver::class.java)
            .setAction("dev.trivk.smartringer.SCHEDULE_${boundary.name}")
            .putExtra("scheduleId", scheduleId)
        return PendingIntent.getBroadcast(
            context,
            ("$scheduleId:${boundary.name}").hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun timerPendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        TIMER_REQUEST_CODE,
        Intent(context, RingerAlarmReceiver::class.java).setAction(ACTION_TIMER_END),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val ACTION_TIMER_END = "dev.trivk.smartringer.TIMER_END"
        private const val TIMER_REQUEST_CODE = 0x534D52
    }
}

enum class TriggerReason { APP_RESUME, ALARM, BOOT, WORKER, USER, TILE }
