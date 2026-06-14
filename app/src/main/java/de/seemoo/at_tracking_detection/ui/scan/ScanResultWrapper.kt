package de.seemoo.at_tracking_detection.ui.scan

import android.annotation.SuppressLint
import android.bluetooth.le.ScanResult
import androidx.databinding.ObservableField
import de.seemoo.at_tracking_detection.database.models.device.BaseDevice.Companion.getAlternativeIdentifier
import de.seemoo.at_tracking_detection.database.models.device.BaseDevice.Companion.getConnectionState
import de.seemoo.at_tracking_detection.database.models.device.BaseDevice.Companion.getUniqueIdentifier
import de.seemoo.at_tracking_detection.database.models.device.ConnectionState
import de.seemoo.at_tracking_detection.database.models.device.DeviceManager.getDeviceType
import de.seemoo.at_tracking_detection.database.models.device.DeviceType
import de.seemoo.at_tracking_detection.database.models.device.TrackingNetwork
import de.seemoo.at_tracking_detection.database.models.device.types.SamsungFindMyMobile
import de.seemoo.at_tracking_detection.database.models.device.types.SamsungTracker

@SuppressLint("MissingPermission")
data class ScanResultWrapper(val scanResult: ScanResult) {
    enum class DetectionStatus { IDLE, QUEUED, CONNECTING }

    val deviceAddress: String = scanResult.device.address
    val rssi: ObservableField<Int> = ObservableField(scanResult.rssi) // This is so the image can update itself live
    var rssiValue: Int = scanResult.rssi
    var txPower: Int = scanResult.txPower
    var isConnectable: Boolean = scanResult.isConnectable
    val deviceType = getDeviceType(scanResult)
    val uniqueIdentifier: String = getUniqueIdentifier(scanResult, deviceType)  // either public key or MAC-Address
    val alternativeIdentifier: String? = getAlternativeIdentifier(scanResult, deviceType) // null if not applicable for this tracker type
    var connectionState = getConnectionState(scanResult, deviceType)
    val serviceUuids = scanResult.scanRecord?.serviceUuids?.map { it.toString() }?.toList()
    val mfg = scanResult.scanRecord?.bytes
    val advertisementFlags = scanResult.scanRecord?.advertiseFlags

    // This value holds the current state for the manual scan if GATT-connection is enabled (only used by trackers who support GATT connections)
    var detectionStatus: DetectionStatus = DetectionStatus.IDLE

    // Information for Sub categorization
    val advertisedName: String? = scanResult.device.name
    val uwbCapable = when (deviceType) {
        DeviceType.SAMSUNG_TRACKER -> SamsungTracker.getUwbAvailability(scanResult)
        DeviceType.AIRTAG -> true
        else -> false
    }

    // Fields previously only available on ScannedDevice — pre-computed here so the raw
    // ScanResult can be freed as soon as this wrapper is created.
    val payloadData: Byte? = scanResult.scanRecord?.getManufacturerSpecificData(76)?.get(2)

    // Name to persist in the database. Samsung Trackers use null
    val deviceName: String? = if (deviceType == DeviceType.SAMSUNG_TRACKER) {
        null
    } else {
        scanResult.scanRecord?.deviceName
    }

    val trackerProperties: Byte? = when (deviceType) {
        DeviceType.SAMSUNG_TRACKER -> SamsungTracker.getPropertiesByte(scanResult)
        DeviceType.SAMSUNG_FIND_MY_MOBILE -> SamsungFindMyMobile.getPropertiesByte(scanResult)
        else -> null
    }

    // Samsung Trackers: Counter increases every 15 minutes
    val agingCounter: ByteArray? = if (deviceType == DeviceType.SAMSUNG_TRACKER)
        SamsungTracker.getInternalAgingCounter(scanResult) else null

    override fun hashCode(): Int = uniqueIdentifier.hashCode()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ScanResultWrapper) return false
        return uniqueIdentifier == other.uniqueIdentifier
    }

    /**
     * Checks if the device is performing tracking.
     * For example AirTags only send out longer advertisements when they are tracking.
     * Other trackers are usually always trackable.
     */
    fun deviceIsTracking(): Boolean {
        if (deviceType.getTrackingNetwork() == TrackingNetwork.APPLE_FIND_MY) {
            if (connectionState == ConnectionState.CONNECTED) {
                return false
            }
        }
        return true
    }
}