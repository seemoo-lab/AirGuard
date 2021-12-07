package de.seemoo.at_tracking_detection.ui.dashboard

import android.content.SharedPreferences
import androidx.lifecycle.LiveData
import androidx.lifecycle.Transformations
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.work.WorkInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import de.seemoo.at_tracking_detection.database.repository.BeaconRepository
import de.seemoo.at_tracking_detection.database.repository.DeviceRepository
import de.seemoo.at_tracking_detection.database.repository.NotificationRepository
import de.seemoo.at_tracking_detection.database.tables.Beacon
import de.seemoo.at_tracking_detection.worker.BackgroundWorkScheduler
import de.seemoo.at_tracking_detection.worker.WorkerConstants
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.time.LocalDateTime
import java.time.ZoneOffset
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val beaconRepository: BeaconRepository,
    private val notificationRepository: NotificationRepository,
    private val deviceRepository: DeviceRepository,
    private val sharedPreferences: SharedPreferences,
    private val backgroundWorkScheduler: BackgroundWorkScheduler
) : ViewModel() {

    private var lastScan: LocalDateTime

    private var lastTimeOpened: LocalDateTime

    private var dateTime: LocalDateTime = LocalDateTime.now(ZoneOffset.UTC)

    private var sharedPreferencesListener: SharedPreferences.OnSharedPreferenceChangeListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                "last_scan" -> lastScan = LocalDateTime.parse(
                    sharedPreferences.getString(
                        "last_scan",
                        dateTime.minusMinutes(15).toString()
                    )
                )
            }
        }

    init {
        lastScan = LocalDateTime.parse(
            sharedPreferences.getString(
                "last_scan",
                dateTime.minusMinutes(15).toString()
            )
        )
        lastTimeOpened = LocalDateTime.parse(
            sharedPreferences.getString(
                "last_time_opened",
                dateTime.toString()
            )
        )
        sharedPreferences.registerOnSharedPreferenceChangeListener(sharedPreferencesListener)
        Timber.d("last scan: $lastScan")
    }

    val isScanning: LiveData<Boolean> =
        Transformations.map(backgroundWorkScheduler.getState(WorkerConstants.PERIODIC_SCAN_WORKER)) {
            it == WorkInfo.State.RUNNING
        }

    val latestBeaconPerDevice: LiveData<List<Beacon>> =
        beaconRepository.latestBeaconPerDevice.asLiveData()

    val latestBeaconsCount: LiveData<Int> =
        beaconRepository.latestBeaconsCount(lastScan).asLiveData()

    val totalLocationsTrackedCount: LiveData<Int> = beaconRepository.locationCount.asLiveData()

    val totalLocationsCountChange: LiveData<Int> =
        beaconRepository.totalLocationCountChange(lastTimeOpened).asLiveData()

    val totalDeviceCount: LiveData<Int> = deviceRepository.totalCount.asLiveData()
    val totalDeviceCountChange: LiveData<Int> =
        deviceRepository.totalDeviceCountChange(lastTimeOpened).asLiveData()

    val currentlyMonitoredDevices: LiveData<Int> =
        deviceRepository.devicesCurrentlyMonitored(lastScan).asLiveData()

    val totalBeaconCount: LiveData<Int> = beaconRepository.totalCount.asLiveData()
    val hideMap: LiveData<Boolean> =
        beaconRepository.totalCount.map { it == 0 }.asLiveData()

    val totalAlertCount: LiveData<Int> = notificationRepository.totalCount.asLiveData()
    val totalAlertCountChange: LiveData<Int> =
        notificationRepository.totalCountChange(lastTimeOpened).asLiveData()

    val totalFalseAlarmCount: LiveData<Int> =
        notificationRepository.totalFalseAlarmCount.asLiveData()
    val totalFalseAlarmCountChange: LiveData<Int> =
        notificationRepository.totalFalseAlarmCountChange(lastTimeOpened).asLiveData()

    fun getBeaconHistory(since: LocalDateTime): LiveData<List<Beacon>> =
        beaconRepository.getBeaconsSince(since).asLiveData()
}