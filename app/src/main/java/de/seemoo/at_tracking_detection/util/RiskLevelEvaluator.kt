package de.seemoo.at_tracking_detection.util

import de.seemoo.at_tracking_detection.database.repository.BeaconRepository
import de.seemoo.at_tracking_detection.database.repository.DeviceRepository
import de.seemoo.at_tracking_detection.database.tables.Device
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.*

class RiskLevelEvaluator {

    companion object  {

        const val RELEVANT_DAYS: Long = 14
        fun relevantTrackingDate(): LocalDateTime = LocalDateTime.now().minusDays(RELEVANT_DAYS)

        /**
         * Evaluates the risk that the user is at. For this all notifications sent (equals trackers discovered) for the last `RELEVANT_DAYS` are checked and a risk score is evaluated
         */
        fun evaluateRiskLevel(deviceRepository: DeviceRepository, beaconRepository: BeaconRepository): RiskLevel {
            val relevantDate = LocalDateTime.now().minusDays(RELEVANT_DAYS)
            val devices: List<Device> = deviceRepository.trackingDevicesSince(relevantDate)

            val totalAlerts = devices.count()


            if (totalAlerts == 0) {
                return RiskLevel.LOW
            }else {
                val trackedLocations = devices.map {
                    beaconRepository.getDeviceBeacons(it.address)
                }.flatMap {it}

                val firstNotif = trackedLocations.first()
                val lastNotif = trackedLocations.last()

                val daysDiff = firstNotif.receivedAt.until(lastNotif.receivedAt, ChronoUnit.DAYS)
                if (daysDiff >= 1) {
                    //High risk
                    return RiskLevel.HIGH
                } else {
                    return RiskLevel.MEDIUM
                }
            }
        }

        fun getLastTrackerDiscoveryDate(deviceRepository: DeviceRepository): Date {
            val relevantDate = LocalDateTime.now().minusDays(RELEVANT_DAYS)
            val devices: List<Device> = deviceRepository.trackingDevicesSince(relevantDate).sortedByDescending { it.lastSeen }

            val lastDiscoveryDate = devices.firstOrNull()?.let { Date.from(it.lastSeen.atZone(ZoneId.systemDefault()).toInstant()) } ?: Date()

            return lastDiscoveryDate
        }

        fun getNumberRelevantTrackers(deviceRepository: DeviceRepository): Int {
            val relevantDate = LocalDateTime.now().minusDays(RELEVANT_DAYS)
            val devices: List<Device> = deviceRepository.trackingDevicesSince(relevantDate)

            return devices.count()
        }
    }
}

enum class RiskLevel {
    LOW, MEDIUM, HIGH
}