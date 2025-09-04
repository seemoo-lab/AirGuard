package de.seemoo.at_tracking_detection.ui.tracking

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.*
import de.seemoo.at_tracking_detection.database.models.Beacon
import de.seemoo.at_tracking_detection.database.models.device.BaseDevice
import de.seemoo.at_tracking_detection.database.models.device.Connectable
import de.seemoo.at_tracking_detection.database.models.device.ConnectionState
import de.seemoo.at_tracking_detection.database.models.device.DeviceManager
import de.seemoo.at_tracking_detection.database.models.device.DeviceType
import de.seemoo.at_tracking_detection.database.repository.BeaconRepository
import de.seemoo.at_tracking_detection.database.repository.DeviceRepository
import de.seemoo.at_tracking_detection.database.repository.NotificationRepository
import de.seemoo.at_tracking_detection.util.SharedPrefs
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject

class TrackingViewModel @Inject constructor(
    private val notificationRepository: NotificationRepository,
    private val beaconRepository: BeaconRepository,
    private val deviceRepository: DeviceRepository,
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

    val markerLocations: LiveData<List<Beacon>> = deviceAddress.map {
        beaconRepository.getDeviceBeacons(it)
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

    fun loadDevice(address: String, deviceTypeOverride: DeviceType) =
        deviceRepository.getDevice(address).also { device ->
            this.device.postValue(device)
            deviceType.value = deviceTypeOverride

            Timber.d("Set Device type: ${deviceType.value}")

            if (device != null) {
                deviceType.value = device.device.deviceContext.deviceType // This line is still necessary for the Device List in Expert Mode
                val deviceObserved = device.nextObservationNotification != null && device.nextObservationNotification!!.isAfter(
                    LocalDateTime.now())
                trackerObserved.postValue(deviceObserved)
                deviceIgnored.postValue(device.ignore)
                noLocationsYet.postValue(false)
                connectable.postValue(device.device is Connectable)
                canBeIgnored.postValue(deviceType.value!!.canBeIgnored(ConnectionState.OVERMATURE_OFFLINE))
                val notification = notificationRepository.notificationForDevice(device).firstOrNull()
                notification?.let { notificationId.postValue(it.notificationId) }

                // Load last seen times
                viewModelScope.launch {
                    loadLastSeenTimes(device)
                    expertMode.postValue(SharedPrefs.advancedMode)
                }

                deviceComment.postValue(device.comment ?: "")
            } else {
                noLocationsYet.postValue(true)
                deviceComment.postValue("")
            }
            showNfcHint.postValue(deviceType.value == DeviceType.AIRTAG)
            if (deviceType.value != null) {
                val websiteURL = DeviceManager.getWebsiteURL(deviceType.value!!)
                manufacturerWebsiteUrl.postValue(websiteURL)
            } else {
                manufacturerWebsiteUrl.postValue("")
            }
        }

    private fun loadLastSeenTimes(baseDevice: BaseDevice) {
        val beacons = beaconRepository.getDeviceBeacons(baseDevice.address)
        val lastSeenList = beacons.sortedByDescending { it.receivedAt }.take(5).map { beacon ->
            DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).format(beacon.receivedAt)
        }
        lastSeenTimes.postValue(lastSeenList)
    }

    fun toggleIgnoreDevice() {
        if (deviceAddress.value != null) {
            val newState = !deviceIgnored.value!!
            viewModelScope.launch {
                deviceRepository.setIgnoreFlag(deviceAddress.value!!, newState)
            }
            deviceIgnored.postValue(newState)
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
            val webpage: Uri = Uri.parse(manufacturerWebsiteUrl.value)
            val intent = Intent(Intent.ACTION_VIEW, webpage)
            context.startActivity(intent)
        }
    }

    // Add function to update comment and save to DB
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
}