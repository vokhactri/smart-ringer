package dev.trivk.smartringer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.trivk.smartringer.model.RingerMode
import dev.trivk.smartringer.model.RingerSchedule
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    schedules: List<RingerSchedule>,
    onAdd: () -> Unit,
    onEdit: (RingerSchedule) -> Unit,
    onToggle: (RingerSchedule) -> Unit,
    onDelete: (RingerSchedule) -> Unit,
) {
    var showHelp by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<RingerSchedule?>(null) }
    val access = rememberSystemAccess()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Smart Ringer") },
                actions = {
                    IconButton(onClick = { showHelp = true }) {
                        Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = "Help")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add schedule") },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!access.exactAlarmGranted) {
                item {
                    AccessCard(
                        title = "Allow exact alarms",
                        body = "Required so ringer changes happen at the selected time.",
                        button = "Open settings",
                        onClick = access.requestExactAlarm,
                    )
                }
            }
            if (schedules.any { it.mode != RingerMode.VIBRATE } && !access.dndGranted) {
                item {
                    AccessCard(
                        title = "Allow ringer policy access",
                        body = "Required by schedules that use Silent or Do Not Disturb mode.",
                        button = "Grant access",
                        onClick = access.requestDnd,
                    )
                }
            }
            if (schedules.isEmpty()) {
                item { EmptySchedules(onAdd) }
            } else {
                items(schedules, key = RingerSchedule::id) { schedule ->
                    ScheduleCard(
                        schedule = schedule,
                        onToggle = { onToggle(schedule) },
                        onEdit = { onEdit(schedule) },
                        onDelete = { pendingDelete = schedule },
                    )
                }
            }
        }
    }

    if (showHelp) {
        AlertDialog(
            onDismissRequest = { showHelp = false },
            icon = { Icon(Icons.Default.NotificationsActive, contentDescription = null) },
            title = { Text("How Smart Ringer works") },
            text = {
                Text("Create a schedule, choose its days and ringer mode, then keep it enabled. Android may delay inexact alarms, so allow exact alarms for reliable timing.")
            },
            confirmButton = { TextButton(onClick = { showHelp = false }) { Text("Got it") } },
        )
    }

    pendingDelete?.let { schedule ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete ${schedule.name}?") },
            text = { Text("This schedule and its pending alarms will be removed.") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(schedule)
                    pendingDelete = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ScheduleCard(
    schedule: RingerSchedule,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (schedule.enabled) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    schedule.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(daySummary(schedule.days), color = MaterialTheme.colorScheme.primary)
                Text(
                    "${schedule.startTime.format(timeFormatter)} – ${schedule.endTime.format(timeFormatter)}",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(modeLabel(schedule.mode), style = MaterialTheme.typography.labelLarge)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Switch(checked = schedule.enabled, onCheckedChange = { onToggle() })
                Row {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit ${schedule.name}")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete ${schedule.name}",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptySchedules(onAdd: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(360.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.NotificationsActive,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(16.dp))
            Text("No schedules yet", style = MaterialTheme.typography.headlineSmall)
            Text("Automate your ringer for work, sleep, and more.")
            Spacer(Modifier.height(20.dp))
            FilledTonalButton(onClick = onAdd) { Text("Create first schedule") }
        }
    }
}

@Composable
private fun AccessCard(title: String, body: String, button: String, onClick: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(body, style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onClick, modifier = Modifier.align(Alignment.End)) { Text(button) }
        }
    }
}

private val timeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

internal fun modeLabel(mode: RingerMode) = when (mode) {
    RingerMode.VIBRATE -> "Vibrate"
    RingerMode.DO_NOT_DISTURB -> "Do Not Disturb"
    RingerMode.SILENT -> "Silent"
}

private fun daySummary(days: Set<DayOfWeek>): String = when (days) {
    DayOfWeek.entries.toSet() -> "Every day"
    setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY) -> "Weekdays"
    setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) -> "Weekend"
    else -> DayOfWeek.entries.filter(days::contains).joinToString(" · ") { it.name.take(3).lowercase().replaceFirstChar(Char::uppercase) }
}
