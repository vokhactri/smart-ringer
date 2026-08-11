package dev.trivk.smartringer.schedule

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import dev.trivk.smartringer.MainActivity
import dev.trivk.smartringer.R
import dev.trivk.smartringer.data.ScheduleRepository
import dev.trivk.smartringer.model.RingerMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AutomationNotifications(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    init {
        createChannels()
    }

    fun showActive(name: String, mode: RingerMode, endsAtMillis: Long) {
        if (!canNotify()) return
        val use24Hour = ScheduleRepository(context).loadSettings().use24HourTime
        val time = SimpleDateFormat(if (use24Hour) "HH:mm" else "h:mm a", Locale.getDefault())
            .format(Date(endsAtMillis))
        manager.notify(
            ACTIVE_NOTIFICATION_ID,
            Notification.Builder(context, STATUS_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("$name is active")
                .setContentText("${mode.label()} until $time")
                .setContentIntent(contentIntent())
                .setCategory(Notification.CATEGORY_STATUS)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .build(),
        )
    }

    fun showEnded() {
        manager.cancel(ACTIVE_NOTIFICATION_ID)
        if (!canNotify()) return
        manager.notify(
            EVENT_NOTIFICATION_ID,
            Notification.Builder(context, EVENT_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Smart Ringer finished")
                .setContentText("Your previous ringer mode was restored.")
                .setContentIntent(contentIntent())
                .setAutoCancel(true)
                .build(),
        )
    }

    fun showPermissionError(name: String) {
        if (!canNotify()) return
        manager.notify(
            ERROR_NOTIFICATION_ID,
            Notification.Builder(context, EVENT_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Could not apply $name")
                .setContentText("Open Smart Ringer and allow ringer policy access.")
                .setContentIntent(contentIntent())
                .setAutoCancel(true)
                .build(),
        )
    }

    fun cancelActive() = manager.cancel(ACTIVE_NOTIFICATION_ID)

    private fun createChannels() {
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(
                    STATUS_CHANNEL_ID,
                    "Active automation",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply { description = "Shows the currently active ringer schedule or timer" },
                NotificationChannel(
                    EVENT_CHANNEL_ID,
                    "Automation events",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply { description = "Notifies when automation ends or needs attention" },
            ),
        )
    }

    private fun canNotify(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun contentIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun RingerMode.label() = when (this) {
        RingerMode.VIBRATE -> "Vibrate"
        RingerMode.DO_NOT_DISTURB -> "Do Not Disturb"
        RingerMode.SILENT -> "Silent"
    }

    private companion object {
        const val STATUS_CHANNEL_ID = "automation_status"
        const val EVENT_CHANNEL_ID = "automation_events"
        const val ACTIVE_NOTIFICATION_ID = 1001
        const val EVENT_NOTIFICATION_ID = 1002
        const val ERROR_NOTIFICATION_ID = 1003
    }
}
