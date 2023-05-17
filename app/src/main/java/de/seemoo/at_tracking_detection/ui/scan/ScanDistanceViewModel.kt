package de.seemoo.at_tracking_detection.ui.scan

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import de.seemoo.at_tracking_detection.util.ble.BLEScanner

class ScanDistanceViewModel : ViewModel() {
    var bluetoothRssi = MutableLiveData<Int>()
    var deviceAddress = MutableLiveData<String>()
    var connectionState = MutableLiveData<String>()
    var batteryState = MutableLiveData<String>()
    var connectionQuality = MutableLiveData<Int>()

    var bluetoothEnabled = MutableLiveData(true)
    init {
        bluetoothEnabled.value = BLEScanner.isBluetoothOn()
    }
}