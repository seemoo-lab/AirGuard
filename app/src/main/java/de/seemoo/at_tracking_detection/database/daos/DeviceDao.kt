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

    @Query("SELECT COUNT(*) FROM device WHERE lastSeen >= :since AND notificationSent == 1 ORDER BY lastSeen DESC")
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

    @Query("SELECT COUNT(*) FROM device")
    fun getTotalCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM device WHERE lastSeen >= :since AND notificationSent == 0")
    fun getCountNotTracking(since: LocalDateTime): Flow<Int>

    @Query("SELECT COUNT(*) FROM device WHERE `ignore` == 1")
    fun getCountIgnored(): Flow<Int>

    @Query("SELECT COUNT(*) FROM device WHERE firstDiscovery >= :since")
    fun getTotalCountChange(since: LocalDateTime): Flow<Int>

    @Query("SELECT COUNT(*) FROM device WHERE lastSeen >= :since")
    fun getCurrentlyMonitored(since: LocalDateTime): Flow<Int>

    @Query("SELECT COUNT(*) FROM device WHERE lastSeen >= :since AND deviceType = :deviceType")
    fun getCountForType(deviceType: String, since: LocalDateTime): Flow<Int>

    @Query("SELECT COUNT(*) FROM device WHERE lastSeen >= :since AND (deviceType = :deviceType1 OR deviceType = :deviceType2)")
    fun getCountForTypes(deviceType1: String, deviceType2: String, since: LocalDateTime): Flow<Int>

    @Query("SELECT COUNT(*) FROM device, location, beacon WHERE beacon.locationId = location.locationId AND beacon.deviceAddress = device.address AND device.address = :deviceAddress AND device.lastSeen >= :since")
    fun getNumberOfLocationsForDevice(deviceAddress: String, since: LocalDateTime): Int

    @Transaction
    @RewriteQueriesToDropUnusedColumns
    @Query("SELECT * FROM device JOIN beacon ON beacon.deviceAddress = deviceAddress WHERE beacon.receivedAt >= :dateTime")
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
    suspend fun delete(baseDevice: BaseDevice)
}