package de.seemoo.at_tracking_detection.database.repository

import androidx.annotation.WorkerThread
import de.seemoo.at_tracking_detection.database.daos.LocationDao
import java.time.LocalDateTime
import kotlinx.coroutines.flow.Flow
import de.seemoo.at_tracking_detection.database.models.Location as LocationModel
import javax.inject.Inject

class LocationRepository @Inject constructor(
    private val locationDao: LocationDao
){
    val totalCount: Int = locationDao.getTotalLocationCount()

    val locations: Flow<List<LocationModel>> = locationDao.getAll()

    fun locationsSince(since: LocalDateTime): List<LocationModel> = locationDao.getLocationsSince(since)

    fun locationsSinceCount(since: LocalDateTime): Flow<Int> = locationDao.getLocationsSinceCount(since)

    fun closestLocation(latitude: Double, longitude: Double): LocationModel? = locationDao.getClosestExistingLocation(latitude, longitude)

    fun getLocationWithId(locationId: Int): LocationModel? = locationDao.getLocationWithId(locationId)

    fun getNumberOfBeaconsForLocation(locationId: Int): Int = locationDao.getNumberOfBeaconsForLocation(locationId)

    fun getLocationsForBeacon(deviceAddress: String): List<LocationModel> = locationDao.getLocationsForDevice(deviceAddress)

    fun getLocationsForBeaconSince(deviceAddress: String, since: LocalDateTime): List<LocationModel> = locationDao.getLocationsForDeviceSince(deviceAddress, since)


    fun getLocationsWithNoBeacons(): List<LocationModel> = locationDao.getLocationsWithNoBeacons()

    @WorkerThread
    suspend fun insert(location: LocationModel) {
        locationDao.insert(location)
    }

    @WorkerThread
    suspend fun update(location: LocationModel) {
        locationDao.update(location)
    }

    @WorkerThread
    suspend fun delete(location: LocationModel) {
        locationDao.delete(location)
    }

    @WorkerThread
    suspend fun deleteLocations(locations: List<LocationModel>) {
        locationDao.deleteLocations(locations)
    }

}