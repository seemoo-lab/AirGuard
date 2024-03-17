package de.seemoo.at_tracking_detection.database.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RewriteQueriesToDropUnusedColumns
import androidx.room.Transaction
import androidx.room.Update
import de.seemoo.at_tracking_detection.database.models.Notification
import de.seemoo.at_tracking_detection.database.relations.NotificationFeedback
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

@Dao
interface NotificationDao {
    @Query("SELECT * FROM notification")
    fun getAll(): Flow<List<Notification>>

    @Query("UPDATE notification SET falseAlarm = :state WHERE :id Like notificationId")
    suspend fun setFalseAlarm(id: Int, state: Boolean)

    @Query("UPDATE notification SET dismissed = :state WHERE :id Like notificationId")
    suspend fun setDismissed(id: Int, state: Boolean)

    @Query("UPDATE notification SET clicked = :state WHERE :id Like notificationId")
    suspend fun setClicked(id: Int, state: Boolean)

    @Query("SELECT COUNT(*) FROM notification WHERE falseAlarm = 1")
    fun getCountFalseAlarm(): Flow<Int>

    @Query("SELECT COUNT(*) FROM notification WHERE falseAlarm = 1 AND createdAt >= :since")
    fun getFalseAlarmCountChange(since: LocalDateTime): Flow<Int>

    @Query("SELECT COUNT(*) FROM notification WHERE falseAlarm = 0")
    fun getCountAlerts(): Flow<Int>

    @Query("SELECT COUNT(*) FROM notification")
    fun getTotalCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM notification WHERE createdAt >= :since")
    fun getTotalCountChange(since: LocalDateTime): Flow<Int>

    @Query("SELECT * FROM notification WHERE createdAt >= :since")
    fun getNotificationsSince(since: LocalDateTime): List<Notification>

    @Query("SELECT COUNT(*) FROM notification WHERE createdAt >= :since")
    fun getTotalCountSince(since: LocalDateTime): Int

    @Query("SELECT * FROM notification ORDER BY createdAt DESC LIMIT 1")
    fun getLastNotification(): List<Notification>

    @Query("SELECT * FROM notification WHERE deviceAddress == :deviceAddress ORDER BY createdAt DESC")
    fun getNotificationForDevice(deviceAddress: String): List<Notification>

    @Query("SELECT COUNT(*) FROM notification WHERE deviceAddress == :deviceAddress AND createdAt >= :since")
    fun getNotificationForDeviceSinceCount(deviceAddress: String, since: LocalDateTime): Int

    @Query("SELECT COUNT(*) FROM notification WHERE deviceAddress == :deviceAddress AND createdAt >= :since AND falseAlarm = 1")
    fun getFalseAlarmForDeviceSinceCount(deviceAddress: String, since: LocalDateTime): Int

    @Transaction
    @RewriteQueriesToDropUnusedColumns
    @Query("SELECT * FROM notification")
    suspend fun getExtendedNotifications(): List<NotificationFeedback>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(notification: Notification): Long

    @Update
    suspend fun update(notification: Notification)

    @Query("SELECT COUNT(*) FROM notification WHERE deviceAddress == :deviceAddress AND falseAlarm = 0 LIMIT 1")
    fun existsNotificationForDevice(deviceAddress: String): Boolean
}