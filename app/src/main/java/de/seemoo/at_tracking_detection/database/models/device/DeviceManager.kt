package de.seemoo.at_tracking_detection.database.models.device

import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.content.IntentFilter
import de.seemoo.at_tracking_detection.database.models.device.types.*
import de.seemoo.at_tracking_detection.util.ble.BluetoothConstants
import timber.log.Timber
import kotlin.experimental.and

object DeviceManager {

    val devices = listOf(AirTag, FindMy, AirPods, AppleDevice, SamsungDevice, Tile, Chipolo, GoogleFindMyNetwork)
    private val appleDevices = listOf(AirTag, FindMy, AirPods, AppleDevice)
    val unsafeConnectionState = listOf(ConnectionState.OVERMATURE_OFFLINE, ConnectionState.UNKNOWN)
    val savedConnectionStates = unsafeConnectionState //enumValues<ConnectionState>().toList()

    private val deviceTypeCache = mutableMapOf<String, DeviceType>()

    fun getDeviceType(scanResult: ScanResult): DeviceType {
        val deviceAddress = scanResult.device.address

        // Check cache, before calculating again
        var deviceType: DeviceType? = getDeviceTypeFromCache(deviceAddress)
        if (deviceType != null) {
            return deviceType
        } else {
            Timber.d("Device type not in cache, calculating...")
            deviceType = calculateDeviceType(scanResult)
            deviceTypeCache[deviceAddress] = deviceType
            return deviceType
        }
    }

    private fun getDeviceTypeFromCache(deviceAddress: String): DeviceType? {
        deviceTypeCache[deviceAddress]?.let { cachedDeviceType ->
            return cachedDeviceType
        }
        return null
    }

    private fun calculateDeviceType(scanResult: ScanResult): DeviceType {
        Timber.d("Retrieving device type for ${scanResult.device.address}")

        scanResult.scanRecord?.let { scanRecord ->
            scanRecord.getManufacturerSpecificData(0x004c)?.let { manufacturerData ->
                if (manufacturerData.size >= 3) { // Ensure array size is sufficient
                    val statusByte: Byte = manufacturerData[2]
                    val deviceTypeInt = (statusByte.and(0x30).toInt() shr 4)

                    for (device in appleDevices) {
                        if (device.statusByteDeviceType == deviceTypeInt.toUInt()) {
                            return device.deviceType
                        }
                    }
                }
            }

             if (scanRecord.serviceData.contains(GoogleFindMyNetwork.offlineFindingServiceUUID)) {
                return GoogleFindMyNetwork.deviceType
            }

            scanRecord.serviceUuids?.let { services ->
                when {
                    services.contains(Tile.offlineFindingServiceUUID) -> return Tile.deviceType
                    services.contains(Chipolo.offlineFindingServiceUUID) -> return Chipolo.deviceType
                    services.contains(SamsungDevice.offlineFindingServiceUUID) -> return SamsungDevice.deviceType
                    else -> return Unknown.deviceType
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
            DeviceType.FIND_MY -> FindMy.websiteManufacturer
            DeviceType.CHIPOLO -> Chipolo.websiteManufacturer
            DeviceType.SAMSUNG_DEVICE -> SamsungDevice.websiteManufacturer
            DeviceType.GOOGLE_FIND_MY_NETWORK -> GoogleFindMyNetwork.websiteManufacturer
        }
    }

    fun deviceTypeToString(deviceType: DeviceType): String {
        return deviceType.name
    }

    fun stringToDeviceType(deviceTypeString: String): DeviceType {
        return DeviceType.valueOf(deviceTypeString)
    }

    val scanFilter: List<ScanFilter> = devices.map {
        it.bluetoothFilter
    }

    val gattIntentFilter: IntentFilter = IntentFilter().apply {
        addAction(BluetoothConstants.ACTION_EVENT_RUNNING)
        addAction(BluetoothConstants.ACTION_GATT_DISCONNECTED)
        addAction(BluetoothConstants.ACTION_EVENT_COMPLETED)
        addAction(BluetoothConstants.ACTION_EVENT_FAILED)
    }
}