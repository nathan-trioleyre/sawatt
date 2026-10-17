package app.sawatt.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import app.sawatt.AppContainer
import app.sawatt.R
import app.sawatt.core.history.PeriodKind
import app.sawatt.core.ride.RideMath
import app.sawatt.core.trainer.DeviceLink
import app.sawatt.core.trainer.LinkState
import app.sawatt.ui.Routes
import app.sawatt.ui.buckets
import app.sawatt.ui.components.LargeTitle
import app.sawatt.ui.components.Metric
import app.sawatt.ui.components.MetricLabel
import app.sawatt.ui.components.PeriodBars
import app.sawatt.ui.components.PlanCard
import app.sawatt.ui.components.PlayButton
import app.sawatt.ui.components.SawattIcons
import app.sawatt.ui.components.SectionCard
import app.sawatt.ui.components.SectionHeader
import app.sawatt.ui.components.StatusDot
import app.sawatt.ui.currentPeriod
import app.sawatt.ui.formatDuration
import app.sawatt.ui.openTab
import app.sawatt.ui.recordsIn
import app.sawatt.ui.theme.SawattColors
import app.sawatt.workout.WorkoutStatus
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun TodayScreen(container: AppContainer, nav: NavHostController) {
    val workout by container.workout.state.collectAsStateWithLifecycle()
    val bike by container.trainer.bike.collectAsStateWithLifecycle()
    val hr by container.trainer.heartRateSensor.collectAsStateWithLifecycle()
    val plans by container.plans.plans.collectAsStateWithLifecycle()
    val records by container.history.records.collectAsStateWithLifecycle()
    val profile by container.settings.profile.collectAsStateWithLifecycle()
    val ftpW = RideMath.effectiveFtp(profile)
    val idle = workout.status == WorkoutStatus.IDLE

    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        LargeTitle(stringResource(R.string.today_title), overline = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault())))

        Row(
            Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp)
                .clip(RoundedCornerShape(10.dp))
                .clickable(role = Role.Button) { nav.navigate(Routes.DEVICES) }
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            DeviceStatus(SawattIcons.DirectionsBike, SawattColors.TextSecondary, stringResource(R.string.today_bike), bike)
            DeviceStatus(Icons.Default.Favorite, SawattColors.Heart, stringResource(R.string.today_heart_rate), hr)
        }

        SectionCard(Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp).fillMaxWidth(), padding = 20.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (idle) {
                        Text(stringResource(R.string.today_free_ride), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.today_free_ride_hint), style = MaterialTheme.typography.bodyMedium, color = SawattColors.TextSecondary)
                    } else {
                        MetricLabel(stringResource(if (workout.status == WorkoutStatus.PAUSED) R.string.today_workout_paused else R.string.today_workout_in_progress), SawattColors.Pause)
                        Text(workout.plan?.name ?: stringResource(R.string.today_free_ride), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    }
                }
                PlayButton(
                    stringResource(if (idle) R.string.today_start_free_ride else R.string.today_back_to_workout),
                    onClick = { nav.navigate(Routes.workout(if (idle) null else workout.plan?.id)) },
                    size = 68.dp,
                )
            }
        }

        if (plans.isEmpty()) {
            SectionHeader(stringResource(R.string.today_your_workouts), action = stringResource(R.string.today_create)) { nav.navigate(Routes.planEditor(null)) }
            Text(
                stringResource(R.string.today_no_plans),
                style = MaterialTheme.typography.bodyMedium,
                color = SawattColors.TextSecondary,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        } else {
            SectionHeader(stringResource(R.string.today_your_workouts), action = stringResource(R.string.today_see_all)) { nav.openTab(Routes.PLANS) }
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(plans, key = { it.id }) { plan ->
                    PlanCard(
                        plan, ftpW,
                        modifier = Modifier.width(248.dp),
                        onClick = if (idle) ({ nav.navigate(Routes.workout(plan.id)) }) else null,
                    )
                }
            }
        }

        SectionHeader(stringResource(R.string.today_this_week), action = stringResource(R.string.today_history)) { nav.openTab(Routes.HISTORY) }
        val week = currentPeriod(PeriodKind.WEEK)
        val weekRecords = recordsIn(records, week)
        Row(Modifier.padding(horizontal = 20.dp).fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Column {
                    MetricLabel(stringResource(R.string.today_time))
                    Metric(formatDuration(weekRecords.sumOf { it.summary.durationSec }), size = 30.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column {
                        MetricLabel(stringResource(R.string.today_rides))
                        Metric(weekRecords.size.toString(), size = 22.sp)
                    }
                    Column {
                        MetricLabel(stringResource(R.string.today_kcal))
                        Metric(weekRecords.sumOf { it.summary.kcal }.roundToInt().toString(), size = 22.sp)
                    }
                }
            }
            Spacer(Modifier.width(16.dp))
            PeriodBars(
                buckets(records, week),
                modifier = Modifier.weight(1f),
                barColor = { if (it.isCurrent) SawattColors.Accent else SawattColors.Chevron },
            )
        }
    }
}

@Composable
private fun DeviceStatus(icon: ImageVector, tint: Color, title: String, link: DeviceLink?) {
    val (status, color) = when (link?.state) {
        null -> stringResource(R.string.today_not_connected) to SawattColors.Muted
        LinkState.CONNECTING -> stringResource(R.string.today_connecting) to SawattColors.Pause
        LinkState.CONNECTED -> stringResource(R.string.today_connected) to SawattColors.Accent
        LinkState.RECONNECTING -> stringResource(R.string.today_waiting) to SawattColors.Pause
    }
    val description = stringResource(R.string.today_device_status, title, status)
    Row(
        Modifier.semantics(mergeDescendants = true) { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        StatusDot(color)
    }
}
