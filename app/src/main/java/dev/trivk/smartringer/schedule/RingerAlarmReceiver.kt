package dev.trivk.smartringer.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.trivk.smartringer.data.ScheduleRepository

class RingerAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val schedules = ScheduleRepository(context).load()
        RingerScheduler(context).run {
            applyCurrentState(schedules)
            rescheduleAll(schedules)
        }
    }
}

