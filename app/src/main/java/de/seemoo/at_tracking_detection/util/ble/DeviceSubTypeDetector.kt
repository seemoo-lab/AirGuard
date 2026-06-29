package de.seemoo.at_tracking_detection.util.ble

import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.database.models.device.DeviceManager
import de.seemoo.at_tracking_detection.database.models.device.DeviceType
import de.seemoo.at_tracking_detection.database.models.device.types.AppleFindMy
import de.seemoo.at_tracking_detection.database.models.device.types.GoogleFindMyNetwork
import de.seemoo.at_tracking_detection.database.models.device.types.GoogleFindMyNetworkType
import de.seemoo.at_tracking_detection.database.models.device.types.PebbleBee
import de.seemoo.at_tracking_detection.database.models.device.types.SamsungFindMyMobile
import de.seemoo.at_tracking_detection.database.models.device.types.SamsungTracker
import de.seemoo.at_tracking_detection.database.models.device.types.SamsungTrackerType
import de.seemoo.at_tracking_detection.database.repository.DeviceRepository
import de.seemoo.at_tracking_detection.ui.scan.ScanResultWrapper
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

object DeviceSubTypeDetector {
    val samsungSubDeviceTypeMap: MutableMap<String, SamsungTrackerType> = ConcurrentHashMap()
    val googleSubDeviceTypeMap: MutableMap<String, GoogleFindMyNetworkType> = ConcurrentHashMap()
    val googleExactTagDeterminedMap: MutableMap<String, Boolean> = ConcurrentHashMap()
    val deviceNameMap: MutableMap<String, String> = ConcurrentHashMap()

    fun needsDetection(wrapper: ScanResultWrapper): Boolean {
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
            else -> false
        }
    }

    suspend fun processDetection(
        wrapper: ScanResultWrapper,
        deviceRepository: DeviceRepository
    ): DetectionResult {
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
                    deviceRepository.getDevice(uid)?.let { device ->
                        device.name = deviceName
                        var isUpgradeAirTag = false
                        var isUpgradeAirPods = false
                        if (deviceName.take(6) == "AirTag" && wrapper.deviceType == DeviceType.FIND_MY) {
                            val upgraded = device.copy(deviceType = DeviceType.AIRTAG)
                            deviceRepository.update(upgraded)
                            DeviceManager.overrideDeviceType(uid, DeviceType.AIRTAG)
                            isUpgradeAirTag = true
                            Timber.d("DeviceSubTypeDetector: Upgraded FIND_MY → AIRTAG for %s", uid)
                        } else if (deviceName.startsWith("AirPods") && wrapper.deviceType == DeviceType.FIND_MY) {
                            val upgraded = device.copy(deviceType = DeviceType.AIRPODS)
                            deviceRepository.update(upgraded)
                            DeviceManager.overrideDeviceType(uid, DeviceType.AIRPODS)
                            isUpgradeAirTag = true
                            isUpgradeAirPods = true
                            Timber.d("DeviceSubTypeDetector: Upgraded FIND_MY → AIRPODS for %s", uid)
                        } else {
                            deviceRepository.update(device)
                        }
                        result = DetectionResult(
                            deviceName = deviceName,
                            isUpgradeToAirTag = isUpgradeAirTag,
                            isUpgradeToAirPods = isUpgradeAirPods
                        )
                    }
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

            else -> {}
        }
        return result
    }

    data class DetectionResult(
        val deviceName: String? = null,
        val subDeviceType: String? = null,
        val isUpgradeToAirTag: Boolean = false,
        val isUpgradeToAirPods: Boolean = false
    )
}
