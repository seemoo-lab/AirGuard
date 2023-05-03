package de.seemoo.at_tracking_detection.ui.scan

import android.bluetooth.le.ScanResult
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.map
import dagger.hilt.android.lifecycle.HiltViewModel
import de.seemoo.at_tracking_detection.database.models.device.BaseDevice
import de.seemoo.at_tracking_detection.database.models.device.DeviceManager
import de.seemoo.at_tracking_detection.database.models.device.BaseDevice.Companion.getPublicKey
import de.seemoo.at_tracking_detection.database.repository.BeaconRepository
import de.seemoo.at_tracking_detection.database.repository.ScanRepository
import de.seemoo.at_tracking_detection.detection.LocationProvider
import de.seemoo.at_tracking_detection.detection.ScanBluetoothWorker
import de.seemoo.at_tracking_detection.detection.ScanBluetoothWorker.Companion.TIME_BETWEEN_BEACONS
import de.seemoo.at_tracking_detection.util.SharedPrefs
import de.seemoo.at_tracking_detection.util.ble.BLEScanner
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import timber.log.Timber
import java.time.LocalDateTime
import javax.inject.Inject

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val scanRepository: ScanRepository,
    private val beaconRepository: BeaconRepository,
    private val locationProvider: LocationProvider,
    ) : ViewModel() {

    val bluetoothDeviceList = MutableLiveData<MutableList<ScanResult>>()

    val scanFinished = MutableLiveData(false)

    val scanStart = MutableLiveData(LocalDateTime.MIN)

    var bluetoothEnabled = MutableLiveData<Boolean>(true)
    init {
        bluetoothDeviceList.value = ArrayList()
        bluetoothEnabled.value = BLEScanner.isBluetoothOn()
    }

    private fun <T> MutableLiveData<T>.notifyObserver() {
        this.value = this.value
    }

    fun addScanResult(scanResult: ScanResult) {
        if (scanFinished.value == true) {
            return
        }

        val currentDate = LocalDateTime.now()
        val uniqueIdentifier = getPublicKey(scanResult) // either public key or MAC-Address
        if (beaconRepository.getNumberOfBeaconsAddress(
            deviceAddress = uniqueIdentifier,
            since = currentDate.minusMinutes(TIME_BETWEEN_BEACONS)
        ) == 0) {
            // There was no beacon with the address saved in the last IME_BETWEEN_BEACONS minutes

            val location = locationProvider.getLastLocation() // if not working: checkRequirements = false
            Timber.d("Got location $location in ScanViewModel")

            MainScope().async {
                ScanBluetoothWorker.insertScanResult(
                    scanResult = scanResult,
                    latitude = location?.latitude,
                    longitude = location?.longitude,
                    accuracy = location?.accuracy,
                    discoveryDate = currentDate,
                )
            }
        }

        val bluetoothDeviceListValue = bluetoothDeviceList.value ?: return
        bluetoothDeviceListValue.removeIf {
            getPublicKey(it) == uniqueIdentifier
        }

        if (SharedPrefs.showConnectedDevices || BaseDevice.getConnectionState(scanResult) in DeviceManager.savedConnectionStates) {
            // only add possible devices to list
            bluetoothDeviceListValue.add(scanResult)
        }

        if (!SharedPrefs.showConnectedDevices){
            // Do not show connected devices when criteria is met
            bluetoothDeviceListValue.removeIf {
                BaseDevice.getConnectionState(it) !in DeviceManager.savedConnectionStates
            }
        }

        // TODO:
        bluetoothDeviceListValue.sortByDescending { it.rssi }
        // bluetoothDeviceListValue.sortByDescending { it.device.address }

        bluetoothDeviceList.postValue(bluetoothDeviceListValue)
        Timber.d("Adding scan result ${scanResult.device.address} with unique identifier $uniqueIdentifier")
        Timber.d(
            "status bytes: ${
                scanResult.scanRecord?.manufacturerSpecificData?.get(76)?.get(2)?.toString(2)
            }"
        )
        Timber.d("Device list: ${bluetoothDeviceList.value?.count()}")
    }

    val isListEmpty: LiveData<Boolean> = bluetoothDeviceList.map { it.isEmpty() }

    val listSize: LiveData<Int> = bluetoothDeviceList.map { it.size }

    /* TODO: remove in future
    suspend fun saveScanToRepository(){
        // Not used anymore, because manual scan is always when the app is open
        if (scanStart.value == LocalDateTime.MIN) { return }
        val duration: Int  = ChronoUnit.SECONDS.between(scanStart.value, LocalDateTime.now()).toInt()
        val scan = Scan(endDate = LocalDateTime.now(), bluetoothDeviceList.value?.size ?: 0, duration, isManual = true, scanMode = ScanSettings.SCAN_MODE_LOW_LATENCY, startDate = scanStart.value ?: LocalDateTime.now())
        scanRepository.insert(scan)
    }
    */
}