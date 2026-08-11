package dev.trivk.smartringer.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class RingerAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val validAction = intent.action == RingerScheduler.ACTION_TIMER_END ||
            intent.action?.startsWith("dev.trivk.smartringer.SCHEDULE_") == true
        if (!validAction) return
        RingerScheduler(context).reconcile(TriggerReason.ALARM)
    }
}
