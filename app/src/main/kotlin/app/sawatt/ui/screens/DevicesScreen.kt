package app.sawatt.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.bluetooth.BluetoothAdapter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.sawatt.AppContainer
import app.sawatt.core.trainer.DeviceLink
import app.sawatt.core.trainer.DeviceType
import app.sawatt.core.trainer.DiscoveredDevice
import app.sawatt.core.trainer.LinkState
import app.sawatt.ui.components.SawattIcons
import app.sawatt.ui.components.SawattTopBar
import app.sawatt.ui.theme.SawattColors

private val blePermissions: Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

@Composable
fun DevicesScreen(container: AppContainer, onBack: () -> Unit) {
    val trainer = container.trainer
    val app by container.settings.app.collectAsStateWithLifecycle()
    val simulated = app.simulateBike
    val context = LocalContext.current
    val scanning by trainer.scanning.collectAsStateWithLifecycle()
    val discovered by trainer.discovered.collectAsStateWithLifecycle()
    val bike by trainer.bike.collectAsStateWithLifecycle()
    val hr by trainer.heartRateSensor.collectAsStateWithLifecycle()
    val readings by container.workout.readings.collectAsStateWithLifecycle()
    var message by remember { mutableStateOf<String?>(null) }

    fun hasPermissions() = blePermissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    val enableBt = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (trainer.isEnabled()) trainer.startScan() else message = "Bluetooth must be on to connect."
    }

    fun scan() {
        message = null
        if (!trainer.isEnabled()) {
            enableBt.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        } else {
            trainer.startScan()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.all { it }) {
            // Also ask for notifications (workout notification); not required.
            scan()
        } else {
            message = "Bluetooth permission is required to find the bike."
        }
    }

    fun requestScan() {
        // Le vélo simulé n'utilise pas le Bluetooth : pas de permission à demander.
        if (simulated || hasPermissions()) scan() else permissionLauncher.launch(blePermissions + notificationPermission())
    }

    LaunchedEffect(Unit) { requestScan() }

    Scaffold(topBar = { SawattTopBar("Connect devices", onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            LinkCard("Bike", bike, extra = bike?.let { "${readings.powerW} W · ${readings.cadenceRpm} rpm" + (it.firmware?.let { f -> " · fw $f" } ?: "") }) {
                trainer.disconnect(DeviceType.BIKE)
                if (!simulated) container.settings.updateApp { it.copy(defaultBike = null) }
            }
            LinkCard("Heart rate", hr, extra = hr?.let { "${readings.heartRate} bpm" }) {
                trainer.disconnect(DeviceType.HEART_RATE)
                if (!simulated) container.settings.updateApp { it.copy(defaultHrSensor = null) }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = ::requestScan, enabled = !scanning) { Text(if (scanning) "Scanning…" else "Scan") }
                Spacer(Modifier.width(12.dp))
                if (scanning) OutlinedButton(onClick = trainer::stopScan) { Text("Stop") }
            }
            if (scanning) LinearProgressIndicator(Modifier.fillMaxWidth())
            message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Text(
                "Can't find your bike? Start pedalling: many indoor bikes are powered by your pedalling and only advertise while awake.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val connected = setOfNotNull(bike?.device?.address, hr?.device?.address)
                items(discovered.filter { it.address !in connected }, key = { it.address }) { d ->
                    DeviceRow(d) {
                        trainer.connect(d)
                        // Ne mémorise que les vrais appareils, pour les reconnecter automatiquement.
                        if (!simulated) {
                            container.settings.updateApp {
                                if (d.type == DeviceType.BIKE) it.copy(defaultBike = d.address) else it.copy(defaultHrSensor = d.address)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun notificationPermission(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) arrayOf(Manifest.permission.POST_NOTIFICATIONS) else emptyArray()

@Composable
private fun LinkCard(title: String, link: DeviceLink?, extra: String?, onDisconnect: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title.uppercase(), style = MaterialTheme.typography.labelSmall)
                if (link == null) {
                    Text("Not connected", style = MaterialTheme.typography.bodyLarge)
                } else {
                    val state = when (link.state) {
                        LinkState.CONNECTING -> "connecting…"
                        LinkState.CONNECTED -> "connected"
                        LinkState.RECONNECTING -> "waiting for device…"
                    }
                    Text("${link.device.name} — $state", style = MaterialTheme.typography.bodyLarge)
                    extra?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
            if (link != null) OutlinedButton(onClick = onDisconnect) { Text("Forget") }
        }
    }
}

@Composable
private fun DeviceRow(device: DiscoveredDevice, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (device.type == DeviceType.BIKE) {
                Icon(SawattIcons.DirectionsBike, contentDescription = "Bike", tint = MaterialTheme.colorScheme.primary)
            } else {
                Icon(Icons.Default.Favorite, contentDescription = "Heart rate", tint = SawattColors.HeartRed)
            }
            Spacer(Modifier.width(12.dp))
            Text(device.name, Modifier.weight(1f))
            Text("${device.rssi} dBm", style = MaterialTheme.typography.labelSmall)
        }
    }
}
