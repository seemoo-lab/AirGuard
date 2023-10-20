package de.seemoo.at_tracking_detection.ui.dashboard

import androidx.lifecycle.*
import dagger.hilt.android.lifecycle.HiltViewModel
import de.seemoo.at_tracking_detection.database.models.Beacon
import de.seemoo.at_tracking_detection.database.repository.BeaconRepository
import de.seemoo.at_tracking_detection.util.risk.RiskLevelEvaluator
import javax.inject.Inject

@HiltViewModel
class DeviceMapViewModel @Inject constructor(
    private val beaconRepository: BeaconRepository ): ViewModel() {

    val deviceAddress = MutableLiveData<String>()

    val markerLocations: LiveData<List<Beacon>> = deviceAddress.map {
        beaconRepository.getDeviceBeacons(it)
    }

    val allLocations: LiveData<List<Beacon>> = beaconRepository.getBeaconsSince(RiskLevelEvaluator.relevantTrackingDate).asLiveData()

    val isMapLoading = MutableLiveData<Boolean>(false)

    val hideMap = MutableLiveData<Boolean>(false)
}