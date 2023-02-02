package de.seemoo.at_tracking_detection.util.risk

import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication
import de.seemoo.at_tracking_detection.database.models.Beacon
import de.seemoo.at_tracking_detection.database.repository.BeaconRepository
import de.seemoo.at_tracking_detection.database.repository.DeviceRepository
import de.seemoo.at_tracking_detection.database.models.device.BaseDevice
import de.seemoo.at_tracking_detection.database.models.device.DeviceType
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
        // val baseDevices: List<BaseDevice> = deviceRepository.trackingDevicesSince(relevantTrackingDate)
        val baseDevices: List<BaseDevice> = deviceRepository.trackingDevicesNotIgnoredSince(relevantTrackingDate)

        val totalTrackers = baseDevices.count()

        if (totalTrackers == 0) {
            return RiskLevel.LOW
        } else {
            var riskMediumCounterStatic = 0 // Counter for Devices with Risk Medium with a static MAC-Address (e.g. Tile)
            var riskMediumCounterDynamic = 0 // Counter for Devices with Risk Medium with a dynamic MAC-Address (e.g. Apple)
            for (baseDevice in baseDevices) {
                val deviceRiskLevel = checkRiskLevelForDevice(baseDevice, useLocation)
                if (deviceRiskLevel == RiskLevel.HIGH) {
                    return RiskLevel.HIGH
                } else if (deviceRiskLevel == RiskLevel.MEDIUM) {
                    if (baseDevice.deviceType == DeviceType.TILE) {
                        riskMediumCounterStatic += 1
                    } else {
                        riskMediumCounterDynamic += 1
                    }
                }
            }

            val riskMediumCounterAll = riskMediumCounterDynamic + riskMediumCounterStatic

            return if (riskMediumCounterAll == 0) {
                RiskLevel.LOW
            } else if (riskMediumCounterDynamic >= MAX_NUMBER_MEDIUM_RISK) {
                RiskLevel.HIGH
            } else {
                RiskLevel.MEDIUM
            }
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
        val baseDevices: List<BaseDevice> = deviceRepository.trackingDevicesNotIgnoredSince(relevantDate)

        return baseDevices.count()
    }

    companion object {
        /** The number of days that we use to calculate the risk **/
        const val RELEVANT_DAYS: Long = 14 // Only consider beacons in the last x days
        private const val NUMBER_OF_NOTIFICATIONS_FOR_HIGH_RISK: Long = 2 // After x MEDIUM risk notifications (for a single device) change risk level to HIGH
        private const val RELEVANT_DAYS_NOTIFICATIONS: Long = 5 // After MEDIUM risk notifications in the last x days (for a single device) change risk level to HIGH
        private const val NUMBER_OF_LOCATIONS_BEFORE_ALARM: Int = 3 // Number of beacons with locations before notification is created
        private const val NUMBER_OF_BEACONS_BEFORE_ALARM: Int = 3 // Number of total beacons before notification is created
        const val MAX_ACCURACY_FOR_LOCATIONS: Float = 100.0F // Minimum Location accuracy for high risk
        const val HOURS_AT_LEAST_UNTIL_NEXT_NOTIFICATION: Long = 8 // Minimum time difference until next notification
        const val MAX_NUMBER_MEDIUM_RISK: Long = 3 // Maximum number of devices with MEDIUM risk until the total risk level is set to high
        private val atLeastTrackedSince: LocalDateTime = LocalDateTime.now().minusMinutes(
            getMinutesAtLeastTrackedBeforeAlarm()
        )
        val relevantTrackingDate: LocalDateTime = LocalDateTime.now().minusDays(RELEVANT_DAYS)
        private val relevantNotificationDate: LocalDateTime = LocalDateTime.now().minusDays(RELEVANT_DAYS_NOTIFICATIONS)

        // A single tracker gets tracked at least for x minutes until notification is created
        private const val MINUTES_AT_LEAST_TRACKED_BEFORE_ALARM_HIGH: Long = 30
        private const val MINUTES_AT_LEAST_TRACKED_BEFORE_ALARM_MEDIUM: Long = 60
        private const val MINUTES_AT_LEAST_TRACKED_BEFORE_ALARM_LOW: Long = 90

        fun getMinutesAtLeastTrackedBeforeAlarm(): Long {
            return when (SharedPrefs.riskSensitivity) {
                0 -> MINUTES_AT_LEAST_TRACKED_BEFORE_ALARM_LOW
                1 -> MINUTES_AT_LEAST_TRACKED_BEFORE_ALARM_MEDIUM
                2 -> MINUTES_AT_LEAST_TRACKED_BEFORE_ALARM_HIGH
                else -> MINUTES_AT_LEAST_TRACKED_BEFORE_ALARM_MEDIUM
            }
        }

        // Checks if BaseDevice is a tracking device
        // Goes through all the criteria
        fun checkRiskLevelForDevice(device: BaseDevice, useLocation: Boolean): RiskLevel {
            // get Repositories
            val deviceRepository = ATTrackingDetectionApplication.getCurrentApp()?.deviceRepository!!
            val beaconRepository = ATTrackingDetectionApplication.getCurrentApp()?.beaconRepository!!
            val notificationRepository = ATTrackingDetectionApplication.getCurrentApp()?.notificationRepository!!

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

                            // Tracker was detected for at least the minimum Tracking Time
                            if (timeDiff >= minTrackingTime) {
                                val numberOfNotifications = notificationRepository.getNotificationForDeviceSinceCount(device.address, relevantNotificationDate)
                                val numberOfLocationsWithAccuracyLimit = deviceRepository.getNumberOfLocationsForDeviceWithAccuracyLimitSince(device.address, MAX_ACCURACY_FOR_LOCATIONS, relevantTrackingDate)

                                // High Risk: High Number of Notifications and Accurate Locations
                                // Medium Risk: Low Number of Notifications or only inaccurate Location Reports
                                return if (numberOfNotifications >= NUMBER_OF_NOTIFICATIONS_FOR_HIGH_RISK && numberOfLocationsWithAccuracyLimit >= NUMBER_OF_LOCATIONS_BEFORE_ALARM) {
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