package de.seemoo.at_tracking_detection.database.viewmodel

import de.seemoo.at_tracking_detection.database.models.Notification
import de.seemoo.at_tracking_detection.database.repository.NotificationRepository
import de.seemoo.at_tracking_detection.util.Utility
import java.time.LocalDateTime
import java.time.ZoneOffset
import javax.inject.Inject

class NotificationViewModel @Inject constructor(private val notificationRepository: NotificationRepository) {

    /***
     * Insert notification for this device to the Database
     */
    suspend fun insertToDb(deviceAddress: String): Int {
        val sensitivity = Utility.getSensitivityLevelValue()
        val notification = Notification(deviceAddress, false, LocalDateTime.now(ZoneOffset.UTC), sensitivity)
        return notificationRepository.insert(notification).toInt()
    }

    suspend fun insert(deviceAddress: String): Int {
        return insertToDb(deviceAddress)
    }

    suspend fun setFalseAlarm(notificationId: Int, state: Boolean) {
        notificationRepository.setFalseAlarm(notificationId, state)
    }

    suspend fun setDismissed(notificationId: Int, state: Boolean) {
        notificationRepository.setDismissed(notificationId, state)
    }

    suspend fun setClicked(notificationId: Int, state: Boolean) {
        notificationRepository.setClicked(notificationId, state)
    }
}