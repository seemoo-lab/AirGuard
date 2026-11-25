package de.seemoo.at_tracking_detection.detection

import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication
import de.seemoo.at_tracking_detection.database.models.device.BaseDevice
import de.seemoo.at_tracking_detection.database.models.device.DeviceType
import de.seemoo.at_tracking_detection.database.models.device.types.AppleDevice
import de.seemoo.at_tracking_detection.database.models.device.types.AppleDeviceType
import de.seemoo.at_tracking_detection.database.repository.DeviceRepository
import de.seemoo.at_tracking_detection.notifications.NotificationService
import de.seemoo.at_tracking_detection.ui.scan.ScanResultWrapper
import de.seemoo.at_tracking_detection.util.SharedPrefs
import de.seemoo.at_tracking_detection.util.ble.DeviceSubTypeDetector
import de.seemoo.at_tracking_detection.util.risk.RiskLevelEvaluator
import timber.log.Timber
import java.time.LocalDateTime

/**
 * Evaluates immediate / proximity-based notification rules that differ from the standard
 * location-following tracker detection logic
 * Examples: e.g., Apple Watches from 2026+
 * Idea: In the future maybe Smart Glasses Support
 */
interface ImmediateDetectionRule {
    val id: String

    fun isEnabled(): Boolean

    fun isCandidate(wrapper: ScanResultWrapper): Boolean

    fun shouldConnectInBackground(wrapper: ScanResultWrapper): Boolean

    fun matchesThreat(
        deviceType: DeviceType?,
        subDeviceType: String?,
        modelIdentifier: String?,
        wrapper: ScanResultWrapper? = null
    ): Boolean

    suspend fun sendNotification(device: BaseDevice, notificationService: NotificationService): Boolean
}

/**
 * Rule for Apple Watches from 2026 or newer (identifier Watch8,1 and newer, i.e., major version >= 8).
 */
object AppleWatchImmediateRule : ImmediateDetectionRule {
    override val id: String = "apple_watch_2026"

    override fun isEnabled(): Boolean = SharedPrefs.isAppleWatchDetectionEnabled

    override fun isCandidate(wrapper: ScanResultWrapper): Boolean =
        isEnabled() && wrapper.isConnectable && wrapper.deviceType == DeviceType.APPLE

    override fun shouldConnectInBackground(wrapper: ScanResultWrapper): Boolean {
        if (!isCandidate(wrapper)) return false
        if (!AppleDevice.isRssiInConnectRange(wrapper.rssiValue)) {
            Timber.d("AppleWatchImmediateRule: Skipping background GATT connection, RSSI ${wrapper.rssiValue} is out of range (min ${AppleDevice.MIN_CONNECT_RSSI})")
            return false
        }
        return true
    }

    override fun matchesThreat(
        deviceType: DeviceType?,
        subDeviceType: String?,
        modelIdentifier: String?,
        wrapper: ScanResultWrapper?
    ): Boolean {
        if (!isEnabled() || deviceType != DeviceType.APPLE) return false

        val isAppleWatch = AppleDeviceType.stringToSubType(subDeviceType) == AppleDeviceType.APPLE_WATCH ||
            AppleDeviceType.fromModelIdentifier(modelIdentifier) == AppleDeviceType.APPLE_WATCH
        if (!isAppleWatch) return false

        val majorVersion = AppleDeviceType.parseMajorVersion(modelIdentifier)
            ?: AppleDeviceType.parseMajorVersion(subDeviceType)
            ?: 0

        return majorVersion >= 8
    }

    override suspend fun sendNotification(
        device: BaseDevice,
        notificationService: NotificationService
    ): Boolean = notificationService.sendAppleWatchNotification(device)
}

object ImmediateNotificationEvaluator {
    private val rules: List<ImmediateDetectionRule> = listOf(
        AppleWatchImmediateRule
        // Future immediate-notification rules (e.g. Meta Smart Glasses) can be registered here
    )

    fun hasEnabledCandidateRule(wrapper: ScanResultWrapper): Boolean =
        rules.any { it.isCandidate(wrapper) }

    fun shouldConnectInBackground(wrapper: ScanResultWrapper): Boolean =
        rules.any { it.shouldConnectInBackground(wrapper) }

    private fun resolveModelIdentifier(device: BaseDevice?, uid: String?, explicitModel: String? = null): String? =
        (explicitModel
            ?: uid?.let { DeviceSubTypeDetector.appleModelIdentifierMap[it] }
            ?: device?.additionalData
            ?: device?.alternativeIdentifier)?.trim()?.takeIf { it.isNotEmpty() }

    fun isHighRiskImmediateThreat(
        device: BaseDevice?,
        wrapper: ScanResultWrapper? = null
    ): Boolean {
        val deviceType = device?.deviceType ?: wrapper?.deviceType
        val uid = device?.address ?: wrapper?.uniqueIdentifier
        val subDeviceType = device?.subDeviceType?.takeIf { it != "UNKNOWN" }
            ?: uid?.let { DeviceSubTypeDetector.appleSubDeviceTypeMap[it] }?.let { AppleDeviceType.subTypeToString(it) }
        val modelIdentifier = resolveModelIdentifier(device, uid)

        return rules.any { it.matchesThreat(deviceType, subDeviceType, modelIdentifier, wrapper) }
    }

    /**
     * Evaluates all registered rules for the given device and triggers an immediate notification
     * if a rule matches and the cooldown period for the same exact model identifier (e.g. "Watch8,2")
     * has passed.
     *
     * @return true if any rule matched the device as an immediate threat (even if notification was suppressed by cooldown).
     */
    suspend fun evaluateAndNotifyIfNeeded(
        device: BaseDevice,
        modelIdentifier: String?,
        wrapper: ScanResultWrapper?,
        deviceRepository: DeviceRepository
    ): Boolean {
        val exactModel = resolveModelIdentifier(device, device.address, modelIdentifier)
        val matchingRule = rules.firstOrNull {
            it.matchesThreat(device.deviceType, device.subDeviceType, exactModel, wrapper)
        } ?: return false

        if (device.ignore || device.safeTracker) {
            Timber.d("ImmediateNotificationEvaluator: Device ${device.address} matched rule ${matchingRule.id} but is ignored/safe.")
            return false
        }

        val now = LocalDateTime.now()
        val cooldownHours = RiskLevelEvaluator.getHoursBetweenNotifications()
        val cooldownSince = now.minusHours(cooldownHours)

        // Check if this device or another entry with the same exact model identifier (e.g. "Watch8,2")
        // was already notified within the cooldown window.
        val recentlyNotifiedSameModel = (device.lastNotificationSent?.isAfter(cooldownSince) == true) ||
            deviceRepository.trackingDevicesNotIgnoredSince(cooldownSince).any { prev ->
                prev.lastNotificationSent?.isAfter(cooldownSince) == true && (
                    prev.address == device.address ||
                        (!exactModel.isNullOrEmpty() && (
                            prev.additionalData.equals(exactModel, ignoreCase = true) ||
                                prev.alternativeIdentifier.equals(exactModel, ignoreCase = true)
                            ))
                    )
            }

        if (!recentlyNotifiedSameModel) {
            Timber.d("ImmediateNotificationEvaluator: Device ${device.address} (model=$exactModel) matched rule ${matchingRule.id}. Sending immediate notification!")
            val notificationService = ATTrackingDetectionApplication.getCurrentApp()?.notificationService
            if (notificationService != null && matchingRule.sendNotification(device, notificationService)) {
                device.notificationSent = true
                device.lastNotificationSent = now
                if (!exactModel.isNullOrEmpty()) {
                    device.additionalData = exactModel
                    device.alternativeIdentifier = exactModel
                }
                deviceRepository.update(device)
            }
        } else {
            Timber.d("ImmediateNotificationEvaluator: Device ${device.address} (model=$exactModel) matched rule ${matchingRule.id}, but a notification for model '$exactModel' was already sent within the ${cooldownHours}h cooldown window.")
        }

        return true
    }
}
