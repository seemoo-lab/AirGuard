package de.seemoo.at_tracking_detection.ui.devices

import androidx.core.util.Pair
import androidx.lifecycle.*
import dagger.hilt.android.lifecycle.HiltViewModel
import de.seemoo.at_tracking_detection.database.repository.BeaconRepository
import de.seemoo.at_tracking_detection.database.repository.DeviceRepository
import de.seemoo.at_tracking_detection.database.tables.Beacon
import de.seemoo.at_tracking_detection.database.tables.Device
import de.seemoo.at_tracking_detection.ui.devices.filter.models.Filter
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class DevicesViewModel @Inject constructor(
    private val beaconRepository: BeaconRepository,
    private val deviceRepository: DeviceRepository
) : ViewModel() {

    fun setIgnoreFlag(deviceAddress: String, state: Boolean) = viewModelScope.launch {
        deviceRepository.setIgnoreFlag(deviceAddress, state)
    }

    fun getDeviceBeaconsCount(deviceAddress: String): String =
        beaconRepository.getDeviceBeaconsCount(deviceAddress).toString()

    fun getDevice(deviceAddress: String): Device = deviceRepository.getDevice(deviceAddress)!!

    fun getMarkerLocations(deviceAddress: String): List<Beacon> =
        beaconRepository.getDeviceBeacons(deviceAddress)

    fun update(device: Device) = viewModelScope.launch {
        deviceRepository.update(device)
    }

    fun addOrRemoveFilter(filter: Filter, remove: Boolean = false) {
        val filterName = filter::class.toString()
        if (remove) {
            activeFilter.remove(filterName)
        } else {
            activeFilter[filterName] = filter
        }
        Timber.d("Active Filter: $activeFilter")
        devices.addSource(deviceRepository.devices.asLiveData()) {
            var filteredDevices = it
            activeFilter.forEach { (_, filter) ->
                filteredDevices = filter.apply(filteredDevices)
            }
            devices.value = filteredDevices
        }
    }

    val devices = MediatorLiveData<List<Device>>()

    init {
        devices.addSource(deviceRepository.devices.asLiveData()) {
            if (activeFilter.isEmpty()) {
                devices.value = it
            }
        }
    }

    val activeFilter: MutableMap<String, Filter> = mutableMapOf()

    val deviceListEmpty: LiveData<Boolean> = devices.map { it.isEmpty() }
}