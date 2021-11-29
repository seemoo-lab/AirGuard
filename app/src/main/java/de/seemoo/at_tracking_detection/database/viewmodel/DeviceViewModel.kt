package de.seemoo.at_tracking_detection.database.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import de.seemoo.at_tracking_detection.database.repository.DeviceRepository
import de.seemoo.at_tracking_detection.database.tables.Device
import kotlinx.coroutines.launch
import javax.inject.Inject

class DeviceViewModel @Inject constructor(private val deviceRepository: DeviceRepository) :
    ViewModel() {
    val devices: LiveData<List<Device>> = deviceRepository.devices.asLiveData()

    fun insert(device: Device) = viewModelScope.launch {
        deviceRepository.insert(device)
    }

    fun update(device: Device) = viewModelScope.launch {
        deviceRepository.update(device)
    }

    suspend fun setIgnoreFlag(deviceAddress: String, state: Boolean) {
        deviceRepository.setIgnoreFlag(deviceAddress, state)
    }

}