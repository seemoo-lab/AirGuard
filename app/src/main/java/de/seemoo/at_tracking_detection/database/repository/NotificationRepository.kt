package de.seemoo.at_tracking_detection.database.repository

import androidx.annotation.WorkerThread
import de.seemoo.at_tracking_detection.database.daos.NotificationDao
import de.seemoo.at_tracking_detection.database.tables.Notification
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationRepository @Inject constructor(private val notificationDao: NotificationDao) {

    val totalCount: Flow<Int> = notificationDao.getTotalCount()
    fun totalCountChange(since: LocalDateTime): Flow<Int> =
        notificationDao.getTotalCountChange(since)

    val totalAlterCount: Flow<Int> = notificationDao.getCountAlerts()

    val totalFalseAlarmCount: Flow<Int> = notificationDao.getCountFalseAlarm()
    fun totalFalseAlarmCountChange(since: LocalDateTime): Flow<Int> =
        notificationDao.getFalseAlarmCountChange(since)

    val notifications: Flow<List<Notification>> = notificationDao.getAll()

    @WorkerThread
    suspend fun insert(notification: Notification): Long {
        return notificationDao.insert(notification)
    }

    @WorkerThread
    suspend fun markFalseAlarm(notificationId: Int) {
        notificationDao.markFalseAlarm(notificationId)
    }
}