package de.seemoo.at_tracking_detection.ui.scan

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class ScanDistanceViewModel : ViewModel() {
    var bluetoothRssi = MutableLiveData<Int>()
    var deviceAddress = MutableLiveData<String>()
    var connectionState = MutableLiveData<String>()
    var batteryState = MutableLiveData<String>()
    var connectionQuality = MutableLiveData<Int>()

}