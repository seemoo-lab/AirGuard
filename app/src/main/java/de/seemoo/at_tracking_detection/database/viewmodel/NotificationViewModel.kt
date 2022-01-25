package de.seemoo.at_tracking_detection.database.viewmodel

import de.seemoo.at_tracking_detection.database.models.Notification
import de.seemoo.at_tracking_detection.database.repository.NotificationRepository
import java.time.LocalDateTime
import java.time.ZoneOffset
import javax.inject.Inject

class NotificationViewModel @Inject constructor(private val notificationRepository: NotificationRepository) {

    suspend fun insert(deviceAddress: String): Int {
        val notification = Notification(deviceAddress, false, LocalDateTime.now(ZoneOffset.UTC))
        return notificationRepository.insert(notification).toInt()
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