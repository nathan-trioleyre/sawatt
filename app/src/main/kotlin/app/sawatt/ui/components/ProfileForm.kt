package app.sawatt.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.sawatt.R
import app.sawatt.core.model.Gender
import app.sawatt.core.model.UserProfile
import app.sawatt.core.ride.RideMath
import app.sawatt.ui.theme.SawattColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToInt

/**
 * The rider's profile fields, shared by the first-launch screen and Me → Profile. Every valid value
 * is passed to [onChange] as it is typed or picked.
 */
@Composable
fun ProfileForm(profile: UserProfile, onChange: ((UserProfile) -> UserProfile) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        MetricLabel(stringResource(R.string.me_you))
        Choice(stringResource(R.string.me_gender), listOf(stringResource(R.string.me_male), stringResource(R.string.me_female)), if (profile.gender == Gender.MALE) 0 else 1) { i ->
            onChange { it.copy(gender = if (i == 0) Gender.MALE else Gender.FEMALE) }
        }
        BirthDateField(profile.birthDate) { d -> onChange { it.copy(birthDate = d) } }
        NumberSetting(stringResource(R.string.me_weight_setting), profile.weightKg.roundToInt()) { v -> if (v in 30..300) onChange { it.copy(weightKg = v.toDouble()) } }
        NumberSetting(stringResource(R.string.me_height_setting), profile.heightCm.roundToInt()) { v -> if (v in 120..250) onChange { it.copy(heightCm = v.toDouble()) } }

        MetricLabel(stringResource(R.string.me_training), modifier = Modifier.padding(top = 8.dp))
        Choice(stringResource(R.string.me_training_per_week), listOf("1-3 h", "3-5 h", "5-8 h", "8+ h"), profile.trainingLevel) { i ->
            onChange { it.copy(trainingLevel = i) }
        }
        OptionalNumberSetting(
            stringResource(R.string.me_ftp_setting),
            profile.ftpW,
            supporting = stringResource(R.string.me_estimated_watts, RideMath.estimatedFtp(profile).roundToInt()),
        ) { v -> if (v in 0..999) onChange { it.copy(ftpW = v) } }
        OptionalNumberSetting(
            stringResource(R.string.me_max_hr_setting),
            profile.maxHr,
            supporting = stringResource(R.string.me_estimated_bpm, RideMath.estimatedMaxHr(RideMath.age(profile.birthDate)).roundToInt()),
        ) { v -> if (v in 0..250) onChange { it.copy(maxHr = v) } }
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

/** A read-only field that opens a date picker; the date is shown in the phone's format. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BirthDateField(date: LocalDate, onPick: (LocalDate) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    // A read-only text field consumes taps, so the press is caught on its interaction source.
    LaunchedEffect(interaction) {
        interaction.interactions.collect { if (it is PressInteraction.Release) open = true }
    }
    OutlinedTextField(
        value = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)),
        onValueChange = {},
        readOnly = true,
        label = { Text(stringResource(R.string.me_birth_date)) },
        singleLine = true,
        interactionSource = interaction,
        modifier = Modifier.fillMaxWidth(),
    )
    if (open) {
        val today = LocalDate.now()
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            yearRange = (today.year - 100)..(today.year - 10),
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    open = false
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(android.R.string.cancel)) } },
        ) {
            DatePicker(state)
        }
    }
}

@Composable
private fun NumberSetting(label: String, value: Int, supporting: String? = null, onValid: (Int) -> Unit) {
    TextSetting(label, value.toString(), KeyboardType.Number, supporting) { text -> text.toIntOrNull()?.let(onValid) }
}

/** A number where 0, shown as an empty field, means "estimate it". */
@Composable
private fun OptionalNumberSetting(label: String, value: Int, supporting: String, onValid: (Int) -> Unit) {
    TextSetting(label, if (value > 0) value.toString() else "", KeyboardType.Number, supporting) { text ->
        if (text.isBlank()) onValid(0) else text.toIntOrNull()?.let(onValid)
    }
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
