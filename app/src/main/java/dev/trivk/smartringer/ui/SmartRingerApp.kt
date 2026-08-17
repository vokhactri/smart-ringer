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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.trivk.smartringer.MainViewModel
import dev.trivk.smartringer.model.RingerMode
import dev.trivk.smartringer.model.RingerSchedule
import dev.trivk.smartringer.model.RingerTimer
import dev.trivk.smartringer.R
import dev.trivk.smartringer.schedule.AutomationTileService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

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
    val backProgress = remember { Animatable(0f) }
    var backSwipeEdge by remember { mutableIntStateOf(BackEventCompat.EDGE_LEFT) }
    var navigationMotion by remember { mutableStateOf(NavigationMotion.INSTANT) }
    val scope = rememberCoroutineScope()

    fun navigateHome(motion: NavigationMotion = NavigationMotion.BACK) {
        navigationMotion = motion
        screen = AppScreen.HOME
        editorSchedule = null
    }

    fun navigateTo(destination: AppScreen) {
        navigationMotion = NavigationMotion.FORWARD
        screen = destination
    }

    val homeContent: @Composable () -> Unit = {
        HomeScreen(
            schedules = schedules,
            timer = timer,
            settings = settings,
            systemAccess = access,
            onAdd = { showAddChoice = true },
            onEdit = {
                editorSchedule = it
                navigateTo(AppScreen.SCHEDULE_EDITOR)
            },
            onToggle = viewModel::toggle,
            onDelete = viewModel::delete,
            onCancelTimer = viewModel::cancelTimer,
            onToggleAutomation = viewModel::setAutomationEnabled,
            onSettings = { navigateTo(AppScreen.SETTINGS) },
        )
    }

    PredictiveBackHandler(enabled = screen != AppScreen.HOME) { progress ->
        var visibleProgressEvents = 0
        try {
            progress.collect { backEvent ->
                if (backEvent.progress > 0.01f && backEvent.progress < 0.99f) {
                    visibleProgressEvents++
                }
                backProgress.snapTo(backEvent.progress)
                backSwipeEdge = backEvent.swipeEdge
            }
            val completedInteractively = visibleProgressEvents >= MinPredictiveProgressEvents
            if (completedInteractively) {
                backProgress.animateTo(
                    1f,
                    spring(
                        dampingRatio = NavigationSpringDampingRatio,
                        stiffness = NavigationSpringStiffness,
                    ),
                )
            }
            navigateHome(
                if (completedInteractively) {
                    NavigationMotion.INSTANT
                } else {
                    NavigationMotion.BACK
                },
            )
            backProgress.snapTo(0f)
        } catch (cancellation: CancellationException) {
            // A cancelled back gesture must leave the current screen intact.
            scope.launch {
                backProgress.animateTo(
                    0f,
                    spring(
                        dampingRatio = NavigationSpringDampingRatio,
                        stiffness = NavigationSpringStiffness,
                    ),
                )
            }
            throw cancellation
        }
    }

    LaunchedEffect(access.notificationGranted) {
        if (!access.notificationGranted && !notificationRequested) {
            notificationRequested = true
            access.requestNotifications()
        }
    }

    Box(Modifier.fillMaxSize()) {
        if (screen != AppScreen.HOME) {
            Surface(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val progress = backProgress.value.coerceIn(0f, 1f)
                        val direction = if (backSwipeEdge == BackEventCompat.EDGE_LEFT) 1f else -1f
                        translationX = -direction * size.width * BackgroundParallaxFraction * (1f - progress)
                    },
            ) { homeContent() }
        }

        Surface(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    if (screen != AppScreen.HOME) {
                        val progress = backProgress.value.coerceIn(0f, 1f)
                        val direction = if (backSwipeEdge == BackEventCompat.EDGE_LEFT) 1f else -1f
                        translationX = size.width * progress * direction
                        shape = RoundedCornerShape(PredictiveBackCornerRadius * progress)
                        clip = progress > 0f
                        shadowElevation = PredictiveBackShadowElevation.toPx() * progress
                    }
                },
        ) {
            AnimatedContent(
                targetState = screen,
                transitionSpec = { navigationTransition(navigationMotion) },
                label = "app navigation",
            ) { destination ->
                when (destination) {
                    AppScreen.HOME -> homeContent()
                    AppScreen.SCHEDULE_EDITOR -> ScheduleEditorScreen(
                        existing = editorSchedule,
                        use24HourTime = settings.use24HourTime,
                        systemAccess = access,
                        onBack = { navigateHome() },
                        onSave = {
                            viewModel.save(it)
                            navigateHome()
                        },
                    )
                    AppScreen.TIMER -> TimerScreen(
                        systemAccess = access,
                        onBack = { navigateHome() },
                        onStart = { duration, mode ->
                            viewModel.startTimer(duration, mode)
                            navigateHome()
                        },
                    )
                    AppScreen.SETTINGS -> SettingsScreen(
                        settings = settings,
                        systemAccess = access,
                        onBack = { navigateHome() },
                        onAutomationEnabled = viewModel::setAutomationEnabled,
                        onUse24HourTime = viewModel::setUse24HourTime,
                    )
                }
            }
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
                    navigateTo(AppScreen.SCHEDULE_EDITOR)
                }) { Text("Schedule") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAddChoice = false
                    navigateTo(AppScreen.TIMER)
                }) { Text("Timer") }
            },
        )
    }

    val missingExactAlarm = !access.exactAlarmGranted
    val missingDnd = requiresDndAccess(schedules, timer) && !access.dndGranted
    val dismissSetupPrompt = {
        permissionSetupDismissed = true
        viewModel.dismissSetupPrompt()
    }

    if (!permissionSetupDismissed && !settings.setupPromptDismissed && (missingExactAlarm || missingDnd)) {
        AlertDialog(
            onDismissRequest = dismissSetupPrompt,
            title = { Text("Finish setup") },
            text = {
                Text(
                    when {
                        missingExactAlarm && missingDnd ->
                            "Smart Ringer needs exact alarm access to run on time and ringer policy access for Silent and Do Not Disturb. Grant both in Android settings."
                        missingExactAlarm ->
                            "Smart Ringer needs exact alarm access so ringer changes happen at the selected time."
                        else ->
                            "Smart Ringer needs ringer policy access for the schedules that use Silent or Do Not Disturb."
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = if (missingExactAlarm) access.requestExactAlarm else access.requestDnd) {
                    Text(if (missingExactAlarm) "Allow exact alarms" else "Allow ringer access")
                }
            },
            dismissButton = {
                TextButton(onClick = dismissSetupPrompt) { Text("Later") }
            },
        )
    }
}

/**
 * Ringer policy access is only needed to enter Silent or Do Not Disturb — plain Vibrate never
 * requires it, so a user who only schedules Vibrate should never be asked for it.
 */
internal fun requiresDndAccess(schedules: List<RingerSchedule>, timer: RingerTimer?): Boolean =
    schedules.any { it.mode != RingerMode.VIBRATE } || (timer != null && timer.mode != RingerMode.VIBRATE)

private enum class AppScreen { HOME, SCHEDULE_EDITOR, TIMER, SETTINGS }
private enum class NavigationMotion { INSTANT, FORWARD, BACK }

private const val BackgroundParallaxFraction = 0.2f
private const val NavigationSpringDampingRatio = 0.85f
private const val NavigationSpringStiffness = 550f
private const val MinPredictiveProgressEvents = 2
private val PredictiveBackCornerRadius = 24.dp
private val PredictiveBackShadowElevation = 18.dp

private fun navigationTransition(motion: NavigationMotion) = when (motion) {
    NavigationMotion.INSTANT -> EnterTransition.None togetherWith ExitTransition.None
    NavigationMotion.FORWARD ->
        (slideInHorizontally(
            animationSpec = spring(
                dampingRatio = NavigationSpringDampingRatio,
                stiffness = NavigationSpringStiffness,
            ),
            initialOffsetX = { it },
        ) togetherWith slideOutHorizontally(
            animationSpec = spring(
                dampingRatio = NavigationSpringDampingRatio,
                stiffness = NavigationSpringStiffness,
            ),
            targetOffsetX = { -it / 5 },
        )).apply { targetContentZIndex = 1f }
    NavigationMotion.BACK ->
        (slideInHorizontally(
            animationSpec = spring(
                dampingRatio = NavigationSpringDampingRatio,
                stiffness = NavigationSpringStiffness,
            ),
            initialOffsetX = { -it / 5 },
        ) togetherWith slideOutHorizontally(
            animationSpec = spring(
                dampingRatio = NavigationSpringDampingRatio,
                stiffness = NavigationSpringStiffness,
            ),
            targetOffsetX = { it },
        )).apply { targetContentZIndex = -1f }
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
        // Hibernating an "unused" app also force-stops it, which suspends every registered alarm —
        // the one system feature that silently defeats a set-and-forget scheduler.
        unusedAppPauseDisabled = Build.VERSION.SDK_INT < Build.VERSION_CODES.R ||
            context.packageManager.isAutoRevokeWhitelisted,
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
                    Icon.createWithResource(context, R.drawable.ic_qs_tile),
                    context.mainExecutor,
                ) { }
            }
        },
        requestBatterySettings = {
            context.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = android.net.Uri.parse("package:${context.packageName}")
            })
        },
        requestUnusedAppSettings = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                context.startActivity(Intent(Intent.ACTION_AUTO_REVOKE_PERMISSIONS).apply {
                    data = android.net.Uri.parse("package:${context.packageName}")
                })
            }
        },
    )
}

internal data class SystemAccess(
    val exactAlarmGranted: Boolean,
    val dndGranted: Boolean,
    val notificationGranted: Boolean,
    val batteryOptimizationExempt: Boolean,
    val unusedAppPauseDisabled: Boolean,
    val requestExactAlarm: () -> Unit,
    val requestDnd: () -> Unit,
    val requestNotifications: () -> Unit,
    val requestAddTile: () -> Unit,
    val requestBatterySettings: () -> Unit,
    val requestUnusedAppSettings: () -> Unit,
)
