package de.seemoo.at_tracking_detection.ui.scan

import android.bluetooth.le.ScanResult
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.seemoo.at_tracking_detection.database.models.device.BaseDevice
import de.seemoo.at_tracking_detection.database.models.device.DeviceManager
import de.seemoo.at_tracking_detection.database.models.device.DeviceType
import de.seemoo.at_tracking_detection.database.models.device.DeviceType.Companion.getAllowedDeviceTypesFromSettings
import de.seemoo.at_tracking_detection.database.models.device.types.GoogleFindMyNetwork
import de.seemoo.at_tracking_detection.database.models.device.types.SamsungTrackerType
import de.seemoo.at_tracking_detection.database.repository.BeaconRepository
import de.seemoo.at_tracking_detection.database.repository.DeviceRepository
import de.seemoo.at_tracking_detection.detection.BackgroundBluetoothScanner
import de.seemoo.at_tracking_detection.detection.BackgroundBluetoothScanner.TIME_BETWEEN_BEACONS
import de.seemoo.at_tracking_detection.detection.LocationProvider
import de.seemoo.at_tracking_detection.util.Utility
import de.seemoo.at_tracking_detection.util.Utility.LocationLogger
import de.seemoo.at_tracking_detection.util.ble.BLEScanner
import de.seemoo.at_tracking_detection.util.privacyPrint
import de.seemoo.at_tracking_detection.util.risk.RiskLevelEvaluator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.LocalDateTime
import javax.inject.Inject

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val deviceRepository: DeviceRepository,
    private val beaconRepository: BeaconRepository,
    private val locationProvider: LocationProvider,
) : ViewModel() {
    val bluetoothDeviceListHighRisk = MutableLiveData<MutableList<ScanResultWrapper>>()
    val bluetoothDeviceListLowRisk = MutableLiveData<MutableList<ScanResultWrapper>>()

    val scanFinished = MutableLiveData(false)

    val bluetoothEnabled = MutableLiveData(true)
    val locationEnabled = MutableLiveData(true)

    private val btListener = object : de.seemoo.at_tracking_detection.util.ble.BluetoothStateMonitor.Listener {
        override fun onBluetoothStateChanged(enabled: Boolean) {
            bluetoothEnabled.postValue(enabled)
            if (!enabled) {
                // Stop any running scan immediately if Bluetooth turns off
                stopForegroundScanIfAny()
            }
        }
    }

    init {
        // Initial values: query system state synchronously
        bluetoothDeviceListHighRisk.value = mutableListOf()
        bluetoothDeviceListLowRisk.value = mutableListOf()

        val btOn = de.seemoo.at_tracking_detection.util.ble.BluetoothStateMonitor.isBluetoothEnabled()
        bluetoothEnabled.value = btOn

        val locOn = de.seemoo.at_tracking_detection.util.ble.LocationStateMonitor.isLocationEnabled()
        locationEnabled.value = locOn
    }

    fun startMonitoringSystemToggles() {
        de.seemoo.at_tracking_detection.util.ble.BluetoothStateMonitor.addListener(btListener)
        // Refresh location state whenever the view becomes visible
        locationEnabled.postValue(
            de.seemoo.at_tracking_detection.util.ble.LocationStateMonitor.isLocationEnabled()
        )
    }

    fun stopMonitoringSystemToggles() {
        de.seemoo.at_tracking_detection.util.ble.BluetoothStateMonitor.removeListener(btListener)
    }

    fun refreshLocationState() {
        val locOn = de.seemoo.at_tracking_detection.util.ble.LocationStateMonitor.isLocationEnabled()
        val previous = locationEnabled.value
        locationEnabled.postValue(locOn)
        if (previous == true && !locOn) {
            // Stop any running scan immediately if Location becomes off and your feature requires it
            stopForegroundScanIfAny()
        }
    }

    private fun stopForegroundScanIfAny() {
        // Foreground BLEScanner is your UI-time scanner; stop via orchestrator safely
        try {
            BLEScanner.unregisterAllForViewModelStop()
        } catch (_: Throwable) { /* ignore */ }
        try {
            de.seemoo.at_tracking_detection.util.ble.ScanOrchestrator.stopScan("ScanViewModel", null)
        } catch (_: Throwable) { /* ignore */ }
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
        } else if (wrappedScanResult.deviceType == DeviceType.GOOGLE_FIND_MY_NETWORK) {
            // If a Google Tracker is a phone or a tracker can be determined while the scan is happening without the need to connect to said tracker
            val googleSubType = GoogleFindMyNetwork.getSubType(wrappedScanResult)
            ScanFragment.googleSubDeviceTypeMap[wrappedScanResult.uniqueIdentifier] = googleSubType
        } else if (wrappedScanResult.deviceType == DeviceType.SAMSUNG_TRACKER && wrappedScanResult.advertisedName == "Smart Tag2") {
            // The SmartTag 2 sometimes advertises its Name, so we can set the subtype directly here
            ScanFragment.samsungSubDeviceTypeMap[wrappedScanResult.uniqueIdentifier] = SamsungTrackerType.SMART_TAG_2
        }

        val currentDate = LocalDateTime.now()
        if (beaconRepository.getNumberOfBeaconsAddress(
                deviceAddress = wrappedScanResult.uniqueIdentifier,
                since = currentDate.minusMinutes(TIME_BETWEEN_BEACONS)
            ) == 0) {
            val skipDevice = Utility.getSkipDevice(wrappedScanResult = wrappedScanResult)
            if (skipDevice) {
                Timber.d("Skipping device ${wrappedScanResult.uniqueIdentifier}")
                return@launch
            }

            // There was no beacon with the address saved in the last IME_BETWEEN_BEACONS minutes
            LocationLogger.log("ScanViewModel: Request Location from Manual Scan")
            val location = locationProvider.getLastLocation() // if not working: checkRequirements = false
            Timber.d("Got location $location in ScanViewModel")

            if (location == null) {
                LocationLogger.log("ScanViewModel: Location could not be retrieved, Location is null")
            } else {
                LocationLogger.log("ScanViewModel: Got Location: ${location.privacyPrint()}, Altitude: ${location.altitude}, Accuracy: ${location.accuracy}")
            }

            BackgroundBluetoothScanner.insertScanResult(
                wrappedScanResult = wrappedScanResult,
                latitude = location?.latitude,
                longitude = location?.longitude,
                altitude = location?.altitude,
                accuracy = location?.accuracy,
                discoveryDate = currentDate,
            )
        }

        val device: BaseDevice? = deviceRepository.getDevice(wrappedScanResult.uniqueIdentifier)

        val isElementHighRisk = isElementHighRisk(device, wrappedScanResult)

        if (isElementHighRisk) {
            val elementHighRisk: ScanResultWrapper? = bluetoothDeviceListHighRiskValue.find {
                it.uniqueIdentifier == wrappedScanResult.uniqueIdentifier
            }

            if (elementHighRisk != null) {
                elementHighRisk.rssi.set(wrappedScanResult.rssi.get())
                elementHighRisk.rssiValue = wrappedScanResult.rssiValue
                elementHighRisk.txPower = wrappedScanResult.txPower
                elementHighRisk.isConnectable = wrappedScanResult.isConnectable
            } else {
                // only add possible devices to list
                bluetoothDeviceListHighRiskValue.add(wrappedScanResult)
            }

        } else {
            val elementLowRisk: ScanResultWrapper? = bluetoothDeviceListLowRiskValue.find {
                it.uniqueIdentifier == wrappedScanResult.uniqueIdentifier
            }

            if (elementLowRisk != null) {
                elementLowRisk.rssi.set(wrappedScanResult.rssi.get())
                elementLowRisk.rssiValue = wrappedScanResult.rssiValue
                elementLowRisk.txPower = wrappedScanResult.txPower
                elementLowRisk.isConnectable = wrappedScanResult.isConnectable
            } else {
                // only add possible devices to list
                bluetoothDeviceListLowRiskValue.add(wrappedScanResult)
            }
        }

        // Sorting list by detection date is not so restless
//        bluetoothDeviceListHighRiskValue.sortByDescending { it.rssiValue }
//        bluetoothDeviceListLowRiskValue.sortByDescending { it.rssiValue }

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
            val highRiskEmpty = bluetoothDeviceListHighRisk.value?.isEmpty() != false
            val lowRiskEmpty = bluetoothDeviceListLowRisk.value?.isEmpty() != false
            this.value = highRiskEmpty && lowRiskEmpty
        }

        addSource(bluetoothDeviceListHighRisk) { update() }
        addSource(bluetoothDeviceListLowRisk) { update() }
    }

    val lowRiskIsEmpty: LiveData<Boolean> = MediatorLiveData<Boolean>().apply {
        // Function to update the isListEmpty LiveData
        fun update() {
            val lowRiskEmpty = bluetoothDeviceListLowRisk.value?.isEmpty() != false
            this.value = lowRiskEmpty
        }

        addSource(bluetoothDeviceListLowRisk) { update() }
    }

    fun isElementHighRisk (device: BaseDevice?, wrappedScanResult: ScanResultWrapper): Boolean {
        val isUnsafeConnectionState = wrappedScanResult.connectionState in DeviceManager.unsafeConnectionState
        val deviceIsIgnored = device?.ignore == true
        val deviceIsSafeTracker = device?.safeTracker == true
        val notificationSent = device?.notificationSent == true
        val notificationRecent = device?.lastNotificationSent?.isAfter(LocalDateTime.now().minusDays(RiskLevelEvaluator.RELEVANT_DAYS_RISK_LEVEL)) == true
        val riskLevelExistent = (device?.riskLevel ?: 0) > 0

        return if (deviceIsIgnored) {
            // Highest Priority: If user ignores device it is automatically low risk
            false
        } else if (deviceIsSafeTracker) {
            // If device is a safe tracker it is automatically low risk
            false
        } else if (isUnsafeConnectionState) {
            // Every unsafeConnectionState is potentially high risk
            true
        } else if (notificationSent && notificationRecent) {
            // This case is relevant for the 15 minute algorithm
            true
        } else if (riskLevelExistent) {
            // This case is relevant for the 15 minute algorithm
            true
        } else {
            false
        }
    }
}