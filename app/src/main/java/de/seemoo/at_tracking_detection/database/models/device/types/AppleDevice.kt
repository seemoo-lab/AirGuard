package de.seemoo.at_tracking_detection.database.models.device.types

import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import androidx.annotation.DrawableRes
import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.database.models.device.ConnectionState
import de.seemoo.at_tracking_detection.database.models.device.Device
import de.seemoo.at_tracking_detection.database.models.device.DeviceContext
import de.seemoo.at_tracking_detection.database.models.device.DeviceType
import de.seemoo.at_tracking_detection.ui.scan.ScanResultWrapper
import de.seemoo.at_tracking_detection.util.Utility
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds

class AppleDevice(val id: Int) : Device() {
    override val imageResource: Int
        @DrawableRes
        get() = R.drawable.ic_baseline_device_unknown_24

    override val defaultDeviceNameWithId: String
        get() = ATTrackingDetectionApplication.getAppContext().resources
            .getString(R.string.device_name_apple_device).format(id)

    override val deviceContext: DeviceContext
        get() = AppleDevice

    companion object : DeviceContext {
        private val GATT_DEVICE_INFORMATION_SERVICE: UUID =
            UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb")
        private val GATT_MODEL_NUMBER_CHARACTERISTIC: UUID =
            UUID.fromString("00002a24-0000-1000-8000-00805f9b34fb")

        override val bluetoothFilter: ScanFilter
            get() = ScanFilter.Builder()
                .setManufacturerData(
                    0x4C,
                    // Only Offline Devices:
                    // byteArrayOf((0x12).toByte(), (0x19).toByte(), (0x00).toByte()),
                    // byteArrayOf((0xFF).toByte(), (0xFF).toByte(), (0x18).toByte())
                    // All Devices:
                    byteArrayOf((0x12).toByte(), (0x19).toByte()),
                    byteArrayOf((0xFF).toByte(), (0x00).toByte())
                )
                .build()

        override val deviceType: DeviceType
            get() = DeviceType.APPLE

        override val defaultDeviceName: String
            get() = ATTrackingDetectionApplication.getAppContext().resources
                .getString(R.string.apple_device_default_name)

        override val minTrackingTime: Int
            get() = 150 * 60

        override val statusByteDeviceType: UInt
            get() = 0u

        override val websiteManufacturer: String
            get() = "https://www.apple.com/"

        val continuityBluetoothFilter: ScanFilter
            get() = ScanFilter.Builder()
                .setManufacturerData(
                    0x4C,
                    byteArrayOf((0x10).toByte()),
                    byteArrayOf((0xFF).toByte())
                )
                .build()

        override fun getConnectionState(scanResult: ScanResult): ConnectionState {
            val mfg = scanResult.scanRecord?.getManufacturerSpecificData(0x4C)
            if (mfg != null && mfg.isNotEmpty() && mfg[0] != (0x12).toByte()) {
                return ConnectionState.CONNECTED
            }
            return AppleFindMy.getConnectionState(scanResult)
        }

        const val MIN_CONNECT_RSSI = -70

        fun isRssiInConnectRange(rssi: Int): Boolean = rssi in MIN_CONNECT_RSSI..0

        /**
         * Connects to the Apple device via BLE GATT and requests the Model Number String
         * characteristic (0x2A24) from the Device Information Service (0x180A).
         *
         * This supports all major Apple devices (as of November 2026) but we only use Apple Watch for now.
         *
         * Example model identifiers returned by Apple devices and how they are labeled:
         * - "iPhone19,4"         -> AppleDeviceType.IPHONE ("iPhone")
         * - "iPad15,7"           -> AppleDeviceType.IPAD ("iPad")
         * - "iPod9,1"            -> AppleDeviceType.IPOD ("iPod")
         * - "Mac17,4"            -> AppleDeviceType.MAC ("Mac")
         * - "Watch8,1"           -> AppleDeviceType.APPLE_WATCH ("Apple Watch")
         * - "AppleTV18,1"        -> AppleDeviceType.APPLE_TV ("Apple TV")
         * - "AudioAccessory11,1" -> AppleDeviceType.HOMEPOD ("Apple HomePod")
         * - "HomeAccessory17,1"  -> AppleDeviceType.APPLE_HOMEVIEW ("Apple HomeView")
         * - "RealityDevice17,1"  -> AppleDeviceType.APPLE_VISION_PRO ("Apple Vision Pro")
         * - "AirPods3,8"         -> AppleDeviceType.AIRPODS ("AirPods", migrated to DeviceType.AIRPODS)
         * - "AirTag1,1"          -> AppleDeviceType.AIRTAG ("AirTag", migrated to DeviceType.AIRTAG)
         */
        suspend fun getModelIdentifier(
            wrappedScanResult: ScanResultWrapper,
            timeoutMs: Long = 10000L
        ): String? {
            if (!wrappedScanResult.isConnectable || !isRssiInConnectRange(wrappedScanResult.rssiValue)) {
                Timber.d(
                    "AppleDevice: Skipping GATT connection for ${wrappedScanResult.deviceAddress} " +
                        "(connectable=${wrappedScanResult.isConnectable}, rssi=${wrappedScanResult.rssiValue})"
                )
                return null
            }
            val characteristicsToRead = listOf(
                Triple(GATT_DEVICE_INFORMATION_SERVICE, GATT_MODEL_NUMBER_CHARACTERISTIC, "string")
            )
            return try {
                Timber.d("AppleDevice: Connecting via GATT (${timeoutMs}ms timeout) to retrieve model number on ${wrappedScanResult.deviceAddress} (rssi=${wrappedScanResult.rssiValue})")
                val rawModel = withTimeoutOrNull(timeoutMs.milliseconds) {
                    Utility.connectAndRetrieveCharacteristics(
                        ATTrackingDetectionApplication.getAppContext(),
                        wrappedScanResult.deviceAddress,
                        characteristicsToRead
                    )[GATT_MODEL_NUMBER_CHARACTERISTIC] as? String
                }
                rawModel?.trim()?.takeIf { it.isNotEmpty() }
            } catch (e: Exception) {
                Timber.w("AppleDevice: Failed to retrieve model identifier for ${wrappedScanResult.deviceAddress}: ${e.message}")
                null
            }
        }
    }
}