package de.seemoo.at_tracking_detection.ui.tracking

import androidx.lifecycle.*
import de.seemoo.at_tracking_detection.database.models.Beacon
import de.seemoo.at_tracking_detection.database.models.device.BaseDevice
import de.seemoo.at_tracking_detection.database.models.device.Connectable
import de.seemoo.at_tracking_detection.database.models.device.DeviceType
import de.seemoo.at_tracking_detection.database.repository.BeaconRepository
import de.seemoo.at_tracking_detection.database.repository.DeviceRepository
import de.seemoo.at_tracking_detection.database.repository.NotificationRepository
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

class TrackingViewModel @Inject constructor(
    private val notificationRepository: NotificationRepository,
    private val beaconRepository: BeaconRepository,
    private val deviceRepository: DeviceRepository,
) : ViewModel() {

    val deviceAddress = MutableLiveData<String>()

    val notificationId = MutableLiveData<Int>()

    val noLocationsYet = MutableLiveData(true)

    val error = MutableLiveData(false)

    val falseAlarm = MutableLiveData(false)
    val deviceIgnored = MutableLiveData(false)

    val soundPlaying = MutableLiveData(false)
    val connecting = MutableLiveData(false)

    val device = MutableLiveData<BaseDevice>()
    val connectable = MutableLiveData(false)

    val canBeIgnored = MutableLiveData(false)

    val showNfcHint = MutableLiveData(false)

    val isMapLoading = MutableLiveData(false)

    val markerLocations: LiveData<List<Beacon>> = deviceAddress.map {
        beaconRepository.getDeviceBeacons(it)
    }

    val beaconsHaveMissingLocation: LiveData<Boolean> = markerLocations.map {
        it.any { beacon ->
            beacon.locationId == null
        }
    }

    val amountBeacons: LiveData<String> = markerLocations.map {
        it.size.toString()
    }

    fun loadDevice(address: String) =
        deviceRepository.getDevice(address).also { it ->
            device.postValue(it)
            if (it != null) {
                deviceIgnored.postValue(it.ignore)
                noLocationsYet.postValue(false)
                connectable.postValue(it.device is Connectable)
                showNfcHint.postValue(it.deviceType == DeviceType.AIRTAG)
                val deviceType = it.deviceType
                if (deviceType != null) {
                    this.canBeIgnored.postValue(deviceType.canBeIgnored())
                }
                val notification = notificationRepository.notificationForDevice(it).firstOrNull()
                notification?.let { notificationId.postValue(it.notificationId) }
            } else {
                noLocationsYet.postValue(true)
            }
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
}