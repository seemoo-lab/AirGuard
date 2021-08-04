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

    @Query("UPDATE notification SET falseAlarm = 1 WHERE :id Like notificationId")
    suspend fun markFalseAlarm(id: Int)

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

    @Transaction
    @RewriteQueriesToDropUnusedColumns
    @Query("SELECT * FROM notification")
    suspend fun getExtendedNotifications(): List<NotificationFeedback>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(notification: Notification): Long

    @Update
    suspend fun update(notification: Notification)
}