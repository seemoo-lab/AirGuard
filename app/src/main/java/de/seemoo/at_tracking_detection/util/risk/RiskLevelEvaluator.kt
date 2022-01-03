package de.seemoo.at_tracking_detection.util.risk

import de.seemoo.at_tracking_detection.database.repository.BeaconRepository
import de.seemoo.at_tracking_detection.database.repository.DeviceRepository
import de.seemoo.at_tracking_detection.database.tables.Device
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.*

class RiskLevelEvaluator(
    private val deviceRepository: DeviceRepository,
    private val beaconRepository: BeaconRepository
) {
    val relevantTrackingDate: LocalDateTime = LocalDateTime.now().minusDays(RELEVANT_DAYS)

    /**
     * Evaluates the risk that the user is at. For this all notifications sent (equals trackers discovered) for the last `RELEVANT_DAYS` are checked and a risk score is evaluated
     */
    fun evaluateRiskLevel(): RiskLevel {
        val relevantDate = LocalDateTime.now().minusDays(RELEVANT_DAYS)
        val devices: List<Device> = deviceRepository.trackingDevicesSince(relevantDate)

        val totalAlerts = devices.count()


        if (totalAlerts == 0) {
            return RiskLevel.LOW
        } else {
            val trackedLocations = devices.map {
                beaconRepository.getDeviceBeacons(it.address)
            }.flatten()

            val firstNotIf = trackedLocations.first()
            val lastNotIf = trackedLocations.last()

            val daysDiff = firstNotIf.receivedAt.until(lastNotIf.receivedAt, ChronoUnit.DAYS)
            return if (daysDiff >= 1) {
                //High risk
                RiskLevel.HIGH
            } else {
                RiskLevel.MEDIUM
            }
        }
    }

    fun getLastTrackerDiscoveryDate(): Date {
        val relevantDate = LocalDateTime.now().minusDays(RELEVANT_DAYS)
        val devices: List<Device> = deviceRepository.trackingDevicesSince(relevantDate)
            .sortedByDescending { it.lastSeen }

        return devices.firstOrNull()
            ?.let { Date.from(it.lastSeen.atZone(ZoneId.systemDefault()).toInstant()) }
            ?: Date()
    }

    fun getNumberRelevantTrackers(): Int {
        val relevantDate = LocalDateTime.now().minusDays(RELEVANT_DAYS)
        val devices: List<Device> = deviceRepository.trackingDevicesSince(relevantDate)

        return devices.count()
    }

    companion object {
        const val RELEVANT_DAYS: Long = 14
    }
}