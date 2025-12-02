package de.seemoo.at_tracking_detection.ui.devices

import androidx.lifecycle.*
import dagger.hilt.android.lifecycle.HiltViewModel
import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.database.models.Beacon
import de.seemoo.at_tracking_detection.database.models.device.BaseDevice
import de.seemoo.at_tracking_detection.database.models.device.DeviceManager
import de.seemoo.at_tracking_detection.database.models.device.DeviceType
import de.seemoo.at_tracking_detection.database.repository.BeaconRepository
import de.seemoo.at_tracking_detection.database.repository.DeviceRepository
import de.seemoo.at_tracking_detection.ui.devices.filter.models.DeviceTypeFilter
import de.seemoo.at_tracking_detection.ui.devices.filter.models.Filter
import de.seemoo.at_tracking_detection.ui.devices.filter.models.IgnoredFilter
import de.seemoo.at_tracking_detection.ui.devices.filter.models.LocationFilter
import de.seemoo.at_tracking_detection.ui.devices.filter.models.NotifiedFilter
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
        updateDeviceList()
    }

    fun getDeviceBeaconsCount(deviceAddress: String): String =
        beaconRepository.getDeviceBeaconsCount(deviceAddress).toString()

    fun getDevice(deviceAddress: String): BaseDevice? = deviceRepository.getDevice(deviceAddress)

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
        val context = ATTrackingDetectionApplication.getAppContext()
        val summaryParts = mutableListOf<String>()

        // Check Location Filter
        val hasLocationFilter = activeFilter.containsKey(LocationFilter::class.toString())
        if (hasLocationFilter) {
            summaryParts.add(context.getString(R.string.found_at_location))
        }

        // Check Status Filters
        if (activeFilter.containsKey(IgnoredFilter::class.toString())) {
            summaryParts.add(context.getString(R.string.ignored_devices))
        }

        if (activeFilter.containsKey(NotifiedFilter::class.toString())) {
            summaryParts.add(context.getString(R.string.tracker_detected))
        }

        // Check Device Type Filters
        if (activeFilter.containsKey(DeviceTypeFilter::class.toString())) {
            val deviceTypeFilter = activeFilter[DeviceTypeFilter::class.toString()] as DeviceTypeFilter
            val totalAvailableTypes = DeviceManager.devices.count()
            val selectedCount = deviceTypeFilter.deviceTypes.count()

            if (selectedCount == totalAvailableTypes) {
                // If all are selected and no other status filters, show "All devices"
                if (summaryParts.isEmpty()) {
                    summaryParts.add(context.getString(R.string.all_devices))
                }
            } else {
                // more than 2 types -> "X types", <= 2 -> list names
                if (selectedCount > 2) {
                    summaryParts.add(context.getString(R.string.selected_types_count, selectedCount))
                } else if (selectedCount > 0) {
                    val typeNames = deviceTypeFilter.deviceTypes.map {
                        DeviceType.userReadableNameDefault(it)
                    }
                    summaryParts.addAll(typeNames)
                }
            }
        } else {
            if (summaryParts.isEmpty()) {
                summaryParts.add(context.getString(R.string.all_devices))
            }
        }

        // Join with commas
        filterSummaryText.postValue(summaryParts.joinToString(", "))
    }

}