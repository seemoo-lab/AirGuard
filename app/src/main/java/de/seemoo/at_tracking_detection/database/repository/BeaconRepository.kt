package de.seemoo.at_tracking_detection.database.repository

import androidx.annotation.WorkerThread
import de.seemoo.at_tracking_detection.database.daos.BeaconDao
import de.seemoo.at_tracking_detection.database.tables.Beacon
import de.seemoo.at_tracking_detection.database.tables.device.Device
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime
import javax.inject.Inject

class BeaconRepository @Inject constructor(
    private val beaconDao: BeaconDao
) {
    val totalCount: Flow<Int> = beaconDao.getTotalCount()

    val locationCount: Flow<Int> = beaconDao.getTotalLocationCount()

    fun totalBeaconCountChange(since: LocalDateTime): Flow<Int> =
        beaconDao.getTotalCountChange(since)

    fun totalLocationCountChange(since: LocalDateTime): Flow<Int> =
        beaconDao.getLatestLocationsCount(since)

    fun getLatestBeacons(since: LocalDateTime): List<Beacon> = beaconDao.getLatestBeacons(since)

    fun latestBeaconsCount(since: LocalDateTime): Flow<Int> = beaconDao.getLatestBeaconCount(since)

    val latestBeaconPerDevice: Flow<List<Beacon>> = beaconDao.getLatestBeaconPerDevice()

    fun getBeaconsSince(since: LocalDateTime): Flow<List<Beacon>> = beaconDao.getBeaconsSince(since)

    fun getDeviceBeaconsCount(deviceAddress: String): Int =
        beaconDao.getDeviceBeaconsCount(deviceAddress)

    fun getDeviceBeacons(deviceAddress: String): List<Beacon> =
        beaconDao.getDeviceBeacons(deviceAddress)

    fun getBeaconsForDevices(devices: List<Device>): List<Beacon> {
        return devices.map {
            beaconDao.getDeviceBeacons(it.address)
        }.flatten()
    }

    @WorkerThread
    suspend fun insert(beacon: Beacon): Long = beaconDao.insert(beacon)
}
