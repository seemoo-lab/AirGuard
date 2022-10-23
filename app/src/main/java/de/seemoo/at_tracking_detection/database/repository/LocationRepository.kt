package de.seemoo.at_tracking_detection.database.repository

import androidx.annotation.WorkerThread
import de.seemoo.at_tracking_detection.database.daos.LocationDao
import de.seemoo.at_tracking_detection.database.models.Location as LocationModel
import javax.inject.Inject

class LocationRepository @Inject constructor(
    private val locationDao: LocationDao
){
    val totalCount: Int = locationDao.getTotalLocationCount()

    fun closestLocation(latitude: Double, longitude: Double): LocationModel? = locationDao.getClosestExistingLocation(latitude, longitude)

    fun getLocationWithId(locationId: Int): LocationModel? = locationDao.getLocationWithId(locationId)

    fun getNumberOfBeaconsForLocation(locationId: Int): Int = locationDao.getNumberOfBeaconsForLocation(locationId)

    @WorkerThread
    suspend fun insert(location: LocationModel): Long = locationDao.insert(location)

}