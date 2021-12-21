package de.seemoo.at_tracking_detection.util

import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import de.seemoo.at_tracking_detection.database.repository.NotificationRepository
import de.seemoo.at_tracking_detection.database.tables.Notification
import kotlinx.coroutines.flow.collect
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.collections.ArrayList

class RiskLevelEvaluator {

    companion object  {

        const val RELEVANT_DAYS: Long = 14

        /**
         * Evaluates the risk that the user is at. For this all notifications sent (equals trackers discovered) for the last `RELEVANT_DAYS` are checked and a risk score is evaluated
         */
        fun evaluateRiskLevel(notificationRepository: NotificationRepository): RiskLevel {
            val relevantDate = LocalDateTime.now().minusDays(RELEVANT_DAYS)
            val notifications: List<Notification> = notificationRepository.notificationsSince(relevantDate)

            val totalAlerts = notifications.count()


            if (totalAlerts == 0) {
                return RiskLevel.LOW
            }else if (totalAlerts == 1) {
                return RiskLevel.MEDIUM
            }else {
                val firstNotif = notifications.first()
                val lastNotif = notifications.last()

                val daysDiff = firstNotif.createdAt.until(lastNotif.createdAt, ChronoUnit.DAYS)
                if (daysDiff >= 1) {
                    //High risk
                    return RiskLevel.HIGH
                } else {
                    return RiskLevel.MEDIUM
                }
            }
        }

        fun getLastTrackerDiscoveryDate(notificationRepository: NotificationRepository): Date {
            val lastNotification: List<Notification> = notificationRepository.last_notification
            val lastDiscoveryDate = lastNotification.firstOrNull()?.let { Date.from(it.createdAt.atZone(ZoneId.systemDefault()).toInstant()) } ?: Date()

            return lastDiscoveryDate
        }

        fun getNumberRelevantTrackers(notificationRepository: NotificationRepository): Int {
            val totalRelevantAlerts: Int = notificationRepository.totalCountSince(LocalDateTime.now().minusDays(
                RELEVANT_DAYS))

            return totalRelevantAlerts
        }
    }
}

enum class RiskLevel {
    LOW, MEDIUM, HIGH
}