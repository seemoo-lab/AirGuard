package de.seemoo.at_tracking_detection.util.ble

import android.location.Location
import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.database.models.device.BaseDevice
import de.seemoo.at_tracking_detection.database.models.device.DeviceManager
import de.seemoo.at_tracking_detection.database.models.device.DeviceType
import de.seemoo.at_tracking_detection.database.models.device.types.AppleDevice
import de.seemoo.at_tracking_detection.database.models.device.types.AppleDeviceType
import de.seemoo.at_tracking_detection.database.models.device.types.AppleFindMy
import de.seemoo.at_tracking_detection.database.models.device.types.GoogleFindMyNetwork
import de.seemoo.at_tracking_detection.database.models.device.types.GoogleFindMyNetworkType
import de.seemoo.at_tracking_detection.database.models.device.types.PebbleBee
import de.seemoo.at_tracking_detection.database.models.device.types.SamsungFindMyMobile
import de.seemoo.at_tracking_detection.database.models.device.types.SamsungTracker
import de.seemoo.at_tracking_detection.database.models.device.types.SamsungTrackerType
import de.seemoo.at_tracking_detection.database.repository.DeviceRepository
import de.seemoo.at_tracking_detection.detection.BackgroundBluetoothScanner
import de.seemoo.at_tracking_detection.detection.ImmediateNotificationEvaluator
import de.seemoo.at_tracking_detection.ui.scan.ScanResultWrapper
import de.seemoo.at_tracking_detection.util.SharedPrefs
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object DeviceSubTypeDetector {
    private val appleDbMutex = Mutex()
    val samsungSubDeviceTypeMap: MutableMap<String, SamsungTrackerType> = ConcurrentHashMap()
    val googleSubDeviceTypeMap: MutableMap<String, GoogleFindMyNetworkType> = ConcurrentHashMap()
    val appleSubDeviceTypeMap: MutableMap<String, AppleDeviceType> = ConcurrentHashMap()
    val appleModelIdentifierMap: MutableMap<String, String> = ConcurrentHashMap()
    val googleExactTagDeterminedMap: MutableMap<String, Boolean> = ConcurrentHashMap()
    val deviceNameMap: MutableMap<String, String> = ConcurrentHashMap()

    fun needsDetection(wrapper: ScanResultWrapper): Boolean {
        if (!wrapper.isConnectable) return false
        val uid = wrapper.uniqueIdentifier
        return when (wrapper.deviceType) {
            DeviceType.SAMSUNG_TRACKER -> {
                val subType = samsungSubDeviceTypeMap[uid]
                subType == null || subType == SamsungTrackerType.UNKNOWN
            }
            DeviceType.GOOGLE_FIND_MY_NETWORK -> {
                val subType = GoogleFindMyNetwork.getSubType(wrapper)
                subType == GoogleFindMyNetworkType.TAG
                        && wrapper.connectionState in DeviceManager.unsafeConnectionState
                        && googleExactTagDeterminedMap[uid] != true
            }
            in DeviceManager.appleDevicesWithInfoService -> {
                val deviceName = deviceNameMap[uid]
                val findMyDefault = ATTrackingDetectionApplication.getAppContext()
                    .resources.getString(R.string.apple_find_my_default_name)
                deviceName.isNullOrEmpty() || deviceName == findMyDefault
            }
            DeviceType.PEBBLEBEE -> {
                val deviceName = deviceNameMap[uid]
                val pebblebeeDefault = ATTrackingDetectionApplication.getAppContext()
                    .resources.getString(R.string.pebblebee_default_name)
                deviceName.isNullOrEmpty() || deviceName == pebblebeeDefault
            }
            DeviceType.SAMSUNG_FIND_MY_MOBILE -> {
                val deviceName = deviceNameMap[uid]
                val samsungFMMDefault = ATTrackingDetectionApplication.getAppContext()
                    .resources.getString(R.string.samsung_find_my_mobile_name)
                deviceName.isNullOrEmpty() || deviceName == samsungFMMDefault
            }
            DeviceType.APPLE -> {
                if (!AppleDevice.isRssiInConnectRange(wrapper.rssiValue)) return false
                val subType = appleSubDeviceTypeMap[uid]
                subType == null || subType == AppleDeviceType.UNKNOWN
            }
            else -> false
        }
    }

    suspend fun processDetection(
        wrapper: ScanResultWrapper,
        deviceRepository: DeviceRepository,
        location: Location? = null
    ): DetectionResult {
        if (!wrapper.isConnectable) return DetectionResult()
        val uid = wrapper.uniqueIdentifier
        var result = DetectionResult()

        when (wrapper.deviceType) {
            DeviceType.SAMSUNG_TRACKER -> {
                val subType = SamsungTracker.getSubType(wrapper)
                samsungSubDeviceTypeMap[uid] = subType
                if (subType != SamsungTrackerType.UNKNOWN) {
                    deviceRepository.getDevice(uid)?.let { device ->
                        device.subDeviceType = SamsungTrackerType.subTypeToString(subType)
                        deviceRepository.update(device)
                    }
                    result = DetectionResult(
                        deviceName = SamsungTrackerType.visibleStringFromSubtype(subType),
                        subDeviceType = SamsungTrackerType.subTypeToString(subType)
                    )
                }
            }

            DeviceType.GOOGLE_FIND_MY_NETWORK -> {
                val subType = GoogleFindMyNetwork.getSubType(wrapper)
                googleSubDeviceTypeMap[uid] = subType
                val errorCaseName = GoogleFindMyNetworkType.visibleStringFromSubtype(subType)

                if (wrapper.connectionState in DeviceManager.unsafeConnectionState) {
                    val deviceName = GoogleFindMyNetwork.getDeviceName(wrapper)
                    deviceRepository.getDevice(uid)?.let { device ->
                        if (deviceName != errorCaseName && deviceName.isNotEmpty()) {
                            device.name = deviceName
                        }
                        device.subDeviceType = GoogleFindMyNetworkType.subTypeToString(subType)
                        deviceRepository.update(device)

                        if (subType == GoogleFindMyNetworkType.TAG
                            && deviceName != errorCaseName
                            && deviceName.isNotEmpty()
                        ) {
                            googleExactTagDeterminedMap[uid] = true
                        }
                    }
                    result = DetectionResult(
                        deviceName = deviceName,
                        subDeviceType = GoogleFindMyNetworkType.subTypeToString(subType)
                    )
                }
            }

            in DeviceManager.appleDevicesWithInfoService -> {
                val findMyDefault = ATTrackingDetectionApplication.getAppContext()
                    .resources.getString(R.string.apple_find_my_default_name)
                val deviceName = AppleFindMy.getSubTypeName(wrapper)

                if (deviceName != findMyDefault && deviceName.isNotEmpty()) {
                    deviceNameMap[uid] = deviceName
                    var isUpgradeAirTag = false
                    var isUpgradeAirPods = false
                    if (deviceName.startsWith("airtag", ignoreCase = true) && wrapper.deviceType == DeviceType.FIND_MY) {
                        DeviceManager.overrideDeviceType(uid, DeviceType.AIRTAG)
                        isUpgradeAirTag = true
                        Timber.d("DeviceSubTypeDetector: Upgraded FIND_MY → AIRTAG for %s", uid)
                    } else if (deviceName.startsWith("airpod", ignoreCase = true) && wrapper.deviceType == DeviceType.FIND_MY) {
                        DeviceManager.overrideDeviceType(uid, DeviceType.AIRPODS)
                        isUpgradeAirPods = true
                        Timber.d("DeviceSubTypeDetector: Upgraded FIND_MY → AIRPODS for %s", uid)
                    }

                    deviceRepository.getDevice(uid)?.let { device ->
                        device.name = deviceName
                        when {
                            isUpgradeAirTag -> deviceRepository.update(device.copy(deviceType = DeviceType.AIRTAG))
                            isUpgradeAirPods -> deviceRepository.update(device.copy(deviceType = DeviceType.AIRPODS))
                            else -> deviceRepository.update(device)
                        }
                    }

                    result = DetectionResult(
                        deviceName = deviceName,
                        isUpgradeToAirTag = isUpgradeAirTag,
                        isUpgradeToAirPods = isUpgradeAirPods
                    )
                }
            }

            DeviceType.PEBBLEBEE -> {
                val pebblebeeDefault = ATTrackingDetectionApplication.getAppContext()
                    .resources.getString(R.string.pebblebee_default_name)
                val deviceName = PebbleBee.getSubTypeName(wrapper)

                if (deviceName != pebblebeeDefault && deviceName.isNotEmpty()) {
                    deviceNameMap[uid] = deviceName
                    deviceRepository.getDevice(uid)?.let { device ->
                        device.name = deviceName
                        deviceRepository.update(device)
                    }
                    result = DetectionResult(deviceName = deviceName)
                }
            }

            DeviceType.SAMSUNG_FIND_MY_MOBILE -> {
                val deviceName = SamsungFindMyMobile.getSubTypeName(wrapper)
                deviceNameMap[uid] = deviceName
                deviceRepository.getDevice(uid)?.let { device ->
                    device.name = deviceName
                    deviceRepository.update(device)
                }
                result = DetectionResult(deviceName = deviceName)
            }

            DeviceType.APPLE -> {
                result = processAppleDevice(wrapper, deviceRepository, location)
            }

            else -> {}
        }
        return result
    }

    private suspend fun processAppleDevice(
        wrapper: ScanResultWrapper,
        deviceRepository: DeviceRepository,
        location: Location?
    ): DetectionResult {
        val uid = wrapper.uniqueIdentifier
        Timber.d("DeviceSubTypeDetector: Apple device detected (${wrapper.deviceAddress}), querying model identifier via GATT...")
        val modelNumber = AppleDevice.getModelIdentifier(wrapper)
        val subType = AppleDeviceType.fromModelIdentifier(modelNumber)
        Timber.d("DeviceSubTypeDetector: Retrieved Apple model number: $modelNumber → subType: $subType")

        modelNumber?.let { appleModelIdentifierMap[uid] = it }
        if (subType == AppleDeviceType.UNKNOWN) {
            Timber.d("DeviceSubTypeDetector: Could not resolve known Apple subtype (model=$modelNumber).")
            return DetectionResult()
        }

        appleSubDeviceTypeMap[uid] = subType
        val visibleName = AppleDeviceType.visibleStringFromSubtype(subType)
        val subTypeString = AppleDeviceType.subTypeToString(subType)
        deviceNameMap[uid] = visibleName

        val isUpgradeAirPods = subType == AppleDeviceType.AIRPODS
        val isUpgradeAirTag = subType == AppleDeviceType.AIRTAG
        val targetDeviceType = when (subType) {
            AppleDeviceType.AIRPODS -> DeviceType.AIRPODS.also { DeviceManager.overrideDeviceType(uid, it) }
            AppleDeviceType.AIRTAG -> DeviceType.AIRTAG.also { DeviceManager.overrideDeviceType(uid, it) }
            else -> DeviceType.APPLE
        }

        appleDbMutex.withLock {
            val existing = deviceRepository.getDevice(uid)
                ?: modelNumber?.takeIf { it.isNotEmpty() && subType == AppleDeviceType.APPLE_WATCH }
                    ?.let { deviceRepository.getDeviceWithAlternativeIdentifier(it) }
                    ?.also {
                        Timber.d("DeviceSubTypeDetector: Matched existing Apple Watch DB entry (id=${it.deviceId}, oldAddress=${it.address}) by model '$modelNumber'")
                    }

            val now = LocalDateTime.now()
            val candidateDevice = existing?.copy(
                address = uid,
                deviceType = targetDeviceType,
                subDeviceType = subTypeString,
                lastSeen = now,
                additionalData = modelNumber ?: existing.additionalData,
                alternativeIdentifier = modelNumber ?: existing.alternativeIdentifier
            ) ?: BaseDevice(
                deviceId = 0,
                uniqueId = UUID.randomUUID().toString(),
                address = uid,
                name = null,
                ignore = false,
                hearted = false,
                connectable = wrapper.isConnectable,
                payloadData = wrapper.payloadData,
                firstDiscovery = now,
                lastSeen = now,
                notificationSent = false,
                lastNotificationSent = null,
                deviceType = targetDeviceType,
                subDeviceType = subTypeString,
                riskLevel = 0,
                lastCalculatedRiskDate = null,
                nextObservationNotification = null,
                currentObservationDuration = null,
                additionalData = modelNumber,
                alternativeIdentifier = modelNumber
            )

            val isImmediateThreat = ImmediateNotificationEvaluator.isHighRiskImmediateThreat(
                device = candidateDevice,
                wrapper = wrapper
            )

            if (isImmediateThreat) {
                val savedDevice = if (existing == null) {
                    deviceRepository.insert(candidateDevice)
                    Timber.d("DeviceSubTypeDetector: Created new DB entry for immediate threat Apple device ($subTypeString, model=$modelNumber)")
                    deviceRepository.getDevice(uid)
                } else {
                    if (existing.address != uid) {
                        migrateDeviceAddress(existing, candidateDevice, deviceRepository)
                    } else {
                        deviceRepository.update(candidateDevice)
                    }
                    Timber.d("DeviceSubTypeDetector: Updated immediate threat Apple device DB entry (address=$uid, subDeviceType=$subTypeString)")
                    candidateDevice
                }

                if (savedDevice != null) {
                    val effectiveLocation = location
                        ?: if (SharedPrefs.useLocationInTrackingDetection) {
                            ATTrackingDetectionApplication.getCurrentApp()?.locationProvider?.getLastLocation(checkRequirements = false)
                        } else {
                            null
                        }

                    // Also save beacon for immediate threat device
                    BackgroundBluetoothScanner.saveBeaconForDevice(
                        wrappedScanResult = wrapper,
                        deviceAddress = savedDevice.address,
                        location = effectiveLocation,
                        discoveryDate = now
                    )
                    ImmediateNotificationEvaluator.evaluateAndNotifyIfNeeded(
                        device = savedDevice,
                        modelIdentifier = modelNumber,
                        wrapper = wrapper,
                        deviceRepository = deviceRepository
                    )
                }
            } else if (existing != null) {
                val updated = existing.copy(
                    deviceType = targetDeviceType,
                    subDeviceType = subTypeString,
                    lastSeen = now,
                    additionalData = modelNumber ?: existing.additionalData,
                    alternativeIdentifier = modelNumber ?: existing.alternativeIdentifier
                )
                deviceRepository.update(updated)
                Timber.d("DeviceSubTypeDetector: Updated existing Apple device DB entry (type=$targetDeviceType, subDeviceType=$subTypeString)")
            } else {
                Timber.d("DeviceSubTypeDetector: Apple device ($subTypeString, model=$modelNumber) is not an immediate threat, not storing in DB.")
            }
        }

        return DetectionResult(
            deviceName = visibleName,
            subDeviceType = subTypeString,
            isUpgradeToAirTag = isUpgradeAirTag,
            isUpgradeToAirPods = isUpgradeAirPods
        )
    }

    /**
     * Safely updates a BaseDevice's MAC address when matched by model identifier.
     */
    private suspend fun migrateDeviceAddress(
        existing: BaseDevice,
        updatedDevice: BaseDevice,
        deviceRepository: DeviceRepository
    ) {
        val app = ATTrackingDetectionApplication.getCurrentApp()
        val beaconRepository = app?.beaconRepository
        val notificationRepository = app?.notificationRepository

        val existingBeacons = beaconRepository?.getDeviceBeacons(existing.address).orEmpty()
        val existingNotifications = notificationRepository?.notificationForDevice(existing).orEmpty()

        if (existingBeacons.isNotEmpty()) {
            beaconRepository?.deleteBeacons(existingBeacons)
        }

        deviceRepository.update(updatedDevice)

        existingBeacons.forEach { beacon ->
            beaconRepository?.insert(beacon.copy(beaconId = 0, deviceAddress = updatedDevice.address))
        }
        existingNotifications.forEach { notification ->
            notificationRepository?.update(notification.copy(deviceAddress = updatedDevice.address))
        }
    }

    data class DetectionResult(
        val deviceName: String? = null,
        val subDeviceType: String? = null,
        val isUpgradeToAirTag: Boolean = false,
        val isUpgradeToAirPods: Boolean = false
    )
}
