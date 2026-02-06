package de.seemoo.at_tracking_detection.ui.scan

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.seemoo.at_tracking_detection.database.models.device.BaseDevice
import de.seemoo.at_tracking_detection.database.models.device.BatteryState
import de.seemoo.at_tracking_detection.database.models.device.ConnectionState
import de.seemoo.at_tracking_detection.util.ble.BLEScanner
import de.seemoo.at_tracking_detection.util.ble.BluetoothEvent
import de.seemoo.at_tracking_detection.util.ble.BluetoothEventManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ScanDistanceViewModel @Inject constructor(
    private val bluetoothEventManager: BluetoothEventManager,
): ViewModel() {
    // var bluetoothRssi = MutableLiveData<Int>()
    var displayName = MutableLiveData<String>("UNKNOWN")
    var deviceAddress = MutableLiveData<String>()
    var connectionStateString = MutableLiveData<String>()
    var connectionState = MutableLiveData<ConnectionState>()
    var batteryStateString = MutableLiveData<String>()
    var batteryState = MutableLiveData<BatteryState>()
    var connectionQuality = MutableLiveData<Int>()
    var isFirstScanCallback = MutableLiveData<Boolean>(true)

    // Sound Playback LiveData
    val soundPlaying = MutableLiveData(false)
    val connecting = MutableLiveData(false)
    val error = MutableLiveData(false)
    val currentDevice = MutableLiveData<BaseDevice?>()

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

        // Observe Bluetooth events and update the UI state
        viewModelScope.launch {
            bluetoothEventManager.events.collectLatest { event ->
                when (event) {
                    BluetoothEvent.Connecting -> {
                        // Event is sent by BluetoothLeService
                    }
                    BluetoothEvent.EventRunning -> {
                        soundPlaying.postValue(true)
                        connecting.postValue(false)
                    }
                    BluetoothEvent.Disconnected -> {
                        soundPlaying.postValue(false)
                        connecting.postValue(false)
                    }
                    BluetoothEvent.EventFailed -> {
                        error.postValue(true)
                        connecting.postValue(false)
                        soundPlaying.postValue(false)
                    }
                    BluetoothEvent.EventCompleted -> {
                        soundPlaying.postValue(false)
                    }
                }
            }
        }
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