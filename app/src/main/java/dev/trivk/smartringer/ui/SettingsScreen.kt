package dev.trivk.smartringer.ui

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Offset
import dev.trivk.smartringer.model.AppSettings
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreen(
    settings: AppSettings,
    systemAccess: SystemAccess,
    onBack: () -> Unit,
    onAutomationEnabled: (Boolean) -> Unit,
    onUse24HourTime: (Boolean) -> Unit,
    onCustomThemeColor: (Int?) -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showTileHelp by remember { mutableStateOf(false) }
    var showColorPicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                SettingsSection("Automation") {
                    AutomationSettingRow(
                        icon = Icons.Default.AutoMode,
                        title = "Enable automation",
                        body = "Run schedules, timers, recovery checks, and the Quick Settings tile.",
                        checked = settings.automationEnabled,
                        onChange = onAutomationEnabled,
                    )
                }
            }
            item {
                SettingsSection("Time format") {
                    TimeFormatSelector(
                        use24HourTime = settings.use24HourTime,
                        onUse24HourTime = onUse24HourTime,
                    )
                }
            }
            item {
                SettingsSection("Appearance") {
                    AppColorRow(
                        customColorArgb = settings.customThemeColorArgb,
                        onClick = { showColorPicker = true },
                    )
                }
            }
            item {
                SettingsSection("System access") {
                    PermissionRow(
                        icon = Icons.Default.Notifications,
                        title = "Notifications",
                        granted = systemAccess.notificationGranted,
                        grantedText = "Allowed",
                        missingText = "Not allowed",
                        onClick = systemAccess.requestNotifications,
                    )
                    PermissionRow(
                        icon = Icons.Default.Schedule,
                        title = "Exact alarms",
                        granted = systemAccess.exactAlarmGranted,
                        grantedText = "Allowed",
                        missingText = "Not allowed",
                        onClick = systemAccess.requestExactAlarm,
                    )
                    PermissionRow(
                        icon = Icons.Default.SettingsRemote,
                        title = "Ringer policy access",
                        granted = systemAccess.dndGranted,
                        grantedText = "Allowed",
                        missingText = "Not allowed",
                        onClick = systemAccess.requestDnd,
                    )
                    PermissionRow(
                        icon = Icons.Default.BatterySaver,
                        title = "Battery optimization",
                        granted = systemAccess.batteryOptimizationExempt,
                        grantedText = "Unrestricted",
                        missingText = "Restricted",
                        onClick = systemAccess.requestBatterySettings,
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        PermissionRow(
                            icon = Icons.Default.PauseCircle,
                            title = "Manage unused apps",
                            granted = systemAccess.unusedAppPauseDisabled,
                            grantedText = "Off",
                            missingText = "On",
                            onClick = systemAccess.requestUnusedAppSettings,
                        )
                    }
                }
            }
            item {
                SettingsSection("Quick Settings") {
                    QuickSettingsRow(
                        onAddTile = {
                            systemAccess.requestAddTile { result ->
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        when (result) {
                                            TileAddResult.ADDED -> "Quick Settings tile added"
                                            TileAddResult.ALREADY_ADDED -> "Quick Settings tile is already added"
                                            TileAddResult.NOT_ADDED -> "Quick Settings tile wasn't added"
                                        },
                                    )
                                }
                            }
                        },
                        onHowToAdd = { showTileHelp = true },
                    )
                }
            }
        }
    }

    if (showTileHelp) {
        AlertDialog(
            onDismissRequest = { showTileHelp = false },
            title = { Text("Add the Quick Settings tile") },
            text = {
                Text("Open Quick Settings, tap Edit, then drag Smart Ringer into the active tiles.")
            },
            confirmButton = {
                TextButton(onClick = { showTileHelp = false }) { Text("Got it") }
            },
        )
    }
    if (showColorPicker) {
        AppColorDialog(
            currentColorArgb = settings.customThemeColorArgb,
            onDismiss = { showColorPicker = false },
            onSelect = {
                onCustomThemeColor(it)
                showColorPicker = false
            },
        )
    }
}

@Composable
private fun AppColorRow(customColorArgb: Int?, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Default.Palette, contentDescription = null)
            Column(Modifier.weight(1f)) {
                Text("App color", fontWeight = FontWeight.SemiBold)
                Text(
                    if (customColorArgb == null) "System colors" else formatThemeColor(customColorArgb),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Box(
                Modifier
                    .size(30.dp)
                    .background(
                        customColorArgb?.let(::Color) ?: MaterialTheme.colorScheme.primary,
                        CircleShape,
                    )
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
            )
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    }
}

@Composable
private fun AppColorDialog(
    currentColorArgb: Int?,
    onDismiss: () -> Unit,
    onSelect: (Int?) -> Unit,
) {
    var hexText by remember(currentColorArgb) {
        mutableStateOf(formatThemeColor(currentColorArgb ?: ThemeColorPresets.first()))
    }
    val parsedColor = parseThemeColor(hexText)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("App color") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("Pick an accent color. It adapts automatically to light and dark mode.")
                TextButton(onClick = { onSelect(null) }) {
                    Text("Use system colors")
                }
                Text("Suggested colors", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    ThemeColorPresets.forEach { argb ->
                        val selected = parsedColor == argb
                        Box(
                            Modifier
                                .size(42.dp)
                                .background(Color(argb), CircleShape)
                                .border(
                                    width = if (selected) 3.dp else 1.dp,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.onSurface
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant
                                    },
                                    shape = CircleShape,
                                )
                                .clickable { hexText = formatThemeColor(argb) },
                        )
                    }
                }
                Text("Color picker", style = MaterialTheme.typography.labelLarge)
                VisualColorPicker(
                    colorArgb = parsedColor ?: ThemeColorPresets.first(),
                    onColorChange = { hexText = formatThemeColor(it) },
                )
                OutlinedTextField(
                    value = hexText,
                    onValueChange = { hexText = it.take(7) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Hex color") },
                    supportingText = {
                        Text(if (parsedColor == null) "Enter a color like #4F5E92" else "Custom accent color")
                    },
                    isError = parsedColor == null,
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = parsedColor != null,
                onClick = { parsedColor?.let(onSelect) },
            ) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun VisualColorPicker(colorArgb: Int, onColorChange: (Int) -> Unit) {
    val hsv = remember(colorArgb) {
        FloatArray(3).also { android.graphics.Color.colorToHSV(colorArgb, it) }
    }
    val hueColor = Color(android.graphics.Color.HSVToColor(floatArrayOf(hsv[0], 1f, 1f)))

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .size(width = 320.dp, height = 164.dp)
                .background(Color.White, RoundedCornerShape(12.dp))
                .pointerInput(hsv[0]) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        fun update(position: Offset) {
                            val saturation = (position.x / size.width).coerceIn(0f, 1f)
                            val value = (1f - position.y / size.height).coerceIn(0f, 1f)
                            onColorChange(
                                android.graphics.Color.HSVToColor(floatArrayOf(hsv[0], saturation, value)),
                            )
                        }
                        update(down.position)
                        do {
                            val event = awaitPointerEvent()
                            event.changes.firstOrNull { it.pressed }?.let { change ->
                                update(change.position)
                                change.consume()
                            }
                        } while (event.changes.any { it.pressed })
                    }
                },
        ) {
            drawRect(Brush.horizontalGradient(listOf(Color.White, hueColor)))
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
            val marker = Offset(hsv[1] * size.width, (1f - hsv[2]) * size.height)
            drawCircle(Color.Black.copy(alpha = 0.65f), radius = 11.dp.toPx(), center = marker, style = Stroke(2.dp.toPx()))
            drawCircle(Color.White, radius = 9.dp.toPx(), center = marker, style = Stroke(3.dp.toPx()))
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .size(width = 320.dp, height = 28.dp)
                .pointerInput(hsv[1], hsv[2]) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        fun update(position: Offset) {
                            val hue = (position.x / size.width).coerceIn(0f, 1f) * 360f
                            onColorChange(
                                android.graphics.Color.HSVToColor(floatArrayOf(hue, hsv[1], hsv[2])),
                            )
                        }
                        update(down.position)
                        do {
                            val event = awaitPointerEvent()
                            event.changes.firstOrNull { it.pressed }?.let { change ->
                                update(change.position)
                                change.consume()
                            }
                        } while (event.changes.any { it.pressed })
                    }
                },
        ) {
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    listOf(
                        Color.Red,
                        Color.Yellow,
                        Color.Green,
                        Color.Cyan,
                        Color.Blue,
                        Color.Magenta,
                        Color.Red,
                    ),
                ),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f),
            )
            val x = (hsv[0] / 360f) * size.width
            drawCircle(Color.Black.copy(alpha = 0.65f), radius = 11.dp.toPx(), center = Offset(x, size.height / 2f), style = Stroke(2.dp.toPx()))
            drawCircle(Color.White, radius = 9.dp.toPx(), center = Offset(x, size.height / 2f), style = Stroke(3.dp.toPx()))
        }
    }
}

internal fun parseThemeColor(input: String): Int? {
    val hex = input.trim().removePrefix("#")
    if (hex.length != 6 || hex.any { it !in "0123456789abcdefABCDEF" }) return null
    return (hex.toLong(16).toInt() or 0xFF000000.toInt())
}

private fun formatThemeColor(argb: Int) = "#%06X".format(argb and 0x00FFFFFF)

private val ThemeColorPresets = listOf(
    0xFF4F5E92.toInt(),
    0xFF006C4C.toInt(),
    0xFF8C4A60.toInt(),
    0xFF8B5000.toInt(),
    0xFF6750A4.toInt(),
)

@Composable
private fun QuickSettingsRow(onAddTile: () -> Unit, onHowToAdd: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Default.Apps, contentDescription = null)
            Column(Modifier.weight(1f)) {
                Text("Smart Ringer tile", fontWeight = FontWeight.SemiBold)
                Text("Toggle all automation from Quick Settings.", style = MaterialTheme.typography.bodyMedium)
            }
            TextButton(
                onClick = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    onAddTile
                } else {
                    onHowToAdd
                },
            ) {
                Text(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) "Add" else "How to add")
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        content()
    }
}

@Composable
private fun AutomationSettingRow(
    icon: ImageVector,
    title: String,
    body: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(icon, contentDescription = null)
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(body, style = MaterialTheme.typography.bodyMedium)
            }
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}

@Composable
private fun TimeFormatSelector(use24HourTime: Boolean, onUse24HourTime: (Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf(false to "12-hour", true to "24-hour").forEachIndexed { index, (value, label) ->
                SegmentedButton(
                    modifier = Modifier.weight(1f),
                    selected = use24HourTime == value,
                    onClick = { onUse24HourTime(value) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = 2),
                    label = { Text(label) },
                )
            }
        }
        Text(
            text = "Example: ${if (use24HourTime) "21:30" else "9:30 PM"}",
            modifier = Modifier.padding(horizontal = 4.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * One visual grammar for every row: the title on the left, the current state on the right, and a
 * trailing icon that says whether the state is fine (check) or needs a trip to system settings
 * (chevron). Accent colour is spent only on rows that need attention, so scanning the section
 * surfaces the gaps instead of the things already done.
 */
@Composable
private fun PermissionRow(
    icon: ImageVector,
    title: String,
    granted: Boolean,
    grantedText: String,
    missingText: String,
    onClick: () -> Unit,
) {
    val accent = if (granted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(icon, contentDescription = null, tint = accent)
            Text(title, modifier = Modifier.weight(1f))
            Text(
                if (granted) grantedText else missingText,
                style = MaterialTheme.typography.bodyMedium,
                color = accent,
                fontWeight = if (granted) FontWeight.Normal else FontWeight.SemiBold,
            )
            Icon(
                if (granted) Icons.Default.CheckCircle else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
