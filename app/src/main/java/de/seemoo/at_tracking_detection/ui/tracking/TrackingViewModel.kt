package de.seemoo.at_tracking_detection.ui.tracking

import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.map
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication
import de.seemoo.at_tracking_detection.database.models.Beacon
import de.seemoo.at_tracking_detection.database.models.device.BaseDevice
import de.seemoo.at_tracking_detection.database.models.device.Connectable
import de.seemoo.at_tracking_detection.database.models.device.ConnectionState
import de.seemoo.at_tracking_detection.database.models.device.DeviceManager
import de.seemoo.at_tracking_detection.database.models.device.DeviceType
import de.seemoo.at_tracking_detection.database.repository.BeaconRepository
import de.seemoo.at_tracking_detection.database.repository.DeviceRepository
import de.seemoo.at_tracking_detection.database.repository.NotificationRepository
import de.seemoo.at_tracking_detection.notifications.NotificationService
import de.seemoo.at_tracking_detection.util.SharedPrefs
import de.seemoo.at_tracking_detection.util.ble.BluetoothEvent
import de.seemoo.at_tracking_detection.util.ble.BluetoothEventManager
import de.seemoo.at_tracking_detection.util.risk.RiskLevelEvaluator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject

class TrackingViewModel @Inject constructor(
    private val notificationRepository: NotificationRepository,
    private val beaconRepository: BeaconRepository,
    private val deviceRepository: DeviceRepository,
    private val bluetoothEventManager: BluetoothEventManager,
) : ViewModel() {

    val deviceAddress = MutableLiveData<String>()

    val notificationId = MutableLiveData<Int>()

    val noLocationsYet = MutableLiveData(true)

    val manufacturerWebsiteUrl = MutableLiveData<String>()

    var deviceType = MutableLiveData<DeviceType>(DeviceType.UNKNOWN)

    val error = MutableLiveData(false)

    val falseAlarm = MutableLiveData(false)
    val deviceIgnored = MutableLiveData(false)
    val trackerObserved = MutableLiveData(false)

    val soundPlaying = MutableLiveData(false)
    val connecting = MutableLiveData(false)

    val device = MutableLiveData<BaseDevice?>()
    val connectable = MutableLiveData(false)

    val canBeIgnored = MutableLiveData(false)

    val showNfcHint = MutableLiveData(false)

    val isMapLoading = MutableLiveData(false)

    val isSafeTracker = MutableLiveData(false)

    val isInternetAvailable = MutableLiveData(true)

    val showMap = MutableLiveData(false) // Should be visible if: Locations in Db, Device is high risk, Internet available
    val showNoInternetWarning = MutableLiveData(false) // Should be visible if: Locations in Db, Device is high risk, no Internet available
    val showNoLocationsWarning = MutableLiveData(false) // Should be visible if: No locations in db, device is high risk
    val showSafeTrackerNoLocationsWarning = MutableLiveData(false) // Should be visible if: No locations in db, device is low risk
    val showSafeTrackerHasLocationsWarning = MutableLiveData(false) // Should be visible if: Locations in db, device is low risk


    // feedback: visible only if notification exists and sharing data is enabled
    val showFeedback = notificationId.map { id ->
        id != -1 && SharedPrefs.shareData
    }

    // Reactively update markers when beacons are written
    // This is relevant for the case when a user very quickly opens the map from the manual scan while the device and beacons are still beeing written
    val markerLocations: LiveData<List<Beacon>> = deviceAddress.switchMap { address ->
        beaconRepository.getDeviceBeaconsFlow(address).asLiveData()
    }

    val amountBeacons: LiveData<String> = markerLocations.map {
        it.size.toString()
    }

    val lastSeenTimes: MutableLiveData<List<String>> = MutableLiveData(emptyList())
    val lastSeenTimesString: LiveData<String> = lastSeenTimes.map {
        it.joinToString(separator = "\n")
    }

    val expertMode = MutableLiveData(false)

    val deviceComment = MutableLiveData<String>("")

    init {
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

    fun updateUIStates() {
        // Read directly from the LiveData values synchronously to prevent race conditions
        val isSafe = isSafeTracker.value ?: false
        val isIgnored = deviceIgnored.value ?: false
        val effectiveSafe = isSafe && !isIgnored

        val noLocs = noLocationsYet.value ?: true
        val internet = isInternetAvailable.value ?: false

        showMap.value = !noLocs && internet
        showNoInternetWarning.value = !noLocs && !internet

        // Warnings
        showNoLocationsWarning.value = !effectiveSafe && noLocs
        showSafeTrackerNoLocationsWarning.value = effectiveSafe && noLocs
        showSafeTrackerHasLocationsWarning.value = effectiveSafe && !noLocs
    }

    fun setInternetAvailable(available: Boolean) {
        isInternetAvailable.value = available
        updateUIStates()
    }

    fun loadDevice(address: String, deviceTypeOverride: DeviceType) {
        deviceAddress.postValue(address)
        deviceType.postValue(deviceTypeOverride)

        // Run DB operations on IO thread
        viewModelScope.launch(Dispatchers.IO) {
            deviceRepository.observeDevice(address).collectLatest { dev ->
                if (dev != null) {
                    val dType = dev.device.deviceContext.deviceType
                    val deviceObserved = dev.nextObservationNotification?.isAfter(LocalDateTime.now()) == true
                    val ignore = dev.ignore
                    val notification = notificationRepository.notificationForDevice(dev).firstOrNull()
                    val notifId = notification?.notificationId ?: -1
                    val isFalseAlarm = notification?.falseAlarm ?: false

                    // Fetch latest beacons and extract the most recent connection state
                    val beacons = beaconRepository.getDeviceBeacons(dev.address)
                    val latestBeacon = beacons.maxByOrNull { it.receivedAt }
                    val latestConnectionState = latestBeacon?.connectionState?.let {
                        try {
                            ConnectionState.valueOf(it)
                        } catch (e: Exception) {
                            ConnectionState.UNKNOWN
                        }
                    }

                    // Exactly replicate the ScanFragment logic to determine risk state
                    val isHighRisk = isElementHighRisk(dev, latestConnectionState)
                    val safeTrackerVal = !isHighRisk

                    val lastSeenList = beacons.sortedByDescending { it.receivedAt }.take(5).map { beacon ->
                        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).format(beacon.receivedAt)
                    }

                    // Switch context to Main Thread so LiveData sets and UI updates happen immediately
                    withContext(Dispatchers.Main) {
                        device.value = dev
                        deviceType.value = dType
                        trackerObserved.value = deviceObserved
                        deviceIgnored.value = ignore
                        noLocationsYet.value = false
                        connectable.value = dev.device is Connectable
                        canBeIgnored.value = dType.canBeIgnored(ConnectionState.OVERMATURE_OFFLINE)
                        if (notifId != -1) notificationId.value = notifId
                        falseAlarm.value = isFalseAlarm
                        isSafeTracker.value = safeTrackerVal
                        lastSeenTimes.value = lastSeenList
                        expertMode.value = SharedPrefs.advancedMode
                        deviceComment.value = dev.comment ?: ""
                        showNfcHint.value = (dType == DeviceType.AIRTAG)
                        manufacturerWebsiteUrl.value = DeviceManager.getWebsiteURL(dType)

                        // Recalculate visibility logic
                        updateUIStates()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        device.value = null
                        noLocationsYet.value = true

                        // not in  DB --> safe
                        isSafeTracker.value = true
                        deviceComment.value = ""
                        showNfcHint.value = (deviceTypeOverride == DeviceType.AIRTAG)
                        manufacturerWebsiteUrl.value = DeviceManager.getWebsiteURL(deviceTypeOverride)

                        // Recalculate visibility logic
                        updateUIStates()
                    }
                }
            }
        }
    }

    private fun isElementHighRisk(dev: BaseDevice?, latestConnectionState: ConnectionState?): Boolean {
        val isUnsafeConnectionState = latestConnectionState != null && latestConnectionState in DeviceManager.unsafeConnectionState
        val deviceIsIgnored = dev?.ignore == true
        val deviceIsSafeTracker = dev?.safeTracker == true
        val notificationSent = dev?.notificationSent == true
        val notificationRecent = dev?.lastNotificationSent?.isAfter(LocalDateTime.now().minusDays(
            RiskLevelEvaluator.RELEVANT_DAYS_RISK_LEVEL)) == true
        val riskLevelExistent = (dev?.riskLevel ?: 0) > 0

        return if (deviceIsIgnored) {
            false
        } else if (deviceIsSafeTracker) {
            false
        } else if (isUnsafeConnectionState) {
            true
        } else if (notificationSent && notificationRecent) {
            true
        } else if (riskLevelExistent) {
            true
        } else {
            false
        }
    }

    fun toggleIgnoreDevice() {
        if (deviceAddress.value != null) {
            val newState = !(deviceIgnored.value ?: false)
            viewModelScope.launch {
                deviceRepository.setIgnoreFlag(deviceAddress.value!!, newState)
            }
            deviceIgnored.value = newState
            updateUIStates() // Recalculate immediately
            Timber.d("Toggle ignore device - new State: $newState")
        }
    }

    fun toggleFalseAlarm() {
        if (notificationId.value != null) {
            val newState = !falseAlarm.value!!
            viewModelScope.launch {
                notificationRepository.setFalseAlarm(notificationId.value!!, newState)
            }
            falseAlarm.postValue(newState)
        }
    }

    fun clickOnWebsite(context: android.content.Context) {
        if (manufacturerWebsiteUrl.value != null) {
            Timber.d("Click on website: ${manufacturerWebsiteUrl.value}")
            val webpage: Uri = manufacturerWebsiteUrl.value!!.toUri()
            val intent = Intent(Intent.ACTION_VIEW, webpage)
            context.startActivity(intent)
        }
    }

    fun updateDeviceComment(newComment: String) {
        device.value?.let { baseDevice ->
            if (baseDevice.comment != newComment) {
                baseDevice.comment = newComment.ifBlank { null }
                viewModelScope.launch {
                    deviceRepository.update(baseDevice)
                }
            }
        }
        deviceComment.postValue(newComment)
    }

    suspend fun deleteDeviceAndRelatedData(): Boolean = withContext(Dispatchers.IO) {
        val address = deviceAddress.value ?: return@withContext false
        val baseDevice = deviceRepository.getDevice(address) ?: return@withContext false

        // cancel all remaining system notifications for this device
        runCatching {
            val nm = NotificationManagerCompat.from(ATTrackingDetectionApplication.getAppContext())
            notificationId.value?.let { id ->
                nm.cancel(NotificationService.TRACKING_NOTIFICATION_TAG, id)
            }
        }

        val beacons = beaconRepository.getDeviceBeacons(address)
        try {
            beaconRepository.deleteBeacons(beacons)
            notificationRepository.deleteForDevice(address)
            deviceRepository.delete(baseDevice)
            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to delete device")
            false
        }
    }
}