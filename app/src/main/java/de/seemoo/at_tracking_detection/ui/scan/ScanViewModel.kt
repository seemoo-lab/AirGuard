package de.seemoo.at_tracking_detection.ui.scan

import android.bluetooth.le.ScanResult
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.map
import timber.log.Timber

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
        val bluetoothDeviceListValue = bluetoothDeviceList.value ?: return
        bluetoothDeviceListValue.removeIf { it.device.address == scanResult.device.address }
        bluetoothDeviceListValue.add(scanResult)
        bluetoothDeviceListValue.sortByDescending { it.rssi }
        bluetoothDeviceList.postValue(bluetoothDeviceListValue)
        Timber.d("Adding scan result ${scanResult.device.address}")
        Timber.d(
            "status bytes: ${
                scanResult.scanRecord?.manufacturerSpecificData?.get(76)?.get(2)?.toString(2)
            }"
        )
        Timber.d("Device list: ${bluetoothDeviceList.value?.count()}")
    }

    val isListEmpty: LiveData<Boolean> = bluetoothDeviceList.map { it.isEmpty() }

    val listSize: LiveData<Int> = bluetoothDeviceList.map { it.size }

}