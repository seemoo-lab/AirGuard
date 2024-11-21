package de.seemoo.at_tracking_detection.util.risk

import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication
import de.seemoo.at_tracking_detection.database.models.Beacon
import de.seemoo.at_tracking_detection.database.repository.BeaconRepository
import de.seemoo.at_tracking_detection.database.repository.DeviceRepository
import de.seemoo.at_tracking_detection.database.models.device.BaseDevice
import de.seemoo.at_tracking_detection.database.models.device.DeviceType
import de.seemoo.at_tracking_detection.database.repository.NotificationRepository
import de.seemoo.at_tracking_detection.util.SharedPrefs
import timber.log.Timber
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
        Timber.d("evaluateRiskLevel() called")
        // val baseDevices: List<BaseDevice> = deviceRepository.trackingDevicesSince(relevantTrackingDate)
        val baseDevices: List<BaseDevice> = deviceRepository.trackingDevicesNotIgnoredSince(relevantTrackingDateForRiskCalculation)

        val totalTrackers = baseDevices.count()

        if (totalTrackers == 0) {
            return RiskLevel.LOW
        } else {
            var riskMediumCounterStatic = 0 // Counter for Devices with Risk Medium with a static MAC-Address (e.g. Tile, Chipolo)
            var riskMediumCounterDynamic = 0 // Counter for Devices with Risk Medium with a dynamic MAC-Address (e.g. Apple, Samsung)
            for (baseDevice in baseDevices) {
                val useLocation = SharedPrefs.useLocationInTrackingDetection
                val deviceRiskLevel = checkRiskLevelForDevice(baseDevice, useLocation)
                if (deviceRiskLevel == RiskLevel.HIGH) {
                    return RiskLevel.HIGH
                } else if (deviceRiskLevel == RiskLevel.MEDIUM) {
                    if ((baseDevice.deviceType != null) && baseDevice.deviceType.canBeIgnored()) {
                        riskMediumCounterStatic += 1
                    } else {
                        riskMediumCounterDynamic += 1
                    }
                }
            }

            val riskMediumCounterAll = riskMediumCounterDynamic + riskMediumCounterStatic

            return if (riskMediumCounterAll == 0) {
                Timber.d("Risk Level is Low")
                RiskLevel.LOW
            } else if (riskMediumCounterDynamic >= MAX_NUMBER_MEDIUM_RISK) {
                Timber.d("Risk Level is High")
                RiskLevel.HIGH
            } else {
                Timber.d("Risk Level is Medium")
                RiskLevel.MEDIUM
            }
        }
    }

    /**
     * The date when a tracker has been discovered last
     */
    fun getLastTrackerDiscoveryDate(): Date {
        val relevantDate = relevantTrackingDateForRiskCalculation
        val baseDevices: List<BaseDevice> = deviceRepository.trackingDevicesSince(relevantDate)
            .sortedByDescending { it.lastSeen }

        return baseDevices.firstOrNull()
            ?.let { Date.from(it.lastSeen.atZone(ZoneId.systemDefault()).toInstant()) }
            ?: Date()
    }

    // How many trackers have been relevant here as tracker
    fun getNumberRelevantTrackers(relevantDays: Long = RELEVANT_DAYS_RISK_LEVEL): Int {
        val relevantDate = LocalDateTime.now().minusDays(relevantDays)
        val baseDevices: List<BaseDevice> = deviceRepository.trackingDevicesNotIgnoredSince(relevantDate)

        return baseDevices.count()
    }

    companion object {
        /** The number of days that we use to calculate the risk **/
        const val RELEVANT_HOURS_TRACKING: Long = 24 // Only consider beacons in the last x hours, default value, can be overwritten in the specific device properties
        private const val DELETE_SAFE_DEVICES_OLDER_THAN_DAYS: Long = 30 // Delete devices that have been seen last more than x days ago
        const val RELEVANT_DAYS_RISK_LEVEL: Long = 14
        const val MAX_AGE_OF_LOCATION: Long = 2000L // Maximum age of a location in milliseconds
        const val PASSIVE_SCAN_TIME_BETWEEN_SCANS: Long = 5 * 60 // Time between passive scans in seconds
        private const val MINUTES_UNTIL_CACHE_IS_UPDATED: Long = 15
        private const val NUMBER_OF_NOTIFICATIONS_FOR_HIGH_RISK: Long = 2 // After x MEDIUM risk notifications (for a single device) change risk level to HIGH
        private const val RELEVANT_DAYS_NOTIFICATIONS: Long = 5 // After MEDIUM risk notifications in the last x days (for a single device) change risk level to HIGH
        private const val NUMBER_OF_BEACONS_BEFORE_ALARM: Int = 3 // Number of total beacons before notification is created
        private const val MAX_ACCURACY_FOR_LOCATIONS: Float = 100.0F // Minimum Location accuracy for high risk
        const val MAX_NUMBER_MEDIUM_RISK: Long = 3 // Maximum number of devices with MEDIUM risk until the total risk level is set to high
        val relevantTrackingDateForRiskCalculation: LocalDateTime = LocalDateTime.now().minusDays(
            RELEVANT_DAYS_RISK_LEVEL) // Fallback Option, if possible use getRelevantTrackingDate() Function
        val deleteBeforeDate: LocalDateTime = LocalDateTime.now().minusDays(DELETE_SAFE_DEVICES_OLDER_THAN_DAYS)
        private val relevantNotificationDate: LocalDateTime = LocalDateTime.now().minusDays(RELEVANT_DAYS_NOTIFICATIONS)

        // Default Values: A single tracker gets tracked at least for x minutes until notification is created
        private const val MINUTES_AT_LEAST_TRACKED_BEFORE_ALARM_HIGH: Long = 30
        private const val MINUTES_AT_LEAST_TRACKED_BEFORE_ALARM_MEDIUM: Long = 60
        private const val MINUTES_AT_LEAST_TRACKED_BEFORE_ALARM_LOW: Long = 120

        // Default Values:
        const val NUMBER_OF_LOCATIONS_BEFORE_ALARM_HIGH: Int = 2
        const val NUMBER_OF_LOCATIONS_BEFORE_ALARM_MEDIUM: Int = 3
        const val NUMBER_OF_LOCATIONS_BEFORE_ALARM_LOW: Int = 4

        private fun getAtLeastTrackedSince(): LocalDateTime = LocalDateTime.now().minusMinutes(
            getMinutesAtLeastTrackedBeforeAlarm()
        )

        fun getRelevantTrackingDateForTrackingDetection(relevantHours: Long = RELEVANT_HOURS_TRACKING): LocalDateTime = LocalDateTime.now().minusHours(relevantHours)

        fun getMinutesAtLeastTrackedBeforeAlarm(): Long {
            return when (SharedPrefs.riskSensitivity) {
                "low" -> MINUTES_AT_LEAST_TRACKED_BEFORE_ALARM_LOW
                "medium" -> MINUTES_AT_LEAST_TRACKED_BEFORE_ALARM_MEDIUM
                "high" -> MINUTES_AT_LEAST_TRACKED_BEFORE_ALARM_HIGH
                else -> MINUTES_AT_LEAST_TRACKED_BEFORE_ALARM_MEDIUM
            }
        }

        fun getNumberOfLocationsToBeConsideredForTrackingDetection(deviceType: DeviceType?): Int {
            return if (deviceType == null)  {
                when (SharedPrefs.riskSensitivity) {
                    "low" -> NUMBER_OF_LOCATIONS_BEFORE_ALARM_LOW
                    "medium" -> NUMBER_OF_LOCATIONS_BEFORE_ALARM_MEDIUM
                    "high" -> NUMBER_OF_LOCATIONS_BEFORE_ALARM_HIGH
                    else -> NUMBER_OF_LOCATIONS_BEFORE_ALARM_MEDIUM
                }
            } else {
                when (SharedPrefs.riskSensitivity) {
                    "low" -> deviceType.getNumberOfLocationsToBeConsideredForTrackingDetectionLow()
                    "medium" -> deviceType.getNumberOfLocationsToBeConsideredForTrackingDetectionMedium()
                    "high" -> deviceType.getNumberOfLocationsToBeConsideredForTrackingDetectionHigh()
                    else -> deviceType.getNumberOfLocationsToBeConsideredForTrackingDetectionMedium()
                }
            }
        }

        // Checks if BaseDevice is a tracking device
        // Goes through all the criteria
        fun checkRiskLevelForDevice(device: BaseDevice, useLocation: Boolean): RiskLevel {
            Timber.d("Checking Risk Level for Device: ${device.address}")

            val beaconRepository = ATTrackingDetectionApplication.getCurrentApp()?.beaconRepository ?: return RiskLevel.LOW

            // Not ignored
            // Tracker has been seen long enough
            if (!device.ignore && device.firstDiscovery <= getAtLeastTrackedSince()) {
                val relevantTrackingDate = relevantTrackingDateForRiskCalculation
                val numberOfBeacons = beaconRepository.getNumberOfBeaconsAddress(device.address, relevantTrackingDate)

                // Detected at least 3 Times
                if (numberOfBeacons >= NUMBER_OF_BEACONS_BEFORE_ALARM) {
                    val deviceRepository = ATTrackingDetectionApplication.getCurrentApp()?.deviceRepository ?: return RiskLevel.LOW

                    val cachedRiskLevel = deviceRepository.getCachedRiskLevel(device.address)
                    val lastCalculatedRiskLevel = deviceRepository.getLastCachedRiskLevelDate(device.address)

                    if (lastCalculatedRiskLevel != null) {
                        if (lastCalculatedRiskLevel >= LocalDateTime.now().minusMinutes(
                                MINUTES_UNTIL_CACHE_IS_UPDATED)) {
                            fun cachedRiskIntToRisk(cachedRiskInt: Int): RiskLevel {
                                return when (cachedRiskInt) {
                                    0 -> RiskLevel.LOW
                                    1 -> RiskLevel.MEDIUM
                                    2 -> RiskLevel.HIGH
                                    else -> RiskLevel.LOW
                                }
                            }

                            return cachedRiskIntToRisk(cachedRiskLevel)
                        }
                    }


                    val numberOfLocations = deviceRepository.getNumberOfLocationsForDeviceSince(device.address, relevantTrackingDate)

                    // Detected at at least 3 different locations
                    if (!useLocation || numberOfLocations >= getNumberOfLocationsToBeConsideredForTrackingDetection(device.deviceType)) {
                        val notificationRepository = ATTrackingDetectionApplication.getCurrentApp()?.notificationRepository ?: return RiskLevel.LOW
                        val falseAlarms = notificationRepository.getFalseAlarmForDeviceSinceCount(device.address, relevantTrackingDate)

                        // No False Alarm (Join via Notification)
                        if (falseAlarms == 0) {
                            val minTrackingTime = device.device.deviceContext.minTrackingTime
                            val beaconList = beaconRepository.getDeviceBeaconsSince(device.address, relevantTrackingDate)
                            val timeDiff = maxTimeDiffBetweenBeacons(beaconList)

                            // Tracker was detected for at least the minimum Tracking Time
                            if (timeDiff >= minTrackingTime) {
                                val numberOfNotifications = notificationRepository.getNotificationForDeviceSinceCount(device.address, relevantNotificationDate)
                                val numberOfLocationsWithAccuracyLimit = deviceRepository.getNumberOfLocationsForDeviceWithAccuracyLimitSince(device.address, MAX_ACCURACY_FOR_LOCATIONS, relevantTrackingDate)

                                // High Risk: High Number of Notifications and Accurate Locations
                                // Medium Risk: Low Number of Notifications or only inaccurate Location Reports
                                return if (numberOfNotifications >= NUMBER_OF_NOTIFICATIONS_FOR_HIGH_RISK && numberOfLocationsWithAccuracyLimit >= getNumberOfLocationsToBeConsideredForTrackingDetection(device.deviceType)) {
                                    deviceRepository.updateRiskLevelCache(device.address, 2, LocalDateTime.now())
                                    RiskLevel.HIGH
                                } else{
                                    deviceRepository.updateRiskLevelCache(device.address, 1, LocalDateTime.now())
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