package dev.trivk.smartringer.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.trivk.smartringer.data.ScheduleRepository

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in supportedActions) return
        val schedules = ScheduleRepository(context).load()
        RingerScheduler(context).run {
            applyCurrentState(schedules)
            rescheduleAll(schedules)
        }
    }

    private companion object {
        val supportedActions = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
        )
    }
}
