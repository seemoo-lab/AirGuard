package de.seemoo.at_tracking_detection.ui.scan

import android.bluetooth.le.ScanResult
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.map
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlin.math.pow

class ScanViewModel : ViewModel() {

    val bluetoothDeviceList = MutableLiveData<MutableList<ScanResult>>()

    val scanFinished = MutableLiveData(false)

    init {
        bluetoothDeviceList.value = ArrayList()
    }

    private fun <T> MutableLiveData<T>.notifyObserver() {
        this.value = this.value
    }

    fun addScanResult(scanResult: ScanResult) {
        bluetoothDeviceList.value?.removeIf() { it.device.address == scanResult.device.address }
        bluetoothDeviceList.value?.add(scanResult)
        bluetoothDeviceList.value?.sortByDescending { it.rssi }
        bluetoothDeviceList.notifyObserver()
    }

    val isListEmpty: LiveData<Boolean> = bluetoothDeviceList.map { it.isEmpty() }

    val listSize: LiveData<Int> = bluetoothDeviceList.map { it.size }

}