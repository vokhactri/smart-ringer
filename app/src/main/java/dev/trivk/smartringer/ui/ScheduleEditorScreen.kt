package dev.trivk.smartringer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.trivk.smartringer.model.RingerMode
import dev.trivk.smartringer.model.RingerSchedule
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleEditorScreen(
    existing: RingerSchedule?,
    onBack: () -> Unit,
    onSave: (RingerSchedule) -> Unit,
) {
    var name by remember(existing?.id) { mutableStateOf(existing?.name.orEmpty()) }
    var days by remember(existing?.id) { mutableStateOf(existing?.days ?: emptySet()) }
    var mode by remember(existing?.id) { mutableStateOf(existing?.mode ?: RingerMode.VIBRATE) }
    var startTime by remember(existing?.id) { mutableStateOf(existing?.startTime ?: LocalTime.of(9, 0)) }
    var endTime by remember(existing?.id) { mutableStateOf(existing?.endTime ?: LocalTime.of(17, 0)) }
    var editingTime by remember { mutableStateOf<TimeField?>(null) }
    val access = rememberSystemAccess()
    val valid = name.isNotBlank() && days.isNotEmpty() && startTime != endTime &&
        (mode == RingerMode.VIBRATE || access.dndGranted)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "Add schedule" else "Edit schedule") },
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
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Schedule name") },
                    singleLine = true,
                )
            }
            item { TimingNote() }
            item {
                Section(title = "Days of week", subtitle = "Select one or more days") {
                    DaySelector(selected = days, onChange = { days = it })
                }
            }
            item {
                Section(title = "Ringer mode") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        ModeCard(
                            icon = Icons.Default.Vibration,
                            title = "Vibrate",
                            body = "Phone vibrates for calls and notifications",
                            selected = mode == RingerMode.VIBRATE,
                            onClick = { mode = RingerMode.VIBRATE },
                        )
                        ModeCard(
                            icon = Icons.Default.DoNotDisturbOn,
                            title = "Do Not Disturb",
                            body = "Silences calls and notifications",
                            selected = mode == RingerMode.DO_NOT_DISTURB,
                            onClick = { mode = RingerMode.DO_NOT_DISTURB },
                        )
                        ModeCard(
                            icon = Icons.AutoMirrored.Filled.VolumeOff,
                            title = "Silent",
                            body = "Completely silent — no sound or vibration",
                            selected = mode == RingerMode.SILENT,
                            onClick = { mode = RingerMode.SILENT },
                        )
                    }
                }
            }
            if (mode != RingerMode.VIBRATE && !access.dndGranted) {
                item {
                    AccessPrompt(
                        text = "Allow ringer policy access before saving Silent or Do Not Disturb schedules.",
                        onClick = access.requestDnd,
                    )
                }
            }
            item {
                Section(title = "Start time") {
                    TimeButton(startTime) { editingTime = TimeField.START }
                }
            }
            item {
                Section(title = "End time") {
                    TimeButton(endTime) { editingTime = TimeField.END }
                    if (startTime == endTime) {
                        Text(
                            "Start and end times must be different.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            item {
                Button(
                    onClick = {
                        onSave(
                            RingerSchedule(
                                id = existing?.id ?: java.util.UUID.randomUUID().toString(),
                                name = name.trim(),
                                days = days,
                                mode = mode,
                                startTime = startTime,
                                endTime = endTime,
                                enabled = existing?.enabled ?: true,
                            ),
                        )
                    },
                    enabled = valid,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                ) {
                    Text(if (existing == null) "Save schedule" else "Save changes")
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    editingTime?.let { field ->
        val current = if (field == TimeField.START) startTime else endTime
        TimePickerDialog(
            title = if (field == TimeField.START) "Start time" else "End time",
            initial = current,
            onDismiss = { editingTime = null },
            onConfirm = {
                if (field == TimeField.START) startTime = it else endTime = it
                editingTime = null
            },
        )
    }
}

@Composable
private fun TimingNote() {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column {
                Text("Important timing note", fontWeight = FontWeight.SemiBold)
                Text(
                    "Allow exact alarms for reliable timing. Without access, Android may delay ringer changes to save battery.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun Section(title: String, subtitle: String? = null, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        subtitle?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        content()
    }
}

@Composable
private fun DaySelector(selected: Set<DayOfWeek>, onChange: (Set<DayOfWeek>) -> Unit) {
    val weekdays = DayOfWeek.entries.take(5).toSet()
    val weekend = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PresetChip("Weekdays", selected == weekdays, Modifier.weight(1f)) { onChange(weekdays) }
            PresetChip("Weekend", selected == weekend, Modifier.weight(1f)) { onChange(weekend) }
            PresetChip("Every day", selected.size == 7, Modifier.weight(1f)) { onChange(DayOfWeek.entries.toSet()) }
        }
        DayOfWeek.entries.chunked(4).forEach { rowDays ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowDays.forEach { day ->
                    FilterChip(
                        selected = day in selected,
                        onClick = {
                            onChange(if (day in selected) selected - day else selected + day)
                        },
                        label = { Text(day.name.take(3).lowercase().replaceFirstChar(Char::uppercase)) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(4 - rowDays.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun PresetChip(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        modifier = modifier,
    )
}

@Composable
private fun ModeCard(
    icon: ImageVector,
    title: String,
    body: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(icon, contentDescription = null, tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            RadioButton(selected = selected, onClick = onClick)
        }
    }
}

@Composable
private fun TimeButton(time: LocalTime, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp),
        shape = RoundedCornerShape(20.dp),
    ) {
        Text(
            time.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)),
            style = MaterialTheme.typography.headlineMedium,
        )
    }
}

@Composable
private fun AccessPrompt(text: String, onClick: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onClick) { Text("Allow") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    title: String,
    initial: LocalTime,
    onDismiss: () -> Unit,
    onConfirm: (LocalTime) -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = false,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TimeInput(state = state) },
        confirmButton = {
            TextButton(onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }) {
                Text("OK")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private enum class TimeField { START, END }
