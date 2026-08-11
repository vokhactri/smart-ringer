package dev.trivk.smartringer.ui

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.trivk.smartringer.MainViewModel
import dev.trivk.smartringer.model.RingerSchedule

@Composable
fun SmartRingerApp(viewModel: MainViewModel) {
    val schedules by viewModel.schedules.collectAsStateWithLifecycle()
    var editorSchedule by remember { mutableStateOf<RingerSchedule?>(null) }
    var isCreating by remember { mutableStateOf(false) }

    Surface(Modifier.fillMaxSize()) {
        if (isCreating || editorSchedule != null) {
            ScheduleEditorScreen(
                existing = editorSchedule,
                onBack = {
                    isCreating = false
                    editorSchedule = null
                },
                onSave = {
                    viewModel.save(it)
                    isCreating = false
                    editorSchedule = null
                },
            )
        } else {
            HomeScreen(
                schedules = schedules,
                onAdd = { isCreating = true },
                onEdit = { editorSchedule = it },
                onToggle = viewModel::toggle,
                onDelete = viewModel::delete,
            )
        }
    }
}

@Composable
internal fun rememberSystemAccess(): SystemAccess {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var refreshToken by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshToken++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    @Suppress("UNUSED_VARIABLE")
    val refresh = refreshToken
    val alarmManager = context.getSystemService(AlarmManager::class.java)
    val notificationManager = context.getSystemService(NotificationManager::class.java)
    return SystemAccess(
        exactAlarmGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms(),
        dndGranted = notificationManager.isNotificationPolicyAccessGranted,
        requestExactAlarm = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = android.net.Uri.parse("package:${context.packageName}")
                })
            }
        },
        requestDnd = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) },
    )
}

internal data class SystemAccess(
    val exactAlarmGranted: Boolean,
    val dndGranted: Boolean,
    val requestExactAlarm: () -> Unit,
    val requestDnd: () -> Unit,
)
