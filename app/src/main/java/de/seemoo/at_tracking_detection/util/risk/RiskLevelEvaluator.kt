package de.seemoo.at_tracking_detection.util.risk

import de.seemoo.at_tracking_detection.database.models.Beacon
import de.seemoo.at_tracking_detection.database.repository.BeaconRepository
import de.seemoo.at_tracking_detection.database.repository.DeviceRepository
import de.seemoo.at_tracking_detection.database.models.device.BaseDevice
import de.seemoo.at_tracking_detection.database.repository.NotificationRepository
import de.seemoo.at_tracking_detection.util.SharedPrefs
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.*

class RiskLevelEvaluator(
    private val deviceRepository: DeviceRepository,
    private val beaconRepository: BeaconRepository,
    private val notificationRepository: NotificationRepository,
) {

    /**
     * Evaluates the risk that the user is at. For this all notifications sent (equals trackers discovered) for the last `RELEVANT_DAYS` are checked and a risk score is evaluated
     */
    fun evaluateRiskLevel(): RiskLevel {
        val baseDevices: List<BaseDevice> = deviceRepository.trackingDevicesSince(relevantTrackingDate)

        val totalTrackers = baseDevices.count()

        if (totalTrackers == 0) {
            return RiskLevel.LOW
        } else {
            var riskMedium = false
            for (baseDevice in baseDevices) {
                val deviceRiskLevel = checkRiskLevelForDevice(baseDevice, useLocation, deviceRepository, beaconRepository, notificationRepository)
                if (deviceRiskLevel == RiskLevel.HIGH) {
                    return RiskLevel.HIGH
                } else if (deviceRiskLevel == RiskLevel.MEDIUM) {
                    riskMedium = true
                }
            }

            if (riskMedium) {
                return RiskLevel.MEDIUM
            }
            return RiskLevel.LOW
        }
    }

    private val useLocation = SharedPrefs.useLocationInTrackingDetection

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
        private const val RELEVANT_DAYS_NOTIFICATIONS: Long = 5
        private const val NUMBER_OF_NOTIFICATIONS_FOR_HIGH_RISK: Long = 2
        private const val NUMBER_OF_LOCATIONS_BEFORE_ALARM: Int = 3
        private const val NUMBER_OF_BEACONS_BEFORE_ALARM: Int = 3
        const val HOURS_AT_LEAST_TRACKED_BEFORE_ALARM: Long = 8
        const val HOURS_AT_LEAST_UNTIL_NEXT_NOTIFICATION: Long = 8
        private val atLeastTrackedSince: LocalDateTime = LocalDateTime.now().minusHours(HOURS_AT_LEAST_TRACKED_BEFORE_ALARM)
        val relevantTrackingDate: LocalDateTime = LocalDateTime.now().minusDays(RELEVANT_DAYS)
        private val relevantNotificationDate: LocalDateTime = LocalDateTime.now().minusDays(RELEVANT_DAYS_NOTIFICATIONS)

        // Checks if BaseDevice is a tracking device
        // Goes through all the criteria
        fun checkRiskLevelForDevice(device: BaseDevice, useLocation: Boolean, deviceRepository: DeviceRepository, beaconRepository: BeaconRepository, notificationRepository: NotificationRepository): RiskLevel {
            // Not ignored
            // Tracker has been seen long enough
            if (!device.ignore && device.firstDiscovery <= atLeastTrackedSince) {
                val numberOfBeacons = beaconRepository.getNumberOfBeaconsAddress(device.address, relevantTrackingDate)

                // Detected at least 3 Times
                if (numberOfBeacons >= NUMBER_OF_BEACONS_BEFORE_ALARM) {
                    val numberOfLocations = deviceRepository.getNumberOfLocationsForDeviceSince(device.address, relevantTrackingDate)

                    // Detected at at least 3 different locations
                    if (!useLocation || numberOfLocations >= NUMBER_OF_LOCATIONS_BEFORE_ALARM) {
                        val falseAlarms = notificationRepository.getFalseAlarmForDeviceSinceCount(device.address, relevantTrackingDate)

                        // No False Alarm (Join via Notification)
                        if (falseAlarms == 0) {
                            val minTrackingTime = device.device.deviceContext.minTrackingTime
                            val beaconList = beaconRepository.getDeviceBeacons(device.address)
                            val timeDiff = maxTimeDiffBetweenBeacons(beaconList)

                            if (timeDiff >= minTrackingTime) {
                                val numberOfNotifications = notificationRepository.getNotificationForDeviceSinceCount(device.address, relevantNotificationDate)

                                return if (numberOfNotifications >= NUMBER_OF_NOTIFICATIONS_FOR_HIGH_RISK) {
                                    RiskLevel.HIGH
                                } else{
                                    RiskLevel.MEDIUM
                                }
                            }
                        }
                    }
                }
            }
            return RiskLevel.LOW
        }

        private fun maxTimeDiffBetweenBeacons(beacons: List<Beacon>): Long {
            if (beacons.isEmpty()) {
                return 0
            }

            //Sort the list by received at
            //Last beacon received is last at the list
            val beaconsSorted = beacons.sortedBy { it.receivedAt }

            val firstBeacon = beaconsSorted.first()
            val lastBeacon = beaconsSorted.last()
            return ChronoUnit.SECONDS.between(firstBeacon.receivedAt, lastBeacon.receivedAt)
        }

    }
}