package app.sawatt.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.sawatt.AppContainer
import app.sawatt.core.trainer.LinkState
import app.sawatt.ui.Routes
import app.sawatt.ui.components.SawattTopBar
import app.sawatt.workout.WorkoutStatus

@Composable
fun HomeScreen(container: AppContainer, nav: NavController) {
    val workout by container.workout.state.collectAsStateWithLifecycle()
    val bike by container.trainer.bike.collectAsStateWithLifecycle()
    val hr by container.trainer.heartRateSensor.collectAsStateWithLifecycle()

    Scaffold(topBar = { SawattTopBar("Sawatt") }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val bikeText = when {
                bike == null -> "No bike connected"
                bike?.state == LinkState.CONNECTED -> "Bike: ${bike?.device?.name}"
                else -> "Bike: ${bike?.device?.name} (waiting…)"
            }
            val hrText = hr?.let { " · HR: ${it.device.name}" } ?: ""
            Text(bikeText + hrText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            if (workout.status != WorkoutStatus.IDLE) {
                MenuCard(Icons.AutoMirrored.Filled.DirectionsBike, "Back to workout", "A workout is in progress") {
                    nav.navigate(Routes.workout(workout.plan?.id))
                }
            } else {
                MenuCard(Icons.AutoMirrored.Filled.DirectionsBike, "Free ride", "Ride freely, split with the Lap button") {
                    nav.navigate(Routes.workout(null))
                }
                MenuCard(Icons.AutoMirrored.Filled.ListAlt, "Structured workouts", "Build and ride interval plans") {
                    nav.navigate(Routes.PLANS)
                }
            }
            MenuCard(Icons.Default.Bluetooth, "Connect devices", "Bike and heart-rate strap") { nav.navigate(Routes.DEVICES) }
            MenuCard(Icons.Default.History, "Workout history", "Workouts, weeks, months, years") { nav.navigate(Routes.HISTORY) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SmallCard(Icons.Default.Person, "Profile", Modifier.weight(1f)) { nav.navigate(Routes.PROFILE) }
                SmallCard(Icons.Default.Settings, "Settings", Modifier.weight(1f)) { nav.navigate(Routes.SETTINGS) }
            }
            MenuCard(Icons.Default.HourglassEmpty, "Coming later", "FTP test, VO2max test, levels & medals, Strava") {
                nav.navigate(Routes.LATER)
            }
        }
    }
}

@Composable
private fun MenuCard(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(16.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SmallCard(icon: ImageVector, title: String, modifier: Modifier, onClick: () -> Unit) {
    Card(
        modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Text(title, style = MaterialTheme.typography.titleSmall)
        }
    }
}
