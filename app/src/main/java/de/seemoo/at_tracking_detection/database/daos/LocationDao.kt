package de.seemoo.at_tracking_detection.database.daos

import androidx.room.*
import de.seemoo.at_tracking_detection.database.models.Location as LocationModel
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

@Dao
interface LocationDao {
    @Query("SELECT * FROM location ORDER BY firstDiscovery DESC")
    fun getAll(): Flow<List<LocationModel>>

    @Query("SELECT * FROM location WHERE lastSeen >= :since ORDER BY lastSeen DESC")
    fun getLocationsSince(since: LocalDateTime): List<LocationModel>

    @Query("SELECT Count(*) FROM location WHERE lastSeen >= :since")
    fun getLocationsSinceCount(since: LocalDateTime): Flow<Int>

    @Query("SELECT COUNT(*) FROM location WHERE locationId IS NOT NULL AND locationId != 0 ")
    fun getTotalLocationCount(): Int

    @Query("SELECT * FROM location ORDER BY ABS(latitude - :latitude) + ABS(longitude - :longitude) ASC LIMIT 1")
    fun getClosestExistingLocation(latitude: Double, longitude:Double): LocationModel?

    @Query("SELECT * FROM location WHERE locationId = :locationId")
    fun getLocationWithId(locationId: Int): LocationModel?

    @Query("SELECT COUNT(*) FROM location, beacon WHERE location.locationId = :locationId AND location.locationId = beacon.locationId")
    fun getNumberOfBeaconsForLocation(locationId: Int): Int

    @Query("SELECT * FROM location WHERE locationId NOT IN (SELECT DISTINCT locationId FROM beacon WHERE locationId IS NOT NULL)")
    fun getLocationsWithNoBeacons(): List<LocationModel>

    @Query("SELECT l.* FROM location l INNER JOIN beacon b ON l.locationId = b.locationId WHERE b.deviceAddress = :deviceAddress")
    fun getLocationsForDevice(deviceAddress: String): List<LocationModel>

    @Query("SELECT l.* FROM location l INNER JOIN beacon b ON l.locationId = b.locationId WHERE b.deviceAddress = :deviceAddress AND b.receivedAt >= :since")
    fun getLocationsForDeviceSince(deviceAddress: String, since: LocalDateTime): List<LocationModel>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(location: LocationModel): Long

    @Update
    suspend fun update(location: LocationModel)

    @Delete
    suspend fun delete(location: LocationModel)

    @Delete
    suspend fun deleteLocations(locations: List<LocationModel>)
}