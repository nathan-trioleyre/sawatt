package app.sawatt.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.util.Log
import app.sawatt.core.ble.AdvertisingParser
import app.sawatt.core.ble.BikeSample
import app.sawatt.core.ble.CadenceCalculator
import app.sawatt.core.ble.CyclingPowerParser
import app.sawatt.core.ble.GattIds
import app.sawatt.core.ble.HeartRateParser
import app.sawatt.core.trainer.DeviceLink
import app.sawatt.core.trainer.DeviceType
import app.sawatt.core.trainer.DiscoveredDevice
import app.sawatt.core.trainer.LinkState
import app.sawatt.core.trainer.TrainerConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * BLE client for a bike with a power sensor (Cycling Power service) and a heart-rate strap.
 *
 * Scans everything and keeps devices advertising 0x1818 or 0x180D, subscribes to 0x2A63 / 0x2A37
 * and reads the firmware revision (0x2A26). Never writes to the bike.
 *
 * The bike is powered by pedalling and disappears when the rider stops, so an unexpected
 * disconnect is followed by a background `autoConnect` that resumes as soon as it advertises again.
 */
@SuppressLint("MissingPermission") // Callers check permissions; SecurityException is caught below.
class BleManager(private val context: Context, private val scope: CoroutineScope) : TrainerConnection {

    private val adapter get() = context.getSystemService(BluetoothManager::class.java)?.adapter

    private val _scanning = MutableStateFlow(false)
    override val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val _discovered = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    override val discovered: StateFlow<List<DiscoveredDevice>> = _discovered.asStateFlow()

    private val _bike = MutableStateFlow<DeviceLink?>(null)
    override val bike: StateFlow<DeviceLink?> = _bike.asStateFlow()

    private val _heartRateSensor = MutableStateFlow<DeviceLink?>(null)
    override val heartRateSensor: StateFlow<DeviceLink?> = _heartRateSensor.asStateFlow()

    private val _bikeSamples = MutableSharedFlow<BikeSample>(extraBufferCapacity = 64)
    override val bikeSamples: SharedFlow<BikeSample> = _bikeSamples.asSharedFlow()

    private val _heartRates = MutableSharedFlow<Int>(extraBufferCapacity = 64)
    override val heartRates: SharedFlow<Int> = _heartRates.asSharedFlow()

    private val connections = mutableMapOf<DeviceType, Connection>()
    private var scanStopJob: Job? = null

    override fun isEnabled(): Boolean = adapter?.isEnabled == true

    // ---- scanning -----------------------------------------------------------------------

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) = handleScanResult(result)
        override fun onBatchScanResults(results: MutableList<ScanResult>) = results.forEach(::handleScanResult)
        override fun onScanFailed(errorCode: Int) {
            Log.w(TAG, "Scan failed: $errorCode")
            _scanning.value = false
        }
    }

    override fun startScan(durationMs: Long) {
        val scanner = adapter?.bluetoothLeScanner ?: return
        if (_scanning.value) return
        // Keep devices we are connected to; drop stale results.
        val connected = setOfNotNull(_bike.value?.device?.address, _heartRateSensor.value?.device?.address)
        _discovered.update { list -> list.filter { it.address in connected } }
        try {
            val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
            // No hardware filter: some phones do not match service UUID
            // filters reliably, so every result is inspected in software.
            scanner.startScan(null, settings, scanCallback)
            _scanning.value = true
        } catch (e: SecurityException) {
            Log.w(TAG, "Missing scan permission", e)
            return
        }
        scanStopJob?.cancel()
        scanStopJob = scope.launch {
            delay(durationMs)
            stopScan()
        }
    }

    override fun stopScan() {
        scanStopJob?.cancel()
        if (!_scanning.value) return
        try {
            adapter?.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (e: SecurityException) {
            Log.w(TAG, "Missing scan permission", e)
        }
        _scanning.value = false
    }

    private fun handleScanResult(result: ScanResult) {
        val record = result.scanRecord
        val uuids = buildSet {
            record?.serviceUuids?.forEach { add(it.uuid.toString().substring(4, 8).uppercase()) }
            record?.bytes?.let { addAll(AdvertisingParser.serviceUuids16(it)) }
        }
        val name = record?.deviceName ?: safeName(result.device) ?: return
        val type = when {
            "%04X".format(GattIds.SERVICE_CYCLING_POWER) in uuids -> DeviceType.BIKE
            "%04X".format(GattIds.SERVICE_HEART_RATE) in uuids -> DeviceType.HEART_RATE
            GattIds.BIKE_NAME_PREFIXES.any { name.startsWith(it) } -> DeviceType.BIKE
            else -> return
        }
        val device = DiscoveredDevice(result.device.address, name, result.rssi, type)
        _discovered.update { list ->
            (list.filterNot { it.address == device.address } + device).sortedByDescending { it.rssi }
        }
    }

    private fun safeName(device: BluetoothDevice): String? = try {
        device.name
    } catch (e: SecurityException) {
        null
    }

    // ---- connections --------------------------------------------------------------------

    private fun linkFlow(type: DeviceType) = if (type == DeviceType.BIKE) _bike else _heartRateSensor

    override fun connect(device: DiscoveredDevice, keepScanning: Boolean) {
        if (!keepScanning) stopScan()
        disconnect(device.type)
        val btDevice = adapter?.getRemoteDevice(device.address) ?: return
        val connection = Connection(device, btDevice)
        connections[device.type] = connection
        linkFlow(device.type).value = DeviceLink(device, LinkState.CONNECTING)
        connection.open(autoConnect = false)
    }

    override fun disconnect(type: DeviceType) {
        connections.remove(type)?.close()
        linkFlow(type).value = null
    }

    override fun disconnectAll() = DeviceType.entries.forEach(::disconnect)

    private inner class Connection(val device: DiscoveredDevice, private val btDevice: BluetoothDevice) {
        private var gatt: BluetoothGatt? = null
        private var closed = false
        private val cadence = CadenceCalculator()

        fun open(autoConnect: Boolean) {
            if (closed) return
            cadence.reset()
            try {
                gatt = btDevice.connectGatt(context, autoConnect, callback, BluetoothDevice.TRANSPORT_LE)
            } catch (e: SecurityException) {
                Log.w(TAG, "Missing connect permission", e)
            }
        }

        fun close() {
            closed = true
            try {
                gatt?.disconnect()
                gatt?.close()
            } catch (e: SecurityException) {
                Log.w(TAG, "Missing connect permission", e)
            }
            gatt = null
        }

        private fun setState(state: LinkState, firmware: String? = null) {
            if (closed) return
            linkFlow(device.type).update { link ->
                DeviceLink(device, state, firmware ?: link?.firmware)
            }
        }

        private val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                if (closed) return
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        setState(LinkState.CONNECTING)
                        g.discoverServices()
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        Log.i(TAG, "${device.name} disconnected (status $status), waiting for it to come back")
                        setState(LinkState.RECONNECTING)
                        g.close()
                        if (gatt === g) gatt = null
                        scope.launch {
                            delay(1_000)
                            open(autoConnect = true)
                        }
                    }
                }
            }

            override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                val (serviceId, charId) = when (device.type) {
                    DeviceType.BIKE -> GattIds.SERVICE_CYCLING_POWER to GattIds.CHAR_CYCLING_POWER_MEASUREMENT
                    DeviceType.HEART_RATE -> GattIds.SERVICE_HEART_RATE to GattIds.CHAR_HEART_RATE_MEASUREMENT
                }
                val characteristic = g.getService(uuid(serviceId))?.getCharacteristic(uuid(charId))
                if (characteristic == null) {
                    Log.w(TAG, "${device.name}: measurement characteristic not found")
                    return
                }
                enableNotifications(g, characteristic)
            }

            override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
                setState(LinkState.CONNECTED)
                // One GATT operation at a time: read the firmware revision once notifications are on.
                if (device.type == DeviceType.BIKE) {
                    g.getService(uuid(GattIds.SERVICE_DEVICE_INFORMATION))
                        ?.getCharacteristic(uuid(GattIds.CHAR_FIRMWARE_REVISION))
                        ?.let { g.readCharacteristic(it) }
                }
            }

            override fun onCharacteristicRead(
                g: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
                status: Int,
            ) = handleRead(characteristic, value, status)

            @Deprecated("Used below API 33")
            override fun onCharacteristicRead(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    @Suppress("DEPRECATION")
                    handleRead(characteristic, characteristic.value ?: return, status)
                }
            }

            override fun onCharacteristicChanged(
                g: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
            ) = handleNotification(characteristic, value)

            @Deprecated("Used below API 33")
            override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    @Suppress("DEPRECATION")
                    handleNotification(characteristic, characteristic.value ?: return)
                }
            }
        }

        private fun enableNotifications(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            g.setCharacteristicNotification(characteristic, true)
            val cccd = characteristic.getDescriptor(uuid(GattIds.DESCRIPTOR_CCCD)) ?: return
            val enable = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                g.writeDescriptor(cccd, enable)
            } else {
                @Suppress("DEPRECATION")
                cccd.value = enable
                @Suppress("DEPRECATION")
                g.writeDescriptor(cccd)
            }
        }

        private fun handleRead(characteristic: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS && characteristic.uuid == uuid(GattIds.CHAR_FIRMWARE_REVISION)) {
                setState(LinkState.CONNECTED, firmware = String(value, Charsets.US_ASCII).trim())
            }
        }

        private fun handleNotification(characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            when (characteristic.uuid) {
                uuid(GattIds.CHAR_CYCLING_POWER_MEASUREMENT) -> {
                    val m = CyclingPowerParser.parse(value) ?: return
                    cadence.onMeasurement(System.currentTimeMillis(), m)?.let { _bikeSamples.tryEmit(it) }
                }
                uuid(GattIds.CHAR_HEART_RATE_MEASUREMENT) -> {
                    HeartRateParser.parse(value)?.let { _heartRates.tryEmit(it) }
                }
            }
        }
    }

    private companion object {
        const val TAG = "BleManager"
        fun uuid(id: Int): UUID = UUID.fromString(GattIds.uuid16(id))
    }
}
