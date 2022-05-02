package de.seemoo.at_tracking_detection.ui.debug

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import dagger.hilt.android.lifecycle.HiltViewModel
import de.seemoo.at_tracking_detection.database.models.Scan
import de.seemoo.at_tracking_detection.database.repository.ScanRepository
import java.time.LocalDateTime
import javax.inject.Inject

@HiltViewModel
class DebugScanViewModel @Inject constructor(
    scanRepository: ScanRepository
): ViewModel() {

    val scansLive: LiveData<List<Scan>>
    val scans: List<Scan>

    init {
        scansLive = scanRepository.flowRelevantScans.asLiveData()
        scans = scanRepository.relevantScans
    }
}