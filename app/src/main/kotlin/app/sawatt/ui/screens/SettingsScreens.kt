package app.sawatt.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.sawatt.AppContainer
import app.sawatt.core.model.Gender
import app.sawatt.core.model.UnitSystem
import app.sawatt.core.ride.RideMath
import app.sawatt.ui.components.SawattTopBar
import app.sawatt.ui.components.SectionCard
import java.time.LocalDate
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(container: AppContainer, onBack: () -> Unit) {
    val profile by container.settings.profile.collectAsStateWithLifecycle()
    val update = container.settings::updateProfile

    Scaffold(topBar = { SawattTopBar("Profile", onBack) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Choice("Gender", listOf("Male", "Female"), if (profile.gender == Gender.MALE) 0 else 1) { i ->
                update { it.copy(gender = if (i == 0) Gender.MALE else Gender.FEMALE) }
            }
            Choice("Units", listOf("Metric", "Imperial"), profile.units.ordinal) { i ->
                update { it.copy(units = UnitSystem.entries[i]) }
            }
            TextSetting("Date of birth (YYYY-MM-DD)", profile.birthDate.toString(), KeyboardType.Text) { text ->
                runCatching { LocalDate.parse(text) }.getOrNull()?.let { d -> update { it.copy(birthDate = d) } }
            }
            NumberSetting("Weight (kg)", profile.weightKg.roundToInt()) { v -> if (v in 30..300) update { it.copy(weightKg = v.toDouble()) } }
            NumberSetting("Height (cm)", profile.heightCm.roundToInt()) { v -> if (v in 120..250) update { it.copy(heightCm = v.toDouble()) } }
            Choice("Cardio training per week", listOf("1-3 h", "3-5 h", "5-8 h", "8+ h"), profile.trainingLevel) { i ->
                update { it.copy(trainingLevel = i) }
            }
            NumberSetting(
                "FTP (W) — 0 = estimate",
                profile.ftpW,
                supporting = "Estimated: ${RideMath.estimatedFtp(profile).roundToInt()} W",
            ) { v -> if (v in 0..999) update { it.copy(ftpW = v) } }
            NumberSetting(
                "Max heart rate (bpm) — 0 = estimate",
                profile.maxHr,
                supporting = "Estimated: ${RideMath.estimatedMaxHr(RideMath.age(profile.birthDate)).roundToInt()} bpm",
            ) { v -> if (v in 0..250) update { it.copy(maxHr = v) } }
        }
    }
}

@Composable
fun SettingsScreen(container: AppContainer, onBack: () -> Unit) {
    val app by container.settings.app.collectAsStateWithLifecycle()
    val bike by container.ble.bike.collectAsStateWithLifecycle()
    val update = container.settings::updateApp

    Scaffold(topBar = { SawattTopBar("Settings", onBack) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ToggleSetting("Beep at interval transitions", app.transitionBeep) { v -> update { it.copy(transitionBeep = v) } }
            ToggleSetting("Keep screen on during workouts", app.keepScreenOn) { v -> update { it.copy(keepScreenOn = v) } }
            ToggleSetting(
                "Simulated bike (demo mode)",
                app.simulateBike,
                "Generates fake power, cadence and heart rate — useful to try the app without the bike.",
            ) { v -> update { it.copy(simulateBike = v) } }

            SectionCard {
                Text("About", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Sawatt: structured interval training for indoor bikes and power meters. " +
                        "It reads the bike over the standard Bluetooth Cycling Power service and never writes to it.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text("Bike firmware: ${bike?.firmware ?: "unknown (connect the bike)"}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun ToggleSetting(title: String, checked: Boolean, subtitle: String? = null, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Choice(title: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(bottom = 4.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { i, label ->
                SegmentedButton(
                    selected = i == selected,
                    onClick = { onSelect(i) },
                    shape = SegmentedButtonDefaults.itemShape(i, options.size),
                ) { Text(label, maxLines = 1) }
            }
        }
    }
}

@Composable
private fun NumberSetting(label: String, value: Int, supporting: String? = null, onValid: (Int) -> Unit) {
    TextSetting(label, value.toString(), KeyboardType.Number, supporting) { text -> text.toIntOrNull()?.let(onValid) }
}

@Composable
private fun TextSetting(
    label: String,
    value: String,
    keyboard: KeyboardType,
    supporting: String? = null,
    onChange: (String) -> Unit,
) {
    // Local text so partial input ("", "7") is allowed; valid values are pushed up as they are typed.
    // Keyed on the stored value so it refreshes when settings finish loading from disk.
    var text by remember(value) { mutableStateOf(value) }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it; onChange(it) },
        label = { Text(label) },
        supportingText = supporting?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
fun ComingLaterScreen(onBack: () -> Unit) {
    Scaffold(topBar = { SawattTopBar("Coming later", onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(
                "FTP test (5 min)" to "Ride 5 minutes as hard as you can; the average sets your FTP.",
                "VO2max test" to "Guided ramp at 70 rpm with resistance cues.",
                "Levels & medals" to "Levels and weekly bronze / silver / gold challenges.",
                "Strava upload" to "Send your workouts to Strava.",
            ).forEach { (title, text) ->
                SectionCard {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(text, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
