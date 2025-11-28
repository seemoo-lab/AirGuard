package de.seemoo.at_tracking_detection.database.repository

import androidx.annotation.WorkerThread
import de.seemoo.at_tracking_detection.database.daos.BeaconDao
import de.seemoo.at_tracking_detection.database.models.Beacon
import de.seemoo.at_tracking_detection.database.models.device.BaseDevice
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime
import javax.inject.Inject

class BeaconRepository @Inject constructor(
    private val beaconDao: BeaconDao
) {
    val allBeacons: List<Beacon> = beaconDao.getAllBeacons()

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

    fun getDeviceBeaconsFlow(deviceAddress: String): Flow<List<Beacon>> =
        beaconDao.observeDeviceBeacons(deviceAddress)

    fun getDeviceBeaconsSince(deviceAddress: String, since: LocalDateTime): List<Beacon> =
        beaconDao.getDeviceBeaconsSince(deviceAddress, since)

    fun getNumberOfBeaconsAddress(deviceAddress: String, since: LocalDateTime): Int = beaconDao.getNumberOfBeaconsAddress(deviceAddress, since)

    fun getNumberOfBeaconsAddressAndLocation(deviceAddress: String, locationId: Int, since: LocalDateTime): Int = beaconDao.getNumberOfBeaconsAddressAndLocation(deviceAddress, locationId, since)

    fun getBeaconsForDevices(baseDevices: List<BaseDevice>): List<Beacon> {
        return baseDevices.map {
            beaconDao.getDeviceBeacons(it.address)
        }.flatten()
    }

    @WorkerThread
    suspend fun insert(beacon: Beacon): Long = beaconDao.insert(beacon)

    @WorkerThread
    suspend fun update(beacon: Beacon) {
        beaconDao.update(beacon)
    }

    @WorkerThread
    suspend fun deleteBeacons(beacons: List<Beacon>) {
        beaconDao.deleteBeacons(beacons)
    }

    fun getBeaconsOlderThanWithoutNotifications(deleteEverythingBefore: LocalDateTime): List<Beacon> = beaconDao.getBeaconsOlderThanWithoutNotifications(deleteEverythingBefore)

    fun getMostRecentBeaconAtLocation(locationId: Int): Beacon? = beaconDao.getMostRecentBeaconAtLocation(locationId)
}
