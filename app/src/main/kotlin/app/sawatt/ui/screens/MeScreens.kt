package app.sawatt.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import app.sawatt.AppContainer
import app.sawatt.core.model.Gender
import app.sawatt.core.model.UnitSystem
import app.sawatt.core.ride.RideMath
import app.sawatt.core.trainer.DeviceLink
import app.sawatt.core.trainer.LinkState
import app.sawatt.ui.Routes
import app.sawatt.ui.components.Group
import app.sawatt.ui.components.GroupDivider
import app.sawatt.ui.components.GroupHeader
import app.sawatt.ui.components.GroupRow
import app.sawatt.ui.components.IconTile
import app.sawatt.ui.components.LargeTitle
import app.sawatt.ui.components.Metric
import app.sawatt.ui.components.MetricLabel
import app.sawatt.ui.components.SawattIcons
import app.sawatt.ui.components.SawattTopBar
import app.sawatt.ui.components.SectionCard
import app.sawatt.ui.components.SwitchRow
import app.sawatt.ui.theme.SawattColors
import java.time.LocalDate
import kotlin.math.roundToInt

/** The "Me" tab: profile numbers, devices and settings. */
@Composable
fun MeScreen(container: AppContainer, nav: NavHostController) {
    val profile by container.settings.profile.collectAsStateWithLifecycle()
    val app by container.settings.app.collectAsStateWithLifecycle()
    val bike by container.trainer.bike.collectAsStateWithLifecycle()
    val hr by container.trainer.heartRateSensor.collectAsStateWithLifecycle()
    val update = container.settings::updateApp
    var aboutOpen by rememberSaveable { mutableStateOf(false) }
    val ftpW = RideMath.effectiveFtp(profile)

    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        LargeTitle("Me")

        SectionCard(Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp).fillMaxWidth(), onClick = { nav.navigate(Routes.PROFILE) }) {
            Row {
                ProfileNumber("FTP", ftpW.roundToInt().toString(), "W", Modifier.weight(1f)) {
                    if (profile.weightKg > 0) "%.1f W/kg".format(ftpW / profile.weightKg) else ""
                }
                ProfileNumber("Max HR", RideMath.effectiveMaxHr(profile).roundToInt().toString(), "bpm", Modifier.weight(1f)) {
                    if (profile.maxHr > 0) "Set by you" else "Estimated"
                }
                ProfileNumber("Weight", profile.weightKg.roundToInt().toString(), "kg", Modifier.weight(1f)) {
                    "Edit profile"
                }
            }
        }

        GroupHeader("Devices")
        Group {
            GroupRow(
                "Bike",
                value = linkStatus(bike),
                leading = { IconTile(SawattIcons.DirectionsBike, SawattColors.Text) },
                onClick = { nav.navigate(Routes.DEVICES) },
            )
            GroupDivider()
            GroupRow(
                "Heart-rate strap",
                value = linkStatus(hr),
                leading = { IconTile(Icons.Default.Favorite, SawattColors.Heart) },
                onClick = { nav.navigate(Routes.DEVICES) },
            )
        }

        GroupHeader("During a workout")
        Group {
            SwitchRow("Beep at each transition", app.transitionBeep) { v -> update { it.copy(transitionBeep = v) } }
            GroupDivider()
            SwitchRow("3-2-1 countdown", app.countdownBeeps, "Three short beeps before each block ends") { v ->
                update { it.copy(countdownBeeps = v) }
            }
            GroupDivider()
            SwitchRow("Keep screen on", app.keepScreenOn) { v -> update { it.copy(keepScreenOn = v) } }
        }

        GroupHeader("App")
        Group {
            GroupRow(
                "Units",
                value = if (profile.units == UnitSystem.METRIC) "Metric" else "Imperial",
                onClick = {
                    container.settings.updateProfile {
                        it.copy(units = if (it.units == UnitSystem.METRIC) UnitSystem.IMPERIAL else UnitSystem.METRIC)
                    }
                },
            )
            GroupDivider()
            SwitchRow("Simulated bike", app.simulateBike, "Try SaWatt without a bike") { v -> update { it.copy(simulateBike = v) } }
            GroupDivider()
            GroupRow("About SaWatt", onClick = { aboutOpen = !aboutOpen })
            if (aboutOpen) {
                Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Structured interval training for indoor bikes and power meters. SaWatt reads the bike over " +
                            "the standard Bluetooth Cycling Power service and never writes to it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SawattColors.TextSecondary,
                    )
                    Text(
                        "Bike firmware: ${bike?.firmware ?: "unknown (connect the bike)"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = SawattColors.TextSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileNumber(
    label: String,
    value: String,
    unit: String,
    modifier: Modifier,
    caption: () -> String,
) {
    Column(modifier) {
        MetricLabel(label)
        Metric(value, unit = unit, size = 30.sp)
        Text(caption(), style = MaterialTheme.typography.bodySmall, fontSize = 12.sp, color = SawattColors.TextSecondary)
    }
}

private fun linkStatus(link: DeviceLink?): String = when (link?.state) {
    null -> "Not connected"
    LinkState.CONNECTING -> "Connecting…"
    LinkState.CONNECTED -> "Connected"
    LinkState.RECONNECTING -> "Waiting…"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(container: AppContainer, onBack: () -> Unit) {
    val profile by container.settings.profile.collectAsStateWithLifecycle()
    val update = container.settings::updateProfile

    Scaffold(containerColor = SawattColors.Background, topBar = { SawattTopBar("Profile", onBack) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            MetricLabel("Training")
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
            Choice("Cardio training per week", listOf("1-3 h", "3-5 h", "5-8 h", "8+ h"), profile.trainingLevel) { i ->
                update { it.copy(trainingLevel = i) }
            }

            MetricLabel("You", modifier = Modifier.padding(top = 8.dp))
            Choice("Gender", listOf("Male", "Female"), if (profile.gender == Gender.MALE) 0 else 1) { i ->
                update { it.copy(gender = if (i == 0) Gender.MALE else Gender.FEMALE) }
            }
            TextSetting("Date of birth (YYYY-MM-DD)", profile.birthDate.toString(), KeyboardType.Text) { text ->
                runCatching { LocalDate.parse(text) }.getOrNull()?.let { d -> update { it.copy(birthDate = d) } }
            }
            NumberSetting("Weight (kg)", profile.weightKg.roundToInt()) { v -> if (v in 30..300) update { it.copy(weightKg = v.toDouble()) } }
            NumberSetting("Height (cm)", profile.heightCm.roundToInt()) { v -> if (v in 120..250) update { it.copy(heightCm = v.toDouble()) } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Choice(title: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.bodyMedium, color = SawattColors.TextSecondary, modifier = Modifier.padding(bottom = 6.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { i, label ->
                SegmentedButton(
                    selected = i == selected,
                    onClick = { onSelect(i) },
                    shape = SegmentedButtonDefaults.itemShape(i, options.size),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = SawattColors.Accent.copy(alpha = 0.18f),
                        activeContentColor = SawattColors.Accent,
                        activeBorderColor = SawattColors.Hairline,
                        inactiveContainerColor = SawattColors.Surface,
                        inactiveContentColor = SawattColors.TextSecondary,
                        inactiveBorderColor = SawattColors.Hairline,
                    ),
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
