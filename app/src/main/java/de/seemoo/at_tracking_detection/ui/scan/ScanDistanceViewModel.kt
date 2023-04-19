package de.seemoo.at_tracking_detection.ui.scan

import android.bluetooth.le.ScanResult
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.map

class ScanDistanceViewModel : ViewModel() {
    private val bluetoothDevice = MutableLiveData<ScanResult>()

    private val bluetoothRssi = MutableLiveData<Int>()

    fun setScanResult(scanResult: ScanResult) {
        val bluetoothDeviceValue = bluetoothDevice.value ?: return // TODO: Is this even necessary????
        bluetoothDevice.postValue(bluetoothDeviceValue) // TODO: Is this even necessary????
        bluetoothRssi.postValue(scanResult.rssi)
    }

    init {
        bluetoothRssi.value = -1
    }

}