package app.sawatt.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.bluetooth.BluetoothAdapter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.sawatt.AppContainer
import app.sawatt.R
import app.sawatt.core.trainer.DeviceLink
import app.sawatt.core.trainer.DeviceType
import app.sawatt.core.trainer.DiscoveredDevice
import app.sawatt.core.trainer.LinkState
import app.sawatt.ui.components.Group
import app.sawatt.ui.components.GroupDivider
import app.sawatt.ui.components.GroupHeader
import app.sawatt.ui.components.GroupRow
import app.sawatt.ui.components.IconTile
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
    var message by remember { mutableStateOf<Int?>(null) }

    fun hasPermissions() = blePermissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    val enableBt = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (trainer.isEnabled()) trainer.startScan() else message = R.string.devices_bluetooth_off
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
            message = R.string.devices_permission_required
        }
    }

    fun requestScan() {
        // The simulated bike does not use Bluetooth: no permission to request.
        if (simulated || hasPermissions()) scan() else permissionLauncher.launch(blePermissions + notificationPermission())
    }

    LaunchedEffect(Unit) { requestScan() }

    Scaffold(containerColor = SawattColors.Background, topBar = { SawattTopBar(stringResource(R.string.devices_title), onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            GroupHeader(stringResource(R.string.devices_connected), Modifier.padding(top = 0.dp))
            Group {
                LinkRow(SawattIcons.DirectionsBike, SawattColors.Text, stringResource(R.string.devices_bike), bike, bike?.let {
                    "${readings.powerW} W · ${readings.cadenceRpm} rpm" + (it.firmware?.let { f -> " · fw $f" } ?: "")
                }) {
                    trainer.disconnect(DeviceType.BIKE)
                    if (!simulated) container.settings.updateApp { it.copy(defaultBike = null) }
                }
                GroupDivider()
                LinkRow(Icons.Default.Favorite, SawattColors.Heart, stringResource(R.string.devices_hr_strap), hr, hr?.let { "${readings.heartRate} bpm" }) {
                    trainer.disconnect(DeviceType.HEART_RATE)
                    if (!simulated) container.settings.updateApp { it.copy(defaultHrSensor = null) }
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(onClick = ::requestScan, enabled = !scanning) { Text(stringResource(if (scanning) R.string.devices_scanning else R.string.devices_scan)) }
                if (scanning) {
                    OutlinedButton(onClick = trainer::stopScan) { Text(stringResource(R.string.devices_stop)) }
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = SawattColors.Accent)
                }
            }
            message?.let {
                Text(stringResource(it), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            }
            Text(
                stringResource(R.string.devices_cant_find_bike),
                style = MaterialTheme.typography.bodySmall,
                color = SawattColors.TextSecondary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )

            val connected = setOfNotNull(bike?.device?.address, hr?.device?.address)
            val nearby = discovered.filter { it.address !in connected }
            if (nearby.isNotEmpty()) {
                GroupHeader(stringResource(R.string.devices_nearby))
                Group {
                    nearby.forEachIndexed { i, d ->
                        if (i > 0) GroupDivider()
                        DeviceRow(d) {
                            trainer.connect(d)
                            // Only remember real devices, to reconnect them automatically.
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
}

private fun notificationPermission(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) arrayOf(Manifest.permission.POST_NOTIFICATIONS) else emptyArray()

@Composable
private fun LinkRow(icon: ImageVector, color: Color, title: String, link: DeviceLink?, extra: String?, onForget: () -> Unit) {
    val state = stringResource(
        when (link?.state) {
            null -> R.string.devices_link_not_connected
            LinkState.CONNECTING -> R.string.devices_link_connecting
            LinkState.CONNECTED -> R.string.devices_link_connected
            LinkState.RECONNECTING -> R.string.devices_link_waiting
        },
    )
    GroupRow(
        link?.device?.name ?: title,
        subtitle = listOfNotNull(state, extra).joinToString(" · "),
        leading = { IconTile(icon, color) },
        trailing = if (link != null) ({ TextButton(onClick = onForget) { Text(stringResource(R.string.devices_forget)) } }) else null,
    )
}

@Composable
private fun DeviceRow(device: DiscoveredDevice, onClick: () -> Unit) {
    val bike = device.type == DeviceType.BIKE
    GroupRow(
        device.name,
        value = "${device.rssi} dBm",
        leading = {
            if (bike) IconTile(SawattIcons.DirectionsBike, SawattColors.Text) else IconTile(Icons.Default.Favorite, SawattColors.Heart)
        },
        onClick = onClick,
    )
}
