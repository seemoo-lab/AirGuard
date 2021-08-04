package de.seemoo.at_tracking_detection.ui.debug

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import de.seemoo.at_tracking_detection.database.repository.DeviceRepository
import javax.inject.Inject

@HiltViewModel
class DebugViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val deviceRepository: DeviceRepository
) : ViewModel()