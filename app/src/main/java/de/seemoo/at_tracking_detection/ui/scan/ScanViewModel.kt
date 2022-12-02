package de.seemoo.at_tracking_detection.ui.scan

import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.location.LocationManager
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.map
import dagger.hilt.android.lifecycle.HiltViewModel
import de.seemoo.at_tracking_detection.database.models.Scan
import de.seemoo.at_tracking_detection.database.repository.BeaconRepository
import de.seemoo.at_tracking_detection.database.repository.DeviceRepository
import de.seemoo.at_tracking_detection.database.repository.LocationRepository
import de.seemoo.at_tracking_detection.database.repository.ScanRepository
import de.seemoo.at_tracking_detection.detection.LocationProvider
import de.seemoo.at_tracking_detection.detection.ScanBluetoothWorker
import de.seemoo.at_tracking_detection.util.SharedPrefs
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import timber.log.Timber
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.*
import javax.inject.Inject
import kotlin.collections.ArrayList

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val scanRepository: ScanRepository,
    private val deviceRepository: DeviceRepository,
    private val locationRepository: LocationRepository,
    private val beaconRepository: BeaconRepository,
    private val locationProvider: LocationProvider,
    ) : ViewModel() {

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
        locationProvider.getCurrentLocation { loc ->
            Timber.d("Got location $loc in ScanViewModel")

            MainScope().async {
                ScanBluetoothWorker.insertScanResult(
                    scanResult = scanResult,
                    latitude = loc?.latitude,
                    longitude = loc?.longitude,
                    accuracy = loc?.accuracy,
                    discoveryDate = LocalDateTime.now(),
                    beaconRepository = beaconRepository,
                    deviceRepository = deviceRepository,
                    locationRepository = locationRepository,
                )
                }
            }

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

    suspend fun saveScanToRepository(){
        if (scanStart.value == LocalDateTime.MIN) { return }
        val duration: Int  = ChronoUnit.SECONDS.between(scanStart.value, LocalDateTime.now()).toInt()
        val scan = Scan(endDate = LocalDateTime.now(), bluetoothDeviceList.value?.size ?: 0, duration, isManual = true, scanMode = ScanSettings.SCAN_MODE_LOW_LATENCY, startDate = scanStart.value ?: LocalDateTime.now())
        scanRepository.insert(scan)
    }
}