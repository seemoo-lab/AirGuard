package de.seemoo.at_tracking_detection.database.daos

import androidx.room.*
import de.seemoo.at_tracking_detection.database.models.Location as LocationModel
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

@Dao
interface LocationDao {
    @Query("SELECT COUNT(*) FROM location WHERE latitude IS NOT NULL AND longitude IS NOT NULL")
    fun getTotalLocationCount(): Int

    @Query("SELECT * FROM location ORDER BY ABS(latitude - :latitude) + ABS(longitude - :longitude) ASC LIMIT 1")
    fun getClosestExistingLocation(latitude: Double, longitude:Double): LocationModel?

    @Query("SELECT * FROM location WHERE locationId = :locationId")
    fun getLocationWithId(locationId: Int): LocationModel?

    @Query("SELECT COUNT(*) FROM location, beacon WHERE location.locationId = :locationId AND location.locationId = beacon.locationId")
    fun getNumberOfBeaconsForLocation(locationId: Int): Int // TODO: check if correct

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(location: LocationModel): Long
}