package dev.trivk.smartringer.ui

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.trivk.smartringer.model.AppSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreen(
    settings: AppSettings,
    systemAccess: SystemAccess,
    onBack: () -> Unit,
    onAutomationEnabled: (Boolean) -> Unit,
    onUse24HourTime: (Boolean) -> Unit,
) {
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
                    SettingsToggle(
                        icon = Icons.Default.Timer,
                        title = "Enable automation",
                        body = "Run schedules, timers, recovery checks, and the Quick Settings tile.",
                        checked = settings.automationEnabled,
                        onChange = onAutomationEnabled,
                    )
                }
            }
            item {
                SettingsSection("Time format") {
                    TimeFormatChoice("12-hour", "9:30 PM", selected = !settings.use24HourTime) {
                        onUse24HourTime(false)
                    }
                    TimeFormatChoice("24-hour", "21:30", selected = settings.use24HourTime) {
                        onUse24HourTime(true)
                    }
                }
            }
            item {
                SettingsSection("System access") {
                    PermissionRow(
                        icon = Icons.Default.Notifications,
                        title = "Notifications",
                        granted = systemAccess.notificationGranted,
                        onClick = systemAccess.requestNotifications,
                    )
                    PermissionRow(
                        icon = Icons.Default.Schedule,
                        title = "Exact alarms",
                        granted = systemAccess.exactAlarmGranted,
                        onClick = systemAccess.requestExactAlarm,
                    )
                    PermissionRow(
                        icon = Icons.Default.SettingsRemote,
                        title = "Ringer policy access",
                        granted = systemAccess.dndGranted,
                        onClick = systemAccess.requestDnd,
                    )
                    PermissionRow(
                        icon = Icons.Default.BatterySaver,
                        title = "Battery optimization",
                        granted = systemAccess.batteryOptimizationExempt,
                        grantedText = "Unrestricted",
                        missingText = "Review",
                        onClick = systemAccess.requestBatterySettings,
                    )
                }
            }
            item {
                SettingsSection("Quick Settings") {
                    QuickSettingsRow(onAddTile = systemAccess.requestAddTile)
                }
            }
        }
    }
}

@Composable
private fun QuickSettingsRow(onAddTile: () -> Unit) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
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
                Text(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        "Toggle all automation without opening the app."
                    } else {
                        "Open Quick Settings, tap Edit, then drag Smart Ringer into the active tiles."
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                TextButton(onClick = onAddTile) {
                    Text("Add tile")
                }
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
private fun SettingsToggle(
    icon: ImageVector,
    title: String,
    body: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Card(shape = RoundedCornerShape(18.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(icon, contentDescription = null)
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(body, style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}

@Composable
private fun TimeFormatChoice(label: String, example: String, selected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = onClick)
            Text(label, modifier = Modifier.weight(1f))
            Text(example, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun PermissionRow(
    icon: ImageVector,
    title: String,
    granted: Boolean,
    grantedText: String = "Allowed",
    missingText: String = "Allow",
    onClick: () -> Unit,
) {
    Card {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(icon, contentDescription = null)
            Text(title, modifier = Modifier.weight(1f))
            if (granted) {
                Text(grantedText, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            } else {
                TextButton(onClick = onClick) { Text(missingText) }
            }
        }
    }
}
