package app.sawatt.ui.screens

import androidx.activity.compose.LocalActivity
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import app.sawatt.AppContainer
import app.sawatt.AppLanguage
import app.sawatt.R
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
import java.util.Locale
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
        LargeTitle(stringResource(R.string.nav_me))

        SectionCard(Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp).fillMaxWidth(), onClick = { nav.navigate(Routes.PROFILE) }) {
            Row {
                ProfileNumber(
                    stringResource(R.string.me_ftp), ftpW.roundToInt().toString(), "W",
                    if (profile.weightKg > 0) "%.1f W/kg".format(ftpW / profile.weightKg) else "",
                    Modifier.weight(1f),
                )
                ProfileNumber(
                    stringResource(R.string.me_max_hr), RideMath.effectiveMaxHr(profile).roundToInt().toString(), "bpm",
                    stringResource(if (profile.maxHr > 0) R.string.me_set_by_you else R.string.me_estimated),
                    Modifier.weight(1f),
                )
                ProfileNumber(
                    stringResource(R.string.me_weight), profile.weightKg.roundToInt().toString(), "kg",
                    stringResource(R.string.me_edit_profile),
                    Modifier.weight(1f),
                )
            }
        }

        GroupHeader(stringResource(R.string.me_devices))
        Group {
            GroupRow(
                stringResource(R.string.me_bike),
                value = stringResource(linkStatus(bike)),
                leading = { IconTile(SawattIcons.DirectionsBike, SawattColors.Text) },
                onClick = { nav.navigate(Routes.DEVICES) },
            )
            GroupDivider()
            GroupRow(
                stringResource(R.string.me_hr_strap),
                value = stringResource(linkStatus(hr)),
                leading = { IconTile(Icons.Default.Favorite, SawattColors.Heart) },
                onClick = { nav.navigate(Routes.DEVICES) },
            )
        }

        GroupHeader(stringResource(R.string.me_during_workout))
        Group {
            SwitchRow(stringResource(R.string.me_transition_beep), app.transitionBeep) { v -> update { it.copy(transitionBeep = v) } }
            GroupDivider()
            SwitchRow(stringResource(R.string.me_countdown), app.countdownBeeps, stringResource(R.string.me_countdown_hint)) { v ->
                update { it.copy(countdownBeeps = v) }
            }
            GroupDivider()
            SwitchRow(stringResource(R.string.me_keep_screen_on), app.keepScreenOn) { v -> update { it.copy(keepScreenOn = v) } }
        }

        GroupHeader(stringResource(R.string.me_app))
        Group {
            GroupRow(
                stringResource(R.string.me_units),
                value = stringResource(if (profile.units == UnitSystem.METRIC) R.string.me_units_metric else R.string.me_units_imperial),
                onClick = {
                    container.settings.updateProfile {
                        it.copy(units = if (it.units == UnitSystem.METRIC) UnitSystem.IMPERIAL else UnitSystem.METRIC)
                    }
                },
            )
            GroupDivider()
            LanguageRow()
            GroupDivider()
            SwitchRow(stringResource(R.string.me_simulated_bike), app.simulateBike, stringResource(R.string.me_simulated_bike_hint)) { v -> update { it.copy(simulateBike = v) } }
            GroupDivider()
            GroupRow(stringResource(R.string.me_about), onClick = { aboutOpen = !aboutOpen })
            if (aboutOpen) {
                Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        stringResource(R.string.me_about_text),
                        style = MaterialTheme.typography.bodySmall,
                        color = SawattColors.TextSecondary,
                    )
                    Text(
                        stringResource(R.string.me_bike_firmware, bike?.firmware ?: stringResource(R.string.me_bike_firmware_unknown)),
                        style = MaterialTheme.typography.bodySmall,
                        color = SawattColors.TextSecondary,
                    )
                }
            }
        }
    }
}

/** Follow the phone, or force one of the [AppLanguage.supported] languages. */
@Composable
private fun LanguageRow() {
    val activity = LocalActivity.current ?: return
    val current = remember { AppLanguage.current(activity) }
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        GroupRow(
            stringResource(R.string.me_language),
            value = current?.let { languageName(it) } ?: stringResource(R.string.me_language_system),
            onClick = { menuOpen = true },
        )
        DropdownMenu(menuOpen, onDismissRequest = { menuOpen = false }, modifier = Modifier.background(SawattColors.SurfaceHigh)) {
            (listOf(null) + AppLanguage.supported).forEach { tag ->
                DropdownMenuItem(
                    text = { Text(tag?.let { languageName(it) } ?: stringResource(R.string.me_language_system)) },
                    trailingIcon = if (tag == current) ({ Icon(Icons.Default.Check, contentDescription = null, tint = SawattColors.Accent) }) else null,
                    onClick = {
                        menuOpen = false
                        if (tag != current) AppLanguage.set(activity, tag)
                    },
                )
            }
        }
    }
}

/** A language's name written in that language, as language pickers usually show it. */
private fun languageName(tag: String): String =
    Locale.forLanguageTag(tag).let { it.getDisplayLanguage(it).replaceFirstChar { c -> c.titlecase(it) } }

@Composable
private fun ProfileNumber(
    label: String,
    value: String,
    unit: String,
    caption: String,
    modifier: Modifier,
) {
    Column(modifier) {
        MetricLabel(label)
        Metric(value, unit = unit, size = 30.sp)
        Text(caption, style = MaterialTheme.typography.bodySmall, fontSize = 12.sp, color = SawattColors.TextSecondary)
    }
}

@StringRes
private fun linkStatus(link: DeviceLink?): Int = when (link?.state) {
    null -> R.string.me_link_not_connected
    LinkState.CONNECTING -> R.string.me_link_connecting
    LinkState.CONNECTED -> R.string.me_link_connected
    LinkState.RECONNECTING -> R.string.me_link_waiting
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(container: AppContainer, onBack: () -> Unit) {
    val profile by container.settings.profile.collectAsStateWithLifecycle()
    val update = container.settings::updateProfile

    Scaffold(containerColor = SawattColors.Background, topBar = { SawattTopBar(stringResource(R.string.me_profile), onBack) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            MetricLabel(stringResource(R.string.me_training))
            NumberSetting(
                stringResource(R.string.me_ftp_setting),
                profile.ftpW,
                supporting = stringResource(R.string.me_estimated_watts, RideMath.estimatedFtp(profile).roundToInt()),
            ) { v -> if (v in 0..999) update { it.copy(ftpW = v) } }
            NumberSetting(
                stringResource(R.string.me_max_hr_setting),
                profile.maxHr,
                supporting = stringResource(R.string.me_estimated_bpm, RideMath.estimatedMaxHr(RideMath.age(profile.birthDate)).roundToInt()),
            ) { v -> if (v in 0..250) update { it.copy(maxHr = v) } }
            Choice(stringResource(R.string.me_training_per_week), listOf("1-3 h", "3-5 h", "5-8 h", "8+ h"), profile.trainingLevel) { i ->
                update { it.copy(trainingLevel = i) }
            }

            MetricLabel(stringResource(R.string.me_you), modifier = Modifier.padding(top = 8.dp))
            Choice(stringResource(R.string.me_gender), listOf(stringResource(R.string.me_male), stringResource(R.string.me_female)), if (profile.gender == Gender.MALE) 0 else 1) { i ->
                update { it.copy(gender = if (i == 0) Gender.MALE else Gender.FEMALE) }
            }
            TextSetting(stringResource(R.string.me_birth_date), profile.birthDate.toString(), KeyboardType.Text) { text ->
                runCatching { LocalDate.parse(text) }.getOrNull()?.let { d -> update { it.copy(birthDate = d) } }
            }
            NumberSetting(stringResource(R.string.me_weight_setting), profile.weightKg.roundToInt()) { v -> if (v in 30..300) update { it.copy(weightKg = v.toDouble()) } }
            NumberSetting(stringResource(R.string.me_height_setting), profile.heightCm.roundToInt()) { v -> if (v in 120..250) update { it.copy(heightCm = v.toDouble()) } }
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
