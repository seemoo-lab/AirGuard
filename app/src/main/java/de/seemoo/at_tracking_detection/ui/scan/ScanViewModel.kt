package de.seemoo.at_tracking_detection.ui.scan

import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.map
import dagger.hilt.android.lifecycle.HiltViewModel
import de.seemoo.at_tracking_detection.database.models.Scan
import de.seemoo.at_tracking_detection.database.repository.ScanRepository
import de.seemoo.at_tracking_detection.util.SharedPrefs
import timber.log.Timber
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import javax.inject.Inject

@HiltViewModel
class ScanViewModel @Inject constructor(private val scanRepository: ScanRepository) : ViewModel() {

    val bluetoothDeviceList = MutableLiveData<MutableList<ScanResult>>()

    val scanFinished = MutableLiveData(false)

    val scanStart = MutableLiveData(LocalDateTime.MIN)

    val isScanningInBackground = SharedPrefs.isScanningInBackground

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

    suspend fun saveScanToRepository() {
        if (scanStart.value == LocalDateTime.MIN) { return }
        val duration: Int  = ChronoUnit.SECONDS.between(scanStart.value, LocalDateTime.now()).toInt()
        val scan = Scan(endDate = LocalDateTime.now(), bluetoothDeviceList.value?.size ?: 0, duration, isManual = true, scanMode = ScanSettings.SCAN_MODE_LOW_LATENCY, startDate = scanStart.value ?: LocalDateTime.now())
        scanRepository.insert(scan)
    }
}