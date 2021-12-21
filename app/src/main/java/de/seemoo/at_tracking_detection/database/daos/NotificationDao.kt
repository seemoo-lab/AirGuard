package de.seemoo.at_tracking_detection.database.daos

import androidx.room.*
import de.seemoo.at_tracking_detection.database.relations.NotificationFeedback
import de.seemoo.at_tracking_detection.database.tables.Notification
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

@Dao
interface NotificationDao {
    @Query("SELECT * FROM notification")
    fun getAll(): Flow<List<Notification>>

    @Query("UPDATE notification SET falseAlarm = :state WHERE :id Like notificationId")
    suspend fun setFalseAlarm(id: Int, state: Boolean)

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

    @Transaction
    @RewriteQueriesToDropUnusedColumns
    @Query("SELECT * FROM notification")
    suspend fun getExtendedNotifications(): List<NotificationFeedback>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(notification: Notification): Long

    @Update
    suspend fun update(notification: Notification)
}