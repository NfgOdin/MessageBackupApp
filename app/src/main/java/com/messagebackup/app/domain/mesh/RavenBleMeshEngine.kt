package com.messagebackup.app.domain.mesh

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Off-Grid Bluetooth Low Energy (BLE) Radio Mesh Transport.
 * Broadcasts cryptographic call-sign beacons and GATT packets without needing
 * any local Wi-Fi router, mobile hotspot, or internet access.
 */
class RavenBleMeshEngine(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {

    companion object {
        // Custom 128-bit UUID dedicated to Muninn Raven Mesh discovery
        val SERVICE_UUID: UUID = UUID.fromString("00008744-0000-1000-8000-00805F9B34FB")
        val PARCEL_UUID = ParcelUuid(SERVICE_UUID)
    }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private var advertiser: BluetoothLeAdvertiser? = null
    private var scanner: BluetoothLeScanner? = null

    private val _discoveredBleNodes = MutableSharedFlow<BleRavenNode>(extraBufferCapacity = 32)
    val discoveredBleNodes: SharedFlow<BleRavenNode> = _discoveredBleNodes.asSharedFlow()

    private var isAdvertising = false
    private var isScanning = false

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            isAdvertising = true
        }

        override fun onStartFailure(errorCode: Int) {
            isAdvertising = false
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.let { handleScanResult(it) }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            results?.forEach { handleScanResult(it) }
        }

        override fun onScanFailed(errorCode: Int) {
            // BLE scan failed
        }
    }

    /**
     * Checks if BLE hardware and advertising is supported on this device.
     */
    fun isBleSupported(): Boolean {
        return bluetoothAdapter != null && bluetoothAdapter.isMultipleAdvertisementSupported
    }

    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }

    /**
     * Starts broadcasting this node's Raven Call-Sign via BLE Advertisement packets.
     */
    @Synchronized
    fun startBroadcasting(callSign: String): Boolean {
        if (!isBleSupported() || !isBluetoothEnabled() || isAdvertising) return false

        try {
            advertiser = bluetoothAdapter?.bluetoothLeAdvertiser ?: return false

            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                .setConnectable(true)
                .setTimeout(0)
                .build()

            // Compress call sign into compact manufacturer or service data payload
            val cleanName = callSign.removePrefix("raven://").take(16)
            val data = AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .setIncludeTxPowerLevel(false)
                .addServiceUuid(PARCEL_UUID)
                .addServiceData(PARCEL_UUID, cleanName.toByteArray(Charsets.UTF_8))
                .build()

            advertiser?.startAdvertising(settings, data, advertiseCallback)
            return true
        } catch (_: SecurityException) {
            return false
        } catch (_: Exception) {
            return false
        }
    }

    /**
     * Stops BLE broadcasting.
     */
    @Synchronized
    fun stopBroadcasting() {
        if (isAdvertising) {
            try {
                advertiser?.stopAdvertising(advertiseCallback)
            } catch (_: SecurityException) {}
            isAdvertising = false
        }
    }

    /**
     * Starts continuous background scanning for nearby off-grid Raven Mesh nodes.
     */
    @Synchronized
    fun startScanning(): Boolean {
        if (!isBleSupported() || !isBluetoothEnabled() || isScanning) return false

        try {
            scanner = bluetoothAdapter?.bluetoothLeScanner ?: return false

            val scanFilter = ScanFilter.Builder()
                .setServiceUuid(PARCEL_UUID)
                .build()

            val scanSettings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
                .build()

            scanner?.startScan(listOf(scanFilter), scanSettings, scanCallback)
            isScanning = true
            return true
        } catch (_: SecurityException) {
            return false
        } catch (_: Exception) {
            return false
        }
    }

    /**
     * Stops scanning for nearby BLE nodes.
     */
    @Synchronized
    fun stopScanning() {
        if (isScanning) {
            try {
                scanner?.stopScan(scanCallback)
            } catch (_: SecurityException) {}
            isScanning = false
        }
    }

    private fun handleScanResult(result: ScanResult) {
        val serviceData = result.scanRecord?.getServiceData(PARCEL_UUID) ?: return
        val callSignPrefix = String(serviceData, Charsets.UTF_8)
        val rssi = result.rssi
        val deviceAddress = result.device.address

        scope.launch {
            _discoveredBleNodes.emit(
                BleRavenNode(
                    callSign = "raven://$callSignPrefix",
                    macAddress = deviceAddress,
                    rssi = rssi,
                    lastDiscoveredMs = System.currentTimeMillis()
                )
            )
        }
    }
}

data class BleRavenNode(
    val callSign: String,
    val macAddress: String,
    val rssi: Int,
    val lastDiscoveredMs: Long
)
