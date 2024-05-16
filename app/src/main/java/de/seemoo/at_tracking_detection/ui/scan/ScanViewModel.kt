package de.seemoo.at_tracking_detection.ui.scan

import android.bluetooth.le.ScanResult
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication
import de.seemoo.at_tracking_detection.database.models.device.DeviceManager
import de.seemoo.at_tracking_detection.database.models.device.DeviceType.Companion.getAllowedDeviceTypesFromSettings
import de.seemoo.at_tracking_detection.database.repository.BeaconRepository
import de.seemoo.at_tracking_detection.database.repository.ScanRepository
import de.seemoo.at_tracking_detection.detection.BackgroundBluetoothScanner
import de.seemoo.at_tracking_detection.detection.BackgroundBluetoothScanner.TIME_BETWEEN_BEACONS
import de.seemoo.at_tracking_detection.detection.LocationProvider
import de.seemoo.at_tracking_detection.util.ble.BLEScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.LocalDateTime
import javax.inject.Inject

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val scanRepository: ScanRepository,
    private val beaconRepository: BeaconRepository,
    private val locationProvider: LocationProvider,
) : ViewModel() {
    val bluetoothDeviceListHighRisk = MutableLiveData<MutableList<ScanResultWrapper>>()
    val bluetoothDeviceListLowRisk = MutableLiveData<MutableList<ScanResultWrapper>>()

    val scanFinished = MutableLiveData(false)

    var bluetoothEnabled = MutableLiveData(true)
    init {
        bluetoothDeviceListHighRisk.value = mutableListOf()
        bluetoothDeviceListLowRisk.value = mutableListOf()
        bluetoothEnabled.value = BLEScanner.isBluetoothOn()
    }

    fun addScanResult(scanResult: ScanResult) = viewModelScope.launch(Dispatchers.IO) {
        if (scanFinished.value == true) {
            return@launch
        }

        val wrappedScanResult = ScanResultWrapper(scanResult)

        val bluetoothDeviceListHighRiskValue = bluetoothDeviceListHighRisk.value?.toMutableList() ?: mutableListOf()
        val bluetoothDeviceListLowRiskValue = bluetoothDeviceListLowRisk.value?.toMutableList() ?: mutableListOf()

        val validDeviceTypes = getAllowedDeviceTypesFromSettings()

        if (wrappedScanResult.deviceType !in validDeviceTypes) {
            // If device not selected in settings then do not add ScanResult to list or database
            return@launch
        }

        val currentDate = LocalDateTime.now()
        if (beaconRepository.getNumberOfBeaconsAddress(
                deviceAddress = wrappedScanResult.uniqueIdentifier,
                since = currentDate.minusMinutes(TIME_BETWEEN_BEACONS)
            ) == 0) {
            // There was no beacon with the address saved in the last IME_BETWEEN_BEACONS minutes
            val location = locationProvider.getLastLocation() // if not working: checkRequirements = false
            Timber.d("Got location $location in ScanViewModel")

            BackgroundBluetoothScanner.insertScanResult(
                scanResult = scanResult,
                latitude = location?.latitude,
                longitude = location?.longitude,
                altitude = location?.altitude,
                accuracy = location?.accuracy,
                discoveryDate = currentDate,
            )
        }

        val deviceRepository = ATTrackingDetectionApplication.getCurrentApp().deviceRepository
        val device = deviceRepository.getDevice(wrappedScanResult.uniqueIdentifier)

        val elementHighRisk: ScanResultWrapper? = bluetoothDeviceListHighRiskValue.find {
            it.uniqueIdentifier == wrappedScanResult.uniqueIdentifier
        }
        val elementLowRisk: ScanResultWrapper? = bluetoothDeviceListLowRiskValue.find {
            it.uniqueIdentifier == wrappedScanResult.uniqueIdentifier
        }

        if (elementHighRisk != null) {
            elementHighRisk.rssi.set(wrappedScanResult.rssi.get())
            elementHighRisk.rssiValue = wrappedScanResult.rssiValue
            elementHighRisk.txPower = wrappedScanResult.txPower
            elementHighRisk.isConnectable = wrappedScanResult.isConnectable
        } else if (wrappedScanResult.connectionState in DeviceManager.unsafeConnectionState && ((device != null && !device.ignore) || device==null)) {
            // only add possible devices to list
            bluetoothDeviceListHighRiskValue.add(wrappedScanResult)
        }

        if (elementLowRisk != null) {
            elementLowRisk.rssi.set(wrappedScanResult.rssi.get())
            elementLowRisk.rssiValue = wrappedScanResult.rssiValue
            elementLowRisk.txPower = wrappedScanResult.txPower
            elementLowRisk.isConnectable = wrappedScanResult.isConnectable
        } else if ((wrappedScanResult.connectionState !in DeviceManager.unsafeConnectionState || (device == null || device.ignore)) && elementHighRisk == null ) {
            // only add possible devices to list
            bluetoothDeviceListLowRiskValue.add(wrappedScanResult)
        }

        bluetoothDeviceListHighRiskValue.sortByDescending { it.rssiValue }
        bluetoothDeviceListLowRiskValue.sortByDescending { it.rssiValue }

        bluetoothDeviceListHighRisk.postValue(bluetoothDeviceListHighRiskValue)
        bluetoothDeviceListLowRisk.postValue(bluetoothDeviceListLowRiskValue)

        Timber.d("Adding scan result ${scanResult.device.address} with unique identifier $wrappedScanResult.uniqueIdentifier")
        Timber.d(
            "status bytes: ${
                scanResult.scanRecord?.manufacturerSpecificData?.get(76)?.get(2)?.toString(2)
            }"
        )
        Timber.d("Device list (High Risk): ${bluetoothDeviceListHighRisk.value?.count()}")
        Timber.d("Device list (Low Risk): ${bluetoothDeviceListLowRisk.value?.count()}")
    }

    val isListEmpty: LiveData<Boolean> = MediatorLiveData<Boolean>().apply {
        // Function to update the isListEmpty LiveData
        fun update() {
            val highRiskEmpty = bluetoothDeviceListHighRisk.value?.isEmpty() ?: true
            val lowRiskEmpty = bluetoothDeviceListLowRisk.value?.isEmpty() ?: true
            this.value = highRiskEmpty && lowRiskEmpty
        }

        addSource(bluetoothDeviceListHighRisk) { update() }
        addSource(bluetoothDeviceListLowRisk) { update() }
    }
}