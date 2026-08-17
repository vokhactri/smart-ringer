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
            applied?.let { finish(it, audioManager, notificationManager) }
            return
        }

        if (applied != null && applied.occurrence == selected.occurrence) {
            hold(selected, applied, audioManager, notificationManager)
            return
        }

        start(selected, applied, audioManager, notificationManager)
    }

    /** First reconcile of this run: remember the pre-automation state and apply the mode. */
    private fun start(
        selected: ActiveAutomation,
        applied: AppliedState?,
        audioManager: AudioManager,
        notificationManager: NotificationManager,
    ) {
        // Read before applying, otherwise the snapshot is the state we are about to install.
        // A chain of back-to-back schedules restores to the state before the first one.
        val previousRingerMode = applied?.previousRingerMode ?: audioManager.ringerMode
        val previousInterruptionFilter = applied?.previousInterruptionFilter
            ?: notificationManager.currentInterruptionFilter

        val succeeded = runCatching {
            when (selected.mode) {
                RingerMode.VIBRATE -> {
                    clearDoNotDisturb(notificationManager)
                    audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                }
                RingerMode.SILENT -> {
                    check(notificationManager.isNotificationPolicyAccessGranted)
                    clearDoNotDisturb(notificationManager)
                    audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
                }
                RingerMode.DO_NOT_DISTURB -> {
                    check(notificationManager.isNotificationPolicyAccessGranted)
                    notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
                }
            }
        }.isSuccess

        if (!succeeded) {
            // Nothing was changed, so nothing is recorded as applied — a later reconcile must not
            // "restore" a mode this run never touched.
            notifications.showPermissionError(selected.name)
            return
        }

        repository.saveAppliedState(
            AppliedState(
                ruleId = selected.id,
                occurrence = selected.occurrence,
                previousRingerMode = previousRingerMode,
                previousInterruptionFilter = previousInterruptionFilter,
                appliedRingerMode = audioManager.ringerMode,
                appliedInterruptionFilter = notificationManager.currentInterruptionFilter,
                overridden = false,
            ),
        )
        notifications.showActive(selected.id, selected.name, selected.mode, selected.endsAtMillis)
    }

    /**
     * A later reconcile of a run already applied. The mode is never rewritten here: if the ringer no
     * longer holds what the app left there, the user changed it by hand and owns it until this run
     * ends.
     */
    private fun hold(
        selected: ActiveAutomation,
        applied: AppliedState,
        audioManager: AudioManager,
        notificationManager: NotificationManager,
    ) {
        if (applied.overridden) {
            notifications.cancelActive()
            return
        }
        val changedByHand = audioManager.ringerMode != applied.appliedRingerMode ||
            notificationManager.currentInterruptionFilter != applied.appliedInterruptionFilter
        if (changedByHand) {
            repository.saveAppliedState(applied.copy(overridden = true))
            notifications.cancelActive()
            return
        }
        // Still holding. showActive is a no-op unless the notification actually went missing.
        notifications.showActive(selected.id, selected.name, selected.mode, selected.endsAtMillis)
    }

    /** The run is over: put back what the app changed, unless the user has taken over. */
    private fun finish(
        applied: AppliedState,
        audioManager: AudioManager,
        notificationManager: NotificationManager,
    ) {
        if (applied.overridden) {
            repository.clearAppliedState()
            notifications.cancelActive()
            return
        }
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
            notifications.cancelActive()
            notifications.showPermissionError("the previous ringer mode")
        }
    }

    /**
     * Vibrate and Silent are meaningless while Do Not Disturb is filtering, so a run clears it on
     * the way in. The pre-automation filter is snapshotted first and put back when the run ends.
     */
    private fun clearDoNotDisturb(manager: NotificationManager) {
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
