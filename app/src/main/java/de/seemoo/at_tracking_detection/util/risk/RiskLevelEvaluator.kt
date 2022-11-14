package de.seemoo.at_tracking_detection.util.risk

import de.seemoo.at_tracking_detection.database.repository.BeaconRepository
import de.seemoo.at_tracking_detection.database.repository.DeviceRepository
import de.seemoo.at_tracking_detection.database.models.device.BaseDevice
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
        val baseDevices: List<BaseDevice> = deviceRepository.trackingDevicesSince(relevantDate)

        val totalTrackers = baseDevices.count()

        if (totalTrackers == 0) {
            return RiskLevel.LOW
        } else {
            val trackedLocations = baseDevices.map {
                beaconRepository.getDeviceBeacons(it.address)
            }.flatten()

            //TODO: Ignored devices and false alarms should not be in the list
            // Change risk evaluation to the one in AirGuard iOS
            // How can we de
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

    /**
     * The date when a tracker has been discovered last
     */
    fun getLastTrackerDiscoveryDate(): Date {
        val relevantDate = relevantTrackingDate
        val baseDevices: List<BaseDevice> = deviceRepository.trackingDevicesSince(relevantDate)
            .sortedByDescending { it.lastSeen }

        return baseDevices.firstOrNull()
            ?.let { Date.from(it.lastSeen.atZone(ZoneId.systemDefault()).toInstant()) }
            ?: Date()
    }

    // How many trackers have been relevant here as tracker
    fun getNumberRelevantTrackers(): Int {
        val relevantDate = LocalDateTime.now().minusDays(RELEVANT_DAYS)
        val baseDevices: List<BaseDevice> = deviceRepository.trackingDevicesSince(relevantDate)

        return baseDevices.count()
    }

    companion object {
        /** The number of days that we use to calculate the risk **/
        const val RELEVANT_DAYS: Long = 14
        val relevantTrackingDate: LocalDateTime = LocalDateTime.now().minusDays(RELEVANT_DAYS)
    }
}