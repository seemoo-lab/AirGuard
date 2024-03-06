package de.seemoo.at_tracking_detection.detection

import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import dagger.hilt.android.AndroidEntryPoint
import de.seemoo.at_tracking_detection.database.repository.BeaconRepository
import de.seemoo.at_tracking_detection.database.repository.DeviceRepository
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class BluetoothReceiver : BroadcastReceiver() {

    @Inject
    lateinit var beaconRepository: BeaconRepository

    @Inject
    lateinit var deviceRepository: DeviceRepository

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            TrackingDetectorConstants.BLUETOOTH_DEVICE_FOUND_ACTION -> {
                val scanResult = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableArrayListExtra(BluetoothLeScanner.EXTRA_LIST_SCAN_RESULT, ScanResult::class.java)
                } else {
                    intent.getParcelableArrayListExtra(BluetoothLeScanner.EXTRA_LIST_SCAN_RESULT)
                }
                if (scanResult != null) {
                    for (result: ScanResult in scanResult) {
                        Timber.d("Found ${result.device.address}")
                    }
                }
            }
        }
    }
}