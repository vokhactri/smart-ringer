package dev.trivk.smartringer.schedule

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
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
    private val repository = ScheduleRepository(context)

    init {
        createChannels()
    }

    fun showActive(id: String, name: String, mode: RingerMode, endsAtMillis: Long) {
        if (!canNotify()) return
        val use24Hour = repository.loadSettings().use24HourTime
        val time = SimpleDateFormat(if (use24Hour) "HH:mm" else "h:mm a", Locale.getDefault())
            .format(Date(endsAtMillis))
        val title = "$name is active"
        val text = "${mode.label()} until $time"
        // Identifies this occurrence and everything rendered from it: endsAtMillis keeps two runs
        // of the same schedule apart, title and text make an edit re-post.
        val key = "$id|$endsAtMillis|$title|$text"

        // Reconcile runs on every app resume, alarm and recovery pass. Re-posting an unchanged
        // notification makes it resurface in the shade, so only post when something actually
        // changed — and never bring back the occurrence the user swiped away.
        val state = repository.loadActiveNotification()
        if (key == state.dismissedKey) return
        if (key == state.postedKey && isActivePosted()) return

        repository.saveActiveNotificationPosted(key)
        manager.notify(
            ACTIVE_NOTIFICATION_ID,
            Notification.Builder(context, STATUS_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(contentIntent())
                .setDeleteIntent(dismissIntent(key))
                .setCategory(Notification.CATEGORY_STATUS)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .build(),
        )
    }

    fun recordActiveDismissed(key: String) = repository.saveActiveNotificationDismissed(key)

    fun showEnded() {
        cancelActive()
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

    fun cancelActive() {
        manager.cancel(ACTIVE_NOTIFICATION_ID)
        repository.clearActiveNotification()
    }

    private fun isActivePosted(): Boolean =
        manager.activeNotifications.any { it.id == ACTIVE_NOTIFICATION_ID }

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

    private fun dismissIntent(key: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        DISMISS_REQUEST_CODE,
        Intent(context, ActiveNotificationDismissReceiver::class.java)
            .setAction(ACTION_ACTIVE_DISMISSED)
            .putExtra(EXTRA_KEY, key),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun RingerMode.label() = when (this) {
        RingerMode.VIBRATE -> "Vibrate"
        RingerMode.DO_NOT_DISTURB -> "Do Not Disturb"
        RingerMode.SILENT -> "Silent"
    }

    companion object {
        internal const val ACTION_ACTIVE_DISMISSED = "dev.trivk.smartringer.ACTIVE_DISMISSED"
        internal const val EXTRA_KEY = "key"
        private const val STATUS_CHANNEL_ID = "automation_status"
        private const val EVENT_CHANNEL_ID = "automation_events"
        private const val ACTIVE_NOTIFICATION_ID = 1001
        private const val EVENT_NOTIFICATION_ID = 1002
        private const val ERROR_NOTIFICATION_ID = 1003
        private const val DISMISS_REQUEST_CODE = 0x444D53
    }
}

/** Fires when the user swipes the ongoing notification away, so it is not posted again. */
class ActiveNotificationDismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AutomationNotifications.ACTION_ACTIVE_DISMISSED) return
        val key = intent.getStringExtra(AutomationNotifications.EXTRA_KEY) ?: return
        AutomationNotifications(context).recordActiveDismissed(key)
    }
}
