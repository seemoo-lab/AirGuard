package de.seemoo.at_tracking_detection.ui.scan

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import de.seemoo.at_tracking_detection.database.models.device.BatteryState
import de.seemoo.at_tracking_detection.database.models.device.ConnectionState
import de.seemoo.at_tracking_detection.util.ble.BLEScanner

class ScanDistanceViewModel: ViewModel() {
    // var bluetoothRssi = MutableLiveData<Int>()
    var displayName = MutableLiveData<String>("UNKNOWN")
    var deviceAddress = MutableLiveData<String>()
    var connectionStateString = MutableLiveData<String>()
    var connectionState = MutableLiveData<ConnectionState>()
    var batteryStateString = MutableLiveData<String>()
    var batteryState = MutableLiveData<BatteryState>()
    var connectionQuality = MutableLiveData<Int>()
    var isFirstScanCallback = MutableLiveData<Boolean>(true)

    var bluetoothEnabled = MutableLiveData(true)
    var locationEnabled = MutableLiveData(true)

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
        val btOn = de.seemoo.at_tracking_detection.util.ble.BluetoothStateMonitor.isBluetoothEnabled()
        bluetoothEnabled.value = btOn

        val locOn = de.seemoo.at_tracking_detection.util.ble.LocationStateMonitor.isLocationEnabled()
        locationEnabled.value = locOn
    }

    fun stopMonitoringSystemToggles() {
        de.seemoo.at_tracking_detection.util.ble.BluetoothStateMonitor.removeListener(btListener)
    }

    fun startMonitoringSystemToggles() {
        de.seemoo.at_tracking_detection.util.ble.BluetoothStateMonitor.addListener(btListener)
        // Refresh location state whenever the view becomes visible
        locationEnabled.postValue(
            de.seemoo.at_tracking_detection.util.ble.LocationStateMonitor.isLocationEnabled()
        )
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
            de.seemoo.at_tracking_detection.util.ble.ScanOrchestrator.stopScan("ScanDistanceViewModel", null)
        } catch (_: Throwable) { /* ignore */ }
    }
}