package de.seemoo.at_tracking_detection.database.daos

import androidx.room.*
import de.seemoo.at_tracking_detection.database.models.device.BaseDevice
import de.seemoo.at_tracking_detection.database.relations.DeviceBeaconNotification
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

@Dao
interface DeviceDao {
    @Query("SELECT * FROM device ORDER BY lastSeen DESC")
    fun getAll(): Flow<List<BaseDevice>>

    @Query("SELECT * FROM device WHERE lastSeen >= :since ORDER BY lastSeen DESC")
    fun getAllSince(since: LocalDateTime): Flow<List<BaseDevice>>

    @Query("SELECT * FROM device WHERE lastSeen >= :since AND notificationSent == 1 ORDER BY lastSeen DESC")
    fun getAllNotificationSinceFlow(since: LocalDateTime): Flow<List<BaseDevice>>

    @Query("SELECT * FROM device WHERE lastSeen >= :since AND notificationSent == 1 ORDER BY lastSeen DESC")
    fun getAllNotificationSince(since: LocalDateTime): List<BaseDevice>

    @Query("SELECT * FROM device WHERE lastSeen >= :since AND notificationSent == 1 AND `ignore` == 0 ORDER BY lastSeen DESC")
    fun getAllTrackingDevicesNotIgnoredSince(since: LocalDateTime): List<BaseDevice>

    @Query("SELECT COUNT(*) FROM device WHERE lastSeen >= :since AND notificationSent == 1 AND `ignore` == 0")
    fun getAllTrackingDevicesNotIgnoredSinceCount(since: LocalDateTime): Flow<Int>

    @Query("SELECT COUNT(*) FROM device WHERE lastSeen >= :since AND notificationSent == 1")
    fun trackingDevicesCount(since: LocalDateTime): Flow<Int>

    @Query("SELECT * FROM device WHERE `ignore` == 1 ORDER BY lastSeen DESC")
    fun getIgnored(): Flow<List<BaseDevice>>

    @Query("SELECT * FROM device WHERE `ignore` == 1 ORDER BY lastSeen DESC")
    fun getIgnoredSync(): List<BaseDevice>

    @Query("SELECT * FROM device WHERE address LIKE :address LIMIT 1")
    fun getByAddress(address: String): BaseDevice?

    @Query("DELETE FROM device WHERE address LIKE :address")
    suspend fun remove(address: String)

    @Query("UPDATE device SET `ignore` = :state WHERE address = :address")
    suspend fun setIgnoreFlag(address: String, state: Boolean)

    @Query("SELECT COUNT(*) FROM device WHERE safeTracker == 0")
    fun getTotalCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM device WHERE lastSeen >= :since AND notificationSent == 0 AND safeTracker == 0")
    fun getCountNotTracking(since: LocalDateTime): Flow<Int>

    @Query("SELECT COUNT(*) FROM device WHERE `ignore` == 1")
    fun getCountIgnored(): Flow<Int>

    @Query("SELECT COUNT(*) FROM device WHERE firstDiscovery >= :since AND safeTracker == 0")
    fun getTotalCountChange(since: LocalDateTime): Flow<Int>

    @Query("SELECT COUNT(*) FROM device WHERE lastSeen >= :since AND safeTracker == 0")
    fun getCurrentlyMonitored(since: LocalDateTime): Flow<Int>

    @Query("SELECT COUNT(*) FROM device WHERE lastSeen >= :since AND deviceType = :deviceType AND safeTracker == 0")
    fun getCountForType(deviceType: String, since: LocalDateTime): Flow<Int>

    @Query("SELECT COUNT(*) FROM device WHERE lastSeen >= :since AND (deviceType = :deviceType1 OR deviceType = :deviceType2) AND safeTracker == 0")
    fun getCountForTypes(deviceType1: String, deviceType2: String, since: LocalDateTime): Flow<Int>

    @Query("SELECT COUNT(DISTINCT(location.locationId)) FROM device, location, beacon WHERE beacon.locationId = location.locationId AND beacon.deviceAddress = device.address AND beacon.locationId != 0 AND device.address = :deviceAddress AND device.lastSeen >= :since")
    fun getNumberOfLocationsForDevice(deviceAddress: String, since: LocalDateTime): Int

    @Query("SELECT COUNT(DISTINCT(location.locationId)) FROM device, location, beacon WHERE beacon.locationId = location.locationId AND beacon.deviceAddress = device.address AND beacon.locationId != 0 AND device.address = :deviceAddress AND accuracy is not NULL AND accuracy <= :maxAccuracy AND device.lastSeen >= :since")
    fun getNumberOfLocationsForWithAccuracyLimitDevice(deviceAddress: String, maxAccuracy: Float, since: LocalDateTime): Int

    @Query("SELECT riskLevel FROM device WHERE address = :deviceAddress")
    fun getCachedRiskLevel(deviceAddress: String): Int

    @Query("SELECT lastCalculatedRiskDate FROM device WHERE address = :deviceAddress")
    fun getLastCachedRiskLevelDate(deviceAddress: String): LocalDateTime?

    @Query("UPDATE device SET riskLevel = :riskLevel, lastCalculatedRiskDate = :lastCalculatedRiskDate WHERE address == :deviceAddress")
    fun updateRiskLevelCache(deviceAddress: String, riskLevel: Int, lastCalculatedRiskDate: LocalDateTime)

    @Query("""
    SELECT * FROM device
    WHERE device.deviceType = :deviceType
    AND additionalData = :additionalData
    AND device.lastSeen BETWEEN :since AND :until
    LIMIT 1 
    """)
    fun getDeviceWithRecentBeacon(deviceType: String, additionalData: String, since: LocalDateTime, until: LocalDateTime): BaseDevice?

    @Transaction
    @RewriteQueriesToDropUnusedColumns
    @Query("SELECT * FROM device WHERE lastSeen >= :dateTime")
    suspend fun getDeviceBeaconsSince(dateTime: LocalDateTime): List<DeviceBeaconNotification>

    @Transaction
    @RewriteQueriesToDropUnusedColumns
    @Query("SELECT * FROM device")
    suspend fun getDeviceBeacons(): List<DeviceBeaconNotification>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(vararg baseDevices: BaseDevice)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(baseDevice: BaseDevice): Long

    @Update
    suspend fun update(baseDevice: BaseDevice)

    @Delete
    suspend fun deleteDevice(baseDevice: BaseDevice)

    @Delete
    suspend fun deleteDevices(baseDevices: List<BaseDevice>)

    @Query("SELECT * FROM device WHERE lastSeen < :since AND notificationSent == 0")
    fun getDevicesOlderThanWithoutNotifications(since: LocalDateTime): List<BaseDevice>

    @Query("SELECT * FROM device WHERE deviceType = :deviceType AND additionalData = :connectionState AND lastSeen < :olderThan AND riskLevel = 0 AND notificationSent = 0")
    fun getDevicesWithDeviceTypeAndConnectionState(
        deviceType: String,
        connectionState: String,
        olderThan: LocalDateTime
    ): List<BaseDevice>
}