package de.seemoo.at_tracking_detection.database.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import de.seemoo.at_tracking_detection.database.repository.DeviceRepository
import de.seemoo.at_tracking_detection.database.models.device.BaseDevice
import kotlinx.coroutines.launch
import javax.inject.Inject

class DeviceViewModel @Inject constructor(private val deviceRepository: DeviceRepository) :
    ViewModel() {
    val devices: LiveData<List<BaseDevice>> = deviceRepository.devices.asLiveData()

    fun insert(baseDevice: BaseDevice) = viewModelScope.launch {
        deviceRepository.insert(baseDevice)
    }

    fun update(baseDevice: BaseDevice) = viewModelScope.launch {
        deviceRepository.update(baseDevice)
    }

    suspend fun setIgnoreFlag(deviceAddress: String, state: Boolean) {
        deviceRepository.setIgnoreFlag(deviceAddress, state)
    }

}