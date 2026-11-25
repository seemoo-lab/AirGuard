package de.seemoo.at_tracking_detection.database.models.device

import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.content.IntentFilter
import de.seemoo.at_tracking_detection.BuildConfig
import de.seemoo.at_tracking_detection.database.models.device.types.AirPods
import de.seemoo.at_tracking_detection.database.models.device.types.AirTag
import de.seemoo.at_tracking_detection.database.models.device.types.AppleDevice
import de.seemoo.at_tracking_detection.database.models.device.types.AppleFindMy
import de.seemoo.at_tracking_detection.database.models.device.types.Chipolo
import de.seemoo.at_tracking_detection.database.models.device.types.GoogleFindMyNetwork
import de.seemoo.at_tracking_detection.database.models.device.types.PebbleBee
import de.seemoo.at_tracking_detection.database.models.device.types.SamsungFindMyMobile
import de.seemoo.at_tracking_detection.database.models.device.types.SamsungTracker
import de.seemoo.at_tracking_detection.database.models.device.types.Tile
import de.seemoo.at_tracking_detection.database.models.device.types.Unknown
import de.seemoo.at_tracking_detection.util.SharedPrefs
import de.seemoo.at_tracking_detection.util.ble.BluetoothConstants
import timber.log.Timber
import kotlin.experimental.and

object DeviceManager {

    val devices = listOf(AirTag, AppleFindMy, AirPods, AppleDevice, SamsungTracker, SamsungFindMyMobile, Tile, Chipolo, PebbleBee, GoogleFindMyNetwork)
    private val appleDevices = listOf(AirTag, AppleFindMy, AirPods, AppleDevice)
    val appleDevicesWithInfoService = listOf(AppleFindMy, AirPods).map { it.deviceType }
    val unsafeConnectionState = listOf(ConnectionState.OVERMATURE_OFFLINE, ConnectionState.UNKNOWN)
    val savedConnectionStates = unsafeConnectionState // All: enumValues<ConnectionState>().toList()
    val additionalSavedConnectionStates = listOf<Pair<DeviceType, ConnectionState>>(Pair<DeviceType, ConnectionState>(DeviceType.GOOGLE_FIND_MY_NETWORK, ConnectionState.PREMATURE_OFFLINE))

    // 15 minute algorithm: If a tracker changes its advertisement every 15 minutes we try to identify with a level of uncertainty if they are still the same
    // savedDeviceTypesWith15MinuteAlgorithm: Device types that are considered by this algorithm
    val savedDeviceTypesWith15MinuteAlgorithm = listOf(DeviceType.SAMSUNG_TRACKER, DeviceType.SAMSUNG_FIND_MY_MOBILE)
    // deviceTypesWith15MinuteAlgorithm: Device types that need to not only appear every 15 minute but also have their agingCounter be +1 compared to the previous advertisement
    // Note: it is necessary that the tracker implements an aging counter function for this to work (see: BackgroundBluetoothScanner.saveDevice)
    val strict15MinuteAlgorithm = listOf(DeviceType.SAMSUNG_TRACKER)
    // savedConnectionStatesWith15MinuteAlgorithm: Connection states that are considered by the 15 minute algorithm
    val savedConnectionStatesWith15MinuteAlgorithm = if (BuildConfig.DEBUG) {
        listOf(ConnectionState.OFFLINE, ConnectionState.PREMATURE_OFFLINE) // This makes testing on samsung phones easier
    } else {
        listOf(ConnectionState.OFFLINE)
    }

    private val deviceTypeCache = mutableMapOf<String, DeviceType>()

    fun getDeviceType(scanResult: ScanResult): DeviceType {
        val deviceAddress = scanResult.device.address
        val cachedType = getDeviceTypeFromCache(deviceAddress)
        val isFindMyAd = scanResult.scanRecord?.getManufacturerSpecificData(0x004c)?.firstOrNull() == (0x12).toByte()

        // If a previous non-FindMy (e.g. 0x10) advertisement cached APPLE, re-evaluate when a 0x12 Find My advertisement arrives
        if (cachedType != null && !(cachedType == DeviceType.APPLE && isFindMyAd)) {
            return cachedType
        }

        Timber.d("Device type not in cache, calculating...")
        val deviceType = calculateDeviceType(scanResult)
        deviceTypeCache[deviceAddress] = deviceType
        return deviceType
    }

    private fun getDeviceTypeFromCache(deviceAddress: String): DeviceType? {
        deviceTypeCache[deviceAddress]?.let { cachedDeviceType ->
            return cachedDeviceType
        }
        return null
    }

    // This allows to override the device type in the cache
    // This is used e.g. if an Apple Find My Device is upgraded to be an AirTag (necessary for AirTag 2nd Gen)
    fun overrideDeviceType(deviceAddress: String, deviceType: DeviceType) {
        Timber.d("DeviceManager: overriding device type for $deviceAddress → $deviceType")
        deviceTypeCache[deviceAddress] = deviceType
    }

    private fun calculateDeviceType(scanResult: ScanResult): DeviceType {
        Timber.d("Retrieving device type for ${scanResult.device.address}")

        scanResult.scanRecord?.let { scanRecord ->
            scanRecord.getManufacturerSpecificData(0x004c)?.let { manufacturerData ->
                val hex = manufacturerData.joinToString(" ") { "%02X".format(it) }
                Timber.d("Apple 0x004C advertisement from ${scanResult.device.address}: connectable=${scanResult.isConnectable}, rssi=${scanResult.rssi}, data=$hex")
                if (manufacturerData.size >= 3 && manufacturerData[0] == (0x12).toByte()) {
                    val statusByte: Byte = manufacturerData[2]
                    val deviceTypeInt = (statusByte.and(0x30).toInt() shr 4)

                    for (device in appleDevices) {
                        if (device.statusByteDeviceType == deviceTypeInt.toUInt()) {
                            return device.deviceType
                        }
                    }
                } else if (manufacturerData.isNotEmpty()) {
                    return DeviceType.APPLE
                }
            }

             if (scanRecord.serviceData.contains(GoogleFindMyNetwork.offlineFindingServiceUUID)) {
                return GoogleFindMyNetwork.deviceType
            }

            scanRecord.serviceUuids?.let { services ->
                return when {
                    services.contains(Tile.offlineFindingServiceUUID) -> Tile.deviceType
                    services.contains(Chipolo.offlineFindingServiceUUID) -> Chipolo.deviceType
                    services.contains(PebbleBee.offlineFindingServiceUUID) -> PebbleBee.deviceType
                    services.contains(SamsungTracker.offlineFindingServiceUUID) -> SamsungTracker.deviceType
                    services.contains(SamsungFindMyMobile.offlineFindingServiceUUID) -> SamsungFindMyMobile.deviceType
                    else -> Unknown.deviceType
                }
            }
        }
        return Unknown.deviceType
    }

    fun getWebsiteURL(deviceType: DeviceType): String {
        return when (deviceType) {
            DeviceType.UNKNOWN -> Unknown.websiteManufacturer
            DeviceType.AIRTAG -> AirTag.websiteManufacturer
            DeviceType.APPLE -> AppleDevice.websiteManufacturer
            DeviceType.AIRPODS -> AirPods.websiteManufacturer
            DeviceType.TILE -> Tile.websiteManufacturer
            DeviceType.FIND_MY -> AppleFindMy.websiteManufacturer
            DeviceType.CHIPOLO -> Chipolo.websiteManufacturer
            DeviceType.PEBBLEBEE -> PebbleBee.websiteManufacturer
            DeviceType.SAMSUNG_TRACKER -> SamsungTracker.websiteManufacturer
            DeviceType.SAMSUNG_FIND_MY_MOBILE -> SamsungFindMyMobile.websiteManufacturer
            DeviceType.GOOGLE_FIND_MY_NETWORK -> GoogleFindMyNetwork.websiteManufacturer
        }
    }

    fun deviceTypeToString(deviceType: DeviceType): String {
        return deviceType.name
    }

    fun stringToDeviceType(deviceTypeString: String): DeviceType {
        return DeviceType.valueOf(deviceTypeString)
    }

    val scanFilter: List<ScanFilter>
        get() = buildList {
            devices.mapTo(this) { it.bluetoothFilter }
            if (SharedPrefs.isAppleWatchDetectionEnabled) {
                add(AppleDevice.continuityBluetoothFilter)
            }
        }

    val gattIntentFilter: IntentFilter = IntentFilter().apply {
        addAction(BluetoothConstants.ACTION_EVENT_RUNNING)
        addAction(BluetoothConstants.ACTION_GATT_DISCONNECTED)
        addAction(BluetoothConstants.ACTION_EVENT_COMPLETED)
        addAction(BluetoothConstants.ACTION_EVENT_FAILED)
    }
}