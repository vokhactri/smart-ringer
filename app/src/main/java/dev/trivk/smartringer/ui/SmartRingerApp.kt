package dev.trivk.smartringer.ui

import android.app.AlarmManager
import android.app.StatusBarManager
import android.app.NotificationManager
import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.trivk.smartringer.MainViewModel
import dev.trivk.smartringer.model.RingerSchedule
import dev.trivk.smartringer.R
import dev.trivk.smartringer.schedule.AutomationTileService
import kotlinx.coroutines.CancellationException

@Composable
fun SmartRingerApp(viewModel: MainViewModel) {
    val schedules by viewModel.schedules.collectAsStateWithLifecycle()
    val timer by viewModel.timer.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val access = rememberSystemAccess()
    var editorSchedule by remember { mutableStateOf<RingerSchedule?>(null) }
    var screen by rememberSaveable { mutableStateOf(AppScreen.HOME) }
    var showAddChoice by remember { mutableStateOf(false) }
    var permissionSetupDismissed by rememberSaveable { mutableStateOf(false) }
    var notificationRequested by rememberSaveable { mutableStateOf(false) }
    var backProgress by remember { mutableFloatStateOf(0f) }

    fun navigateHome() {
        screen = AppScreen.HOME
        editorSchedule = null
    }

    PredictiveBackHandler(enabled = screen != AppScreen.HOME) { progress ->
        try {
            progress.collect { backProgress = it.progress }
            navigateHome()
            backProgress = 0f
        } catch (_: CancellationException) {
            // A cancelled back gesture must leave the current screen intact.
            backProgress = 0f
        }
    }

    LaunchedEffect(access.notificationGranted) {
        if (!access.notificationGranted && !notificationRequested) {
            notificationRequested = true
            access.requestNotifications()
        }
    }

    Surface(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationX = size.width * backProgress * 0.08f
                scaleX = 1f - backProgress * 0.02f
                scaleY = 1f - backProgress * 0.02f
                alpha = 1f - backProgress * 0.08f
            },
    ) {
        when (screen) {
            AppScreen.HOME -> HomeScreen(
                schedules = schedules,
                timer = timer,
                settings = settings,
                systemAccess = access,
                onAdd = { showAddChoice = true },
                onEdit = {
                    editorSchedule = it
                    screen = AppScreen.SCHEDULE_EDITOR
                },
                onToggle = viewModel::toggle,
                onDelete = viewModel::delete,
                onCancelTimer = viewModel::cancelTimer,
                onToggleAutomation = viewModel::setAutomationEnabled,
                onSettings = { screen = AppScreen.SETTINGS },
            )
            AppScreen.SCHEDULE_EDITOR -> ScheduleEditorScreen(
                existing = editorSchedule,
                use24HourTime = settings.use24HourTime,
                systemAccess = access,
                onBack = ::navigateHome,
                onSave = {
                    viewModel.save(it)
                    navigateHome()
                },
            )
            AppScreen.TIMER -> TimerScreen(
                systemAccess = access,
                onBack = ::navigateHome,
                onStart = { duration, mode ->
                    viewModel.startTimer(duration, mode)
                    navigateHome()
                },
            )
            AppScreen.SETTINGS -> SettingsScreen(
                settings = settings,
                systemAccess = access,
                onBack = ::navigateHome,
                onAutomationEnabled = viewModel::setAutomationEnabled,
                onUse24HourTime = viewModel::setUse24HourTime,
            )
        }
    }

    if (showAddChoice) {
        AlertDialog(
            onDismissRequest = { showAddChoice = false },
            title = { Text("Create automation") },
            text = { Text("Use a repeating schedule or start a one-time timer.") },
            confirmButton = {
                TextButton(onClick = {
                    showAddChoice = false
                    editorSchedule = null
                    screen = AppScreen.SCHEDULE_EDITOR
                }) { Text("Schedule") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAddChoice = false
                    screen = AppScreen.TIMER
                }) { Text("Timer") }
            },
        )
    }

    if (!permissionSetupDismissed && (!access.exactAlarmGranted || !access.dndGranted)) {
        AlertDialog(
            onDismissRequest = { permissionSetupDismissed = true },
            title = { Text("Finish setup") },
            text = {
                Text("Smart Ringer needs exact alarm access to run on time and ringer policy access for Silent and Do Not Disturb. Grant both in Android settings.")
            },
            confirmButton = {
                TextButton(onClick = if (!access.exactAlarmGranted) access.requestExactAlarm else access.requestDnd) {
                    Text(if (!access.exactAlarmGranted) "Allow exact alarms" else "Allow ringer access")
                }
            },
            dismissButton = {
                TextButton(onClick = { permissionSetupDismissed = true }) { Text("Later") }
            },
        )
    }
}

private enum class AppScreen { HOME, SCHEDULE_EDITOR, TIMER, SETTINGS }

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
    val powerManager = context.getSystemService(PowerManager::class.java)
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { refreshToken++ }
    return SystemAccess(
        exactAlarmGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms(),
        dndGranted = notificationManager.isNotificationPolicyAccessGranted,
        notificationGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
        batteryOptimizationExempt = powerManager.isIgnoringBatteryOptimizations(context.packageName),
        requestExactAlarm = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = android.net.Uri.parse("package:${context.packageName}")
                })
            }
        },
        requestDnd = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) },
        requestNotifications = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        },
        requestAddTile = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val activity = context as? Activity
                activity?.getSystemService(StatusBarManager::class.java)?.requestAddTileService(
                    ComponentName(context, AutomationTileService::class.java),
                    "Smart Ringer",
                    Icon.createWithResource(context, R.drawable.ic_notification),
                    context.mainExecutor,
                ) { }
            }
        },
        requestBatterySettings = {
            context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        },
    )
}

internal data class SystemAccess(
    val exactAlarmGranted: Boolean,
    val dndGranted: Boolean,
    val notificationGranted: Boolean,
    val batteryOptimizationExempt: Boolean,
    val requestExactAlarm: () -> Unit,
    val requestDnd: () -> Unit,
    val requestNotifications: () -> Unit,
    val requestAddTile: () -> Unit,
    val requestBatterySettings: () -> Unit,
)
