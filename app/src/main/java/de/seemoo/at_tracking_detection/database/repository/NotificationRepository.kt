package de.seemoo.at_tracking_detection.database.repository

import androidx.annotation.WorkerThread
import de.seemoo.at_tracking_detection.database.daos.NotificationDao
import de.seemoo.at_tracking_detection.database.models.Notification
import de.seemoo.at_tracking_detection.database.models.device.BaseDevice
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationRepository @Inject constructor(private val notificationDao: NotificationDao) {

    val totalCount: Flow<Int> = notificationDao.getTotalCount()

    /**
     * All Notifications sent since a given date
     * @param since: Date since when notifications should be returned
     */
    fun notificationsSince(since: LocalDateTime): List<Notification> =
        notificationDao.getNotificationsSince(since)

    fun totalCountChange(since: LocalDateTime): Flow<Int> =
        notificationDao.getTotalCountChange(since)

    /**
     * Returns the number of notifications sent since a given date
     */
    fun totalCountSince(since: LocalDateTime): Int = notificationDao.getTotalCountSince(since)

    val totalAlertCount: Flow<Int> = notificationDao.getCountAlerts()

    val totalFalseAlarmCount: Flow<Int> = notificationDao.getCountFalseAlarm()
    fun totalFalseAlarmCountChange(since: LocalDateTime): Flow<Int> =
        notificationDao.getFalseAlarmCountChange(since)

    val notifications: Flow<List<Notification>> = notificationDao.getAll()

    /**
     * Returns a list with only the last notification
     */
    val last_notification: List<Notification> = notificationDao.getLastNotification()

    fun notificationForDevice(device: BaseDevice) = notificationDao.getNotificationForDevice(device.address)

    @WorkerThread
    suspend fun insert(notification: Notification): Long {
        return notificationDao.insert(notification)
    }

    @WorkerThread
    suspend fun setFalseAlarm(notificationId: Int, state: Boolean) {
        notificationDao.setFalseAlarm(notificationId, state)
    }

    @WorkerThread
    suspend fun setDismissed(notificationId: Int, state: Boolean) {
        notificationDao.setDismissed(notificationId, state)
    }

    @WorkerThread
    suspend fun setClicked(notificationId: Int, state: Boolean) {
        notificationDao.setClicked(notificationId, state)
    }
}