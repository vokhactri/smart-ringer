package dev.trivk.smartringer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.trivk.smartringer.model.RingerMode
import java.time.Duration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimerScreen(
    systemAccess: SystemAccess,
    onBack: () -> Unit,
    onStart: (Duration, RingerMode) -> Unit,
) {
    var totalMinutes by remember { mutableIntStateOf(30) }
    var customMinutes by remember { mutableStateOf("30") }
    var mode by remember { mutableStateOf(RingerMode.VIBRATE) }
    val policyGranted = mode == RingerMode.VIBRATE || systemAccess.dndGranted

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Start timer") },
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
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Duration", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(15, 30, 60, 120).forEach { minutes ->
                            FilterChip(
                                selected = totalMinutes == minutes,
                                onClick = {
                                    totalMinutes = minutes
                                    customMinutes = minutes.toString()
                                },
                                label = { Text(if (minutes < 60) "${minutes}m" else "${minutes / 60}h") },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    OutlinedTextField(
                        value = customMinutes,
                        onValueChange = { value ->
                            customMinutes = value.filter(Char::isDigit).take(4)
                            totalMinutes = customMinutes.toIntOrNull() ?: 0
                        },
                        label = { Text("Custom minutes") },
                        suffix = { Text("min") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        supportingText = { Text("1 minute to 24 hours") },
                    )
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Ringer mode", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    TimerModeCard(Icons.Default.Vibration, "Vibrate", mode == RingerMode.VIBRATE) {
                        mode = RingerMode.VIBRATE
                    }
                    TimerModeCard(Icons.Default.DoNotDisturbOn, "Do Not Disturb", mode == RingerMode.DO_NOT_DISTURB) {
                        mode = RingerMode.DO_NOT_DISTURB
                    }
                    TimerModeCard(Icons.AutoMirrored.Filled.VolumeOff, "Silent", mode == RingerMode.SILENT) {
                        mode = RingerMode.SILENT
                    }
                }
            }
            if (!policyGranted) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Ringer policy access is required for this mode.", modifier = Modifier.weight(1f))
                            TextButton(onClick = systemAccess.requestDnd) { Text("Allow") }
                        }
                    }
                }
            }
            item {
                Button(
                    onClick = { onStart(Duration.ofMinutes(totalMinutes.toLong()), mode) },
                    enabled = totalMinutes in 1..1_440 && policyGranted,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Start timer") }
            }
        }
    }
}

@Composable
private fun TimerModeCard(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(icon, contentDescription = null)
            Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            RadioButton(selected = selected, onClick = onClick)
        }
    }
}
