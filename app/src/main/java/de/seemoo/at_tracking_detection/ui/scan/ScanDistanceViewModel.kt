package de.seemoo.at_tracking_detection.ui.scan

import android.bluetooth.le.ScanResult
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.map
import de.seemoo.at_tracking_detection.database.models.device.types.SamsungDevice.Companion.getPublicKey

class ScanDistanceViewModel : ViewModel() {
    var bluetoothRssi = MutableLiveData<Int>()
    var deviceAddress = MutableLiveData<String>()
    var connectionState = MutableLiveData<String>()
    var batteryState = MutableLiveData<String>()

}