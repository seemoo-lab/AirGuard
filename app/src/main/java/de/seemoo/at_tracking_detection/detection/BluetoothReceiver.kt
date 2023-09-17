package de.seemoo.at_tracking_detection.detection

import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi
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

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            TrackingDetectorConstants.BLUETOOTH_DEVICE_FOUND_ACTION -> {
                val scanResult =
                    intent.getParcelableArrayListExtra<ScanResult>(BluetoothLeScanner.EXTRA_LIST_SCAN_RESULT)
                if (scanResult != null) {
                    for (result: ScanResult in scanResult) {
                        Timber.d("Found ${result.device.address}")
                    }
                }
            }
        }
    }
}