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
import de.seemoo.at_tracking_detection.ui.devices.filter.models.DateRangeFilter
import de.seemoo.at_tracking_detection.ui.devices.filter.models.DeviceTypeFilter
import de.seemoo.at_tracking_detection.ui.devices.filter.models.Filter
import de.seemoo.at_tracking_detection.ui.devices.filter.models.IgnoredFilter
import de.seemoo.at_tracking_detection.ui.devices.filter.models.LocationFilter
import de.seemoo.at_tracking_detection.ui.devices.filter.models.NotifiedFilter
import de.seemoo.at_tracking_detection.ui.devices.filter.models.FavoriteFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class DevicesViewModel @Inject constructor(
    private val beaconRepository: BeaconRepository,
    private val deviceRepository: DeviceRepository
) : ViewModel() {
    enum class SortOption {
        NAME, LAST_SEEN, FIRST_DISCOVERED, TIMES_SEEN
    }

    enum class FilterState {
        UNSELECTED, INCLUDING, EXCLUDING
    }

    private var currentSort = SortOption.LAST_SEEN
    private var rawDevicesList: List<BaseDevice> = emptyList()

    val devices = MediatorLiveData<List<BaseDevice>>()
    val activeFilter: MutableMap<String, Filter> = mutableMapOf()

    // Track the state of Ignored and Notified filters
    val ignoredFilterState: MutableLiveData<FilterState> = MutableLiveData(FilterState.UNSELECTED)
    val notifiedFilterState: MutableLiveData<FilterState> = MutableLiveData(FilterState.UNSELECTED)
    val favoriteFilterState: MutableLiveData<FilterState> = MutableLiveData(FilterState.UNSELECTED)

    // UI State
    val isLoading: MutableLiveData<Boolean> = MutableLiveData(true) // Start loading
    private var hasLoadedData = false
    val showEmptyState: MutableLiveData<Boolean> = MutableLiveData(false)
    val showAllDevicesButton: MutableLiveData<Boolean> = MutableLiveData(false)
    var emptyListText: MutableLiveData<String> = MutableLiveData()
    var infoText: MutableLiveData<String> = MutableLiveData()
    var filterIsExpanded: MutableLiveData<Boolean> = MutableLiveData(false)
    var filterSummaryText: MutableLiveData<String> = MutableLiveData("")

    init {
        isLoading.value = true
        devices.addSource(deviceRepository.devices.asLiveData()) {
            rawDevicesList = it
            hasLoadedData = true
            updateVisibleList()
        }
    }

    fun setSortOption(option: SortOption) {
        currentSort = option
        updateVisibleList()
    }

    fun setFavoriteState(deviceAddress: String, hearted: Boolean) = viewModelScope.launch {
        deviceRepository.toggleHeart(deviceAddress, hearted)
    }

    fun getCurrentSort(): SortOption = currentSort

    fun cycleIgnoredFilterState() {
        val currentState = ignoredFilterState.value ?: FilterState.UNSELECTED
        val nextState = when (currentState) {
            FilterState.UNSELECTED -> FilterState.INCLUDING
            FilterState.INCLUDING -> FilterState.EXCLUDING
            FilterState.EXCLUDING -> FilterState.UNSELECTED
        }
        ignoredFilterState.value = nextState

        when (nextState) {
            FilterState.UNSELECTED -> activeFilter.remove(IgnoredFilter::class.toString())
            FilterState.INCLUDING -> activeFilter[IgnoredFilter::class.toString()] = IgnoredFilter(filterFor = true)
            FilterState.EXCLUDING -> activeFilter[IgnoredFilter::class.toString()] = IgnoredFilter(filterFor = false)
        }

        updateFilterSummaryText()
        updateVisibleList()
        Timber.d("Ignored Filter State: $nextState")
    }

    fun cycleNotifiedFilterState() {
        val currentState = notifiedFilterState.value ?: FilterState.UNSELECTED
        val nextState = when (currentState) {
            FilterState.UNSELECTED -> FilterState.INCLUDING
            FilterState.INCLUDING -> FilterState.EXCLUDING
            FilterState.EXCLUDING -> FilterState.UNSELECTED
        }
        notifiedFilterState.value = nextState

        when (nextState) {
            FilterState.UNSELECTED -> activeFilter.remove(NotifiedFilter::class.toString())
            FilterState.INCLUDING -> activeFilter[NotifiedFilter::class.toString()] = NotifiedFilter(filterFor = true)
            FilterState.EXCLUDING -> activeFilter[NotifiedFilter::class.toString()] = NotifiedFilter(filterFor = false)
        }

        updateFilterSummaryText()
        updateVisibleList()
        Timber.d("Notified Filter State: $nextState")
    }

    fun cycleFavoriteFilterState() {
        val currentState = favoriteFilterState.value ?: FilterState.UNSELECTED
        val nextState = when (currentState) {
            FilterState.UNSELECTED -> FilterState.INCLUDING
            FilterState.INCLUDING -> FilterState.EXCLUDING
            FilterState.EXCLUDING -> FilterState.UNSELECTED
        }
        favoriteFilterState.value = nextState

        when (nextState) {
            FilterState.UNSELECTED -> activeFilter.remove(FavoriteFilter::class.toString())
            FilterState.INCLUDING -> activeFilter[FavoriteFilter::class.toString()] = FavoriteFilter(filterFor = true)
            FilterState.EXCLUDING -> activeFilter[FavoriteFilter::class.toString()] = FavoriteFilter(filterFor = false)
        }

        updateFilterSummaryText()
        updateVisibleList()
        Timber.d("Favorite Filter State: $nextState")
    }

    fun addOrRemoveFilter(filter: Filter, remove: Boolean = false) {
        val filterName = filter::class.toString()
        if (remove) {
            activeFilter.remove(filterName)
        } else {
            activeFilter[filterName] = filter
        }
        updateFilterSummaryText()
        updateVisibleList()
        Timber.d("Active Filter: $activeFilter")
    }

    // applies Filters AND Sorting
    fun updateVisibleList() {
        val currentDevices = rawDevicesList
        val currentFilters = activeFilter.toMap() // Snapshot for thread safety
        val sort = currentSort

        showEmptyState.value = false

        viewModelScope.launch(Dispatchers.Default) {
            var filteredDevices = currentDevices

            // Apply Filters
            currentFilters.forEach { (_, filter) ->
                filteredDevices = filter.apply(filteredDevices)
            }

            // Apply Sorting
            filteredDevices = when (sort) {
                SortOption.NAME -> filteredDevices.sortedBy { it.getDeviceNameWithID() }
                SortOption.LAST_SEEN -> filteredDevices.sortedByDescending { it.lastSeen }
                SortOption.FIRST_DISCOVERED -> filteredDevices.sortedByDescending { it.firstDiscovery }
                SortOption.TIMES_SEEN -> {
                    val beaconCounts = filteredDevices.associate { it.address to getDeviceBeaconsCountInt(it.address) }
                    filteredDevices.sortedByDescending { beaconCounts[it.address] ?: 0 }
                }
            }

            filteredDevices = filteredDevices.filter { it.hearted } + filteredDevices.filterNot { it.hearted }

            withContext(Dispatchers.Main) {
                devices.value = filteredDevices
                isLoading.value = false // Done loading

                if (hasLoadedData) {
                    showEmptyState.value = filteredDevices.isEmpty()
                    showAllDevicesButton.value = filteredDevices.isEmpty() && hasMeaningfulFilters()
                }
            }
        }
    }

    private fun hasMeaningfulFilters(): Boolean {
        // Check if we have filters that actually filter out devices (not just DeviceTypeFilter with all types)
        return activeFilter.any { (filterName, _) ->
            filterName == NotifiedFilter::class.toString() ||
            filterName == IgnoredFilter::class.toString() ||
            filterName == FavoriteFilter::class.toString() ||
            filterName == DateRangeFilter::class.toString() ||
            filterName == LocationFilter::class.toString()
        }
    }

    fun setIgnoreFlag(deviceAddress: String, state: Boolean) = viewModelScope.launch {
        deviceRepository.setIgnoreFlag(deviceAddress, state)
    }

    fun update(baseDevice: BaseDevice) = viewModelScope.launch {
        deviceRepository.update(baseDevice)
    }

    fun getDeviceBeaconsCount(deviceAddress: String): String =
        getDeviceBeaconsCountInt(deviceAddress).toString()

    fun getDeviceBeaconsCountInt(deviceAddress: String): Int =
        beaconRepository.getDeviceBeaconsCount(deviceAddress)

    fun getDevice(deviceAddress: String): BaseDevice? = deviceRepository.getDevice(deviceAddress)

    fun getMarkerLocations(deviceAddress: String): List<Beacon> =
        beaconRepository.getDeviceBeacons(deviceAddress)

    fun updateFilterSummaryText() {
        val context = ATTrackingDetectionApplication.getAppContext()
        val summaryParts = mutableListOf<String>()

        // Check Location Filter
        val hasLocationFilter = activeFilter.containsKey(LocationFilter::class.toString())
        if (hasLocationFilter) {
            summaryParts.add(context.getString(R.string.found_at_location))
        }

        // Check Status Filters
        when (ignoredFilterState.value) {
            FilterState.INCLUDING -> summaryParts.add(context.getString(R.string.ignored_devices))
            FilterState.EXCLUDING -> summaryParts.add(context.getString(R.string.not_ignored))
            else -> {}
        }

        when (favoriteFilterState.value) {
            FilterState.INCLUDING -> summaryParts.add(context.getString(R.string.filter_marked_headline))
            FilterState.EXCLUDING -> summaryParts.add(context.getString(R.string.filter_not_marked_headline))
            else -> {}
        }

        when (notifiedFilterState.value) {
            FilterState.INCLUDING -> summaryParts.add(context.getString(R.string.tracker_detected))
            FilterState.EXCLUDING -> summaryParts.add(context.getString(R.string.not_notified))
            else -> {}
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