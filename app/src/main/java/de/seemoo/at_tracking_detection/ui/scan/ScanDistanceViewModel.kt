package de.seemoo.at_tracking_detection.ui.scan

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import de.seemoo.at_tracking_detection.database.models.device.BatteryState
import de.seemoo.at_tracking_detection.database.models.device.ConnectionState
import de.seemoo.at_tracking_detection.util.ble.BLEScanner

class ScanDistanceViewModel : ViewModel() {
    var bluetoothRssi = MutableLiveData<Int>()
    var deviceAddress = MutableLiveData<String>()
    var connectionStateString = MutableLiveData<String>()
    var connectionState = MutableLiveData<ConnectionState>()
    var batteryStateString = MutableLiveData<String>()
    var batteryState = MutableLiveData<BatteryState>()
    var connectionQuality = MutableLiveData<Int>()
    var isFirstScanCallback = MutableLiveData<Boolean>(true)

    var bluetoothEnabled = MutableLiveData(true)
    init {
        bluetoothEnabled.value = BLEScanner.isBluetoothOn()
    }
}