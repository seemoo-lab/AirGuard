package de.seemoo.at_tracking_detection.detection

import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.SharedPreferences
import android.location.Location
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import de.seemoo.at_tracking_detection.BuildConfig
import de.seemoo.at_tracking_detection.database.repository.BeaconRepository
import de.seemoo.at_tracking_detection.database.repository.DeviceRepository
import de.seemoo.at_tracking_detection.database.tables.Beacon
import de.seemoo.at_tracking_detection.database.tables.Device
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.LocalDateTime

@HiltWorker
class ScanBluetoothWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val beaconRepository: BeaconRepository,
    private val deviceRepository: DeviceRepository,
    private val locationProvider: LocationProvider,
    private val sharedPreferences: SharedPreferences
) :
    CoroutineWorker(appContext, workerParams) {

    private lateinit var bluetoothAdapter: BluetoothAdapter

    private lateinit var scanResults: ArrayList<ScanResult>

    private var location: Location? = null

    override suspend fun doWork(): Result {
        Timber.d("Bluetooth scanning worker started!")
        try {
            bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        } catch (e: Throwable) {
            Timber.e("BluetoothAdapter not found!")
            return Result.retry()
        }
        sharedPreferences.edit().putString("last_scan", LocalDateTime.now().toString()).apply()
        Timber.d("Start Scanning for bluetooth le devices...")
        scanResults = ArrayList()
        bluetoothAdapter.bluetoothLeScanner.startScan(
            buildFilter(),
            buildSettings(),
            leScanCallback
        )
        val useLocation = sharedPreferences.getBoolean("use_location", false)
        if (useLocation) {
            location = locationProvider.getCurrentLocation()
            Timber.d("Using location for beacons")
        }

        delay(SCAN_DURATION)
        Timber.d("Scanning for bluetooth le devices stopped!")
        bluetoothAdapter.bluetoothLeScanner.stopScan(leScanCallback)

        return Result.success()
    }

    private val leScanCallback: ScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, scanResult: ScanResult) {
            super.onScanResult(callbackType, scanResult)
            Timber.d("Found ${scanResult.device.address} at ${LocalDateTime.now()}")

            //Checks if the device has been found already
            if (!scanResults.any { it.device.address == scanResult.device.address }) {
                GlobalScope.launch {
                    Timber.i(" from ${scanResult.device.address}")
                    insertScanResult(
                        scanResult,
                        location?.latitude,
                        location?.longitude,
                        LocalDateTime.now()
                    )
                }
                scanResults.add(scanResult)
            }

        }
    }

    private suspend fun insertScanResult(
        scanResult: ScanResult,
        latitude: Double?,
        longitude: Double?,
        discoveryDate: LocalDateTime
    ) {
        val payloadData = scanResult.scanRecord?.manufacturerSpecificData?.get(76)?.get(2)
        var device = deviceRepository.getDevice(scanResult.device.address)
        Timber.d("Device: $device")
        if (device == null) {
            device = Device(
                scanResult.device.address,
                false,
                scanResult.isConnectable,
                payloadData,
                discoveryDate,
                discoveryDate
            )
            deviceRepository.insert(device)
        } else {
            Timber.d("Device already in the database... Updating the last seen date!")
            device.lastSeen = discoveryDate
            deviceRepository.update(device)
        }


        val beacon = if (BuildConfig.DEBUG) {
            // Save the manufacturer data to the beacon
            Beacon(
                discoveryDate, scanResult.rssi, scanResult.device.address, latitude, longitude,
                scanResult.scanRecord?.bytes
            )
        } else {
            Beacon(
                discoveryDate, scanResult.rssi, scanResult.device.address, latitude, longitude,
                null
            )
        }

        beaconRepository.insert(beacon)
    }

    private fun buildSettings() =
        ScanSettings.Builder().setScanMode(getScanMode()).build()

    private fun getScanMode(): Int {
        val useLowPower = sharedPreferences.getBoolean("use_low_power_ble", false)
        return if (useLowPower) {
            ScanSettings.SCAN_MODE_LOW_POWER
        } else {
            ScanSettings.SCAN_MODE_BALANCED
        }
    }

    private fun buildFilter() =
        mutableListOf<ScanFilter>(
            ScanFilter.Builder()
                .setManufacturerData(0x4C, byteArrayOf((0x12).toByte(), (0x19).toByte()))
                .build()
        )

    companion object {
        const val SCAN_DURATION = 15000L
    }
}