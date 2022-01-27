package de.seemoo.at_tracking_detection.util.risk

import de.seemoo.at_tracking_detection.database.repository.BeaconRepository
import de.seemoo.at_tracking_detection.database.repository.DeviceRepository
import de.seemoo.at_tracking_detection.database.tables.Device
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.math.abs

class RiskLevelEvaluator(
    private val deviceRepository: DeviceRepository,
    private val beaconRepository: BeaconRepository
) {

    /**
     * Evaluates the risk that the user is at. For this all notifications sent (equals trackers discovered) for the last `RELEVANT_DAYS` are checked and a risk score is evaluated
     */
    fun evaluateRiskLevel(): RiskLevel {
        val relevantDate = relevantTrackingDate
        val devices: List<Device> = deviceRepository.trackingDevicesSince(relevantDate)

        val totalTrackers = devices.count()

        if (totalTrackers == 0) {
            return RiskLevel.LOW
        } else {
            val trackedLocations = devices.map {
                beaconRepository.getDeviceBeacons(it.address)
            }.flatten()

            val firstBeacon = trackedLocations.first()
            val lastBeacon = trackedLocations.last()

            val daysDiff = abs(firstBeacon.receivedAt.until(lastBeacon.receivedAt, ChronoUnit.DAYS))
            return if (daysDiff >= 1) {
                //High risk
                RiskLevel.HIGH
            } else {
                RiskLevel.MEDIUM
            }
        }
    }

    fun getLastTrackerDiscoveryDate(): Date {
        val relevantDate = relevantTrackingDate
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
        val relevantTrackingDate: LocalDateTime = LocalDateTime.now().minusDays(RELEVANT_DAYS)
    }
}