package de.seemoo.at_tracking_detection.ui.devices

import androidx.compose.ui.res.stringResource
import androidx.lifecycle.*
import dagger.hilt.android.lifecycle.HiltViewModel
import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.database.repository.BeaconRepository
import de.seemoo.at_tracking_detection.database.repository.DeviceRepository
import de.seemoo.at_tracking_detection.database.models.Beacon
import de.seemoo.at_tracking_detection.database.models.device.BaseDevice
import de.seemoo.at_tracking_detection.database.models.device.DeviceType
import de.seemoo.at_tracking_detection.ui.devices.filter.models.DeviceTypeFilter
import de.seemoo.at_tracking_detection.ui.devices.filter.models.Filter
import de.seemoo.at_tracking_detection.ui.devices.filter.models.IgnoredFilter
import de.seemoo.at_tracking_detection.ui.devices.filter.models.NotifiedFilter
import kotlinx.coroutines.launch
import timber.log.Timber
import java.lang.StringBuilder
import javax.inject.Inject

@HiltViewModel
class DevicesViewModel @Inject constructor(
    private val beaconRepository: BeaconRepository,
    private val deviceRepository: DeviceRepository
) : ViewModel() {

    fun setIgnoreFlag(deviceAddress: String, state: Boolean) = viewModelScope.launch {
        deviceRepository.setIgnoreFlag(deviceAddress, state)
        updateDeviceList()
    }

    fun getDeviceBeaconsCount(deviceAddress: String): String =
        beaconRepository.getDeviceBeaconsCount(deviceAddress).toString()

    fun getDevice(deviceAddress: String): BaseDevice = deviceRepository.getDevice(deviceAddress)!!

    fun getMarkerLocations(deviceAddress: String): List<Beacon> =
        beaconRepository.getDeviceBeacons(deviceAddress)

    fun update(baseDevice: BaseDevice) = viewModelScope.launch {
        deviceRepository.update(baseDevice)
    }

    fun addOrRemoveFilter(filter: Filter, remove: Boolean = false) {
        val filterName = filter::class.toString()
        if (remove) {
            activeFilter.remove(filterName)
        } else {
            activeFilter[filterName] = filter
        }
        updateDeviceList()
        Timber.d("Active Filter: $activeFilter")
        updateFilterSummaryText()
    }

    private fun updateDeviceList() {
        devices.addSource(deviceRepository.devices.asLiveData()) {
            var filteredDevices = it
            activeFilter.forEach { (_, filter) ->
                filteredDevices = filter.apply(filteredDevices)
            }
            devices.value = filteredDevices
        }
    }

    val devices = MediatorLiveData<List<BaseDevice>>()

    init {
        devices.addSource(deviceRepository.devices.asLiveData()) {
            if (activeFilter.isEmpty()) {
                devices.value = it
            }
        }
    }

    val activeFilter: MutableMap<String, Filter> = mutableMapOf()

    val deviceListEmpty: LiveData<Boolean> = devices.map { it.isEmpty() }

    var emptyListText: MutableLiveData<String> = MutableLiveData()
    var infoText: MutableLiveData<String> = MutableLiveData()

    var filterIsExpanded: MutableLiveData<Boolean> = MutableLiveData(false)
    var filterSummaryText: MutableLiveData<String> = MutableLiveData("")

    private fun updateFilterSummaryText() {
        val filterStringBuilder = StringBuilder()
        val context = ATTrackingDetectionApplication.getAppContext()
        // No filters option
        if (activeFilter.containsKey(IgnoredFilter::class.toString())) {
            filterStringBuilder.append(context.getString(R.string.ignored_devices))
            filterStringBuilder.append(", ")
        }

        if (activeFilter.containsKey(NotifiedFilter::class.toString())) {
            filterStringBuilder.append(context.getString(R.string.tracker_detected))
            filterStringBuilder.append(", ")
        }

        if (activeFilter.containsKey(DeviceTypeFilter::class.toString())) {
            val deviceTypeFilter = activeFilter[DeviceTypeFilter::class.toString()] as DeviceTypeFilter
            for (device in deviceTypeFilter.deviceTypes) {
                filterStringBuilder.append(DeviceType.userReadableName(device))
                filterStringBuilder.append(", ")
            }
            if (deviceTypeFilter.deviceTypes.count() > 0) {
                filterStringBuilder.delete(filterStringBuilder.length-2, filterStringBuilder.length-1)
            }
        }else {
            // All devices
            filterStringBuilder.append(context.getString(R.string.title_device_map))
        }

        filterSummaryText.postValue(filterStringBuilder.toString())
    }

}