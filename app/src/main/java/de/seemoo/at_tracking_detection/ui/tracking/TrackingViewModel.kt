package de.seemoo.at_tracking_detection.ui.tracking

import android.content.Intent
import androidx.lifecycle.*
import de.seemoo.at_tracking_detection.database.repository.BeaconRepository
import de.seemoo.at_tracking_detection.database.repository.DeviceRepository
import de.seemoo.at_tracking_detection.database.repository.NotificationRepository
import de.seemoo.at_tracking_detection.database.tables.Beacon
import de.seemoo.at_tracking_detection.database.tables.Device
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrackingViewModel @Inject constructor(
    private val notificationRepository: NotificationRepository,
    private val beaconRepository: BeaconRepository,
    private val deviceRepository: DeviceRepository
) : ViewModel() {

    val deviceAddress = MutableLiveData<String>()

    val notificationId = MutableLiveData<Int>()

    val error = MutableLiveData(false)

    val falseAlarmClickable = MutableLiveData(true)
    val ignoreDeviceClickable = MutableLiveData(true)

    val soundPlaying = MutableLiveData(false)

    val connecting = MutableLiveData(false)

    val device = MutableLiveData<Device>()

    val gattServiceIntent = MutableLiveData<Intent>()

    fun getMarkerLocations(): LiveData<List<Beacon>> = Transformations.map(deviceAddress) {
        beaconRepository.getDeviceBeacons(it)
    }

    fun getDevice(address: String): Device? =
        deviceRepository.getDevice(address).also { device.postValue(it) }

    fun ignoreDevice() {
        if (deviceAddress.value != null) {
            viewModelScope.launch {
                deviceRepository.ignoreDevice(deviceAddress.value!!)
            }
            ignoreDeviceClickable.postValue(false)
        }
    }

    fun markFalseAlarm() {
        if (notificationId.value != null) {
            viewModelScope.launch {
                notificationRepository.markFalseAlarm(notificationId.value!!)
            }
            falseAlarmClickable.postValue(false)
        }
    }
}