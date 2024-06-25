package de.seemoo.at_tracking_detection.database.models.device.types

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.ParcelUuid
import androidx.annotation.DrawableRes
import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.database.models.device.BatteryState
import de.seemoo.at_tracking_detection.database.models.device.ConnectionState
import de.seemoo.at_tracking_detection.database.models.device.Device
import de.seemoo.at_tracking_detection.database.models.device.DeviceContext
import de.seemoo.at_tracking_detection.database.models.device.DeviceType
import de.seemoo.at_tracking_detection.ui.scan.ScanResultWrapper
import de.seemoo.at_tracking_detection.util.Utility.getBitsFromByte
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class SamsungDevice(val id: Int) : Device() {
    override val imageResource: Int
        @DrawableRes
        get() = R.drawable.ic_smarttag_icon

    override val defaultDeviceNameWithId: String
        get() = ATTrackingDetectionApplication.getAppContext().resources.getString(R.string.device_name_smarttag)
            .format(id)

    override val deviceContext: DeviceContext
        get() = SamsungDevice

    companion object : DeviceContext {
        internal val GATT_GENERIC_ACCESS_UUID = UUID.fromString("00001800-0000-1000-8000-00805f9b34fb")
        internal val GATT_DEVICE_NAME_UUID = UUID.fromString("00002a00-0000-1000-8000-00805f9b34fb")
        internal val GATT_APPEARANCE_UUID = UUID.fromString("00002a01-0000-1000-8000-00805f9b34fb")

        internal val GATT_DEVICE_INFORMATION_UUID = UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb")
        internal val GATT_MANUFACTURER_NAME_UUID = UUID.fromString("00002a29-0000-1000-8000-00805f9b34fb")

        override val bluetoothFilter: ScanFilter
            get() = ScanFilter.Builder()
                .setServiceData(
                    offlineFindingServiceUUID,
                    // First Byte:
                    // 13, FF --> After 8 Hours,
                    // 12, FE --> After 15 Minutes
                    // 10, F8 --> Connected
                    //
                    // Twelve Byte:
                    // 04, 00 --> UWB off,
                    // 04, 04 --> UWB on
                    byteArrayOf((0x10).toByte()),
                    byteArrayOf((0xF8).toByte())
                )
                .build()

        override val deviceType: DeviceType
            get() = DeviceType.SAMSUNG_DEVICE

        override val websiteManufacturer: String
            get() = "https://www.samsung.com/"

        override val defaultDeviceName: String
            get() = ATTrackingDetectionApplication.getAppContext().resources.getString(R.string.samsung_device_name)

        override val statusByteDeviceType: UInt
            get() = 0u

        val offlineFindingServiceUUID: ParcelUuid = ParcelUuid.fromString("0000FD5A-0000-1000-8000-00805F9B34FB")

        @SuppressLint("MissingPermission")
        suspend fun connectAndRetrieveCharacteristics(context: Context, deviceAddress: String): Triple<String?, Int?, String?> =
            suspendCancellableCoroutine { continuation ->
                val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
                val bluetoothAdapter = bluetoothManager.adapter
                val bluetoothDevice = bluetoothAdapter.getRemoteDevice(deviceAddress)

                val gattCallback = object : BluetoothGattCallback() {
                    var deviceName: String? = null
                    var appearance: Int? = null
                    var manufacturerName: String? = null

                    @SuppressLint("MissingPermission")
                    override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
                        if (newState == BluetoothProfile.STATE_CONNECTED) {
                            Timber.d("Connected to GATT server.")
                            gatt?.discoverServices()
                        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                            Timber.d("Disconnected from GATT server.")
                            if (continuation.isActive) {
                                continuation.resume(Triple(deviceName, appearance, manufacturerName))
                            }
                        }
                    }

                    @SuppressLint("MissingPermission")
                    override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
                        if (status == BluetoothGatt.GATT_SUCCESS) {
                            Timber.d("Services discovered.")
                            gatt?.let {
                                readNextCharacteristic(it)
                            }
                        } else {
                            Timber.w("onServicesDiscovered received: $status")
                            if (continuation.isActive) {
                                continuation.resumeWithException(Exception("Failed to discover services: $status"))
                            }
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    @SuppressLint("MissingPermission")
                    override fun onCharacteristicRead(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
                        if (status == BluetoothGatt.GATT_SUCCESS) {
                            when (characteristic?.uuid) {
                                GATT_DEVICE_NAME_UUID -> {
                                    if (characteristic != null) {
                                        deviceName = characteristic.getStringValue(0)
                                    }
                                }
                                GATT_APPEARANCE_UUID -> {
                                    if (characteristic != null) {
                                        appearance = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, 0)
                                    }
                                }
                                GATT_MANUFACTURER_NAME_UUID -> {
                                    if (characteristic != null) {
                                        manufacturerName = characteristic.getStringValue(0)
                                    }
                                }
                            }
                        } else {
                            Timber.w("Failed to read characteristic: $status")
                        }
                        gatt?.let { readNextCharacteristic(it) }
                    }

                    private fun readNextCharacteristic(gatt: BluetoothGatt) {
                        when {
                            deviceName == null -> {
                                val char = gatt.getService(GATT_GENERIC_ACCESS_UUID)?.getCharacteristic(GATT_DEVICE_NAME_UUID)
                                if (char != null) {
                                    gatt.readCharacteristic(char)
                                } else {
                                    deviceName = "Unknown"
                                    readNextCharacteristic(gatt)
                                }
                            }
                            appearance == null -> {
                                val char = gatt.getService(GATT_GENERIC_ACCESS_UUID)?.getCharacteristic(GATT_APPEARANCE_UUID)
                                if (char != null) {
                                    gatt.readCharacteristic(char)
                                } else {
                                    appearance = -1
                                    readNextCharacteristic(gatt)
                                }
                            }
                            manufacturerName == null -> {
                                val char = gatt.getService(GATT_DEVICE_INFORMATION_UUID)?.getCharacteristic(GATT_MANUFACTURER_NAME_UUID)
                                if (char != null) {
                                    gatt.readCharacteristic(char)
                                } else {
                                    manufacturerName = "Unknown"
                                    readNextCharacteristic(gatt)
                                }
                            }
                            else -> {
                                if (continuation.isActive) {
                                    continuation.resume(Triple(deviceName, appearance, manufacturerName))
                                }
                                gatt.disconnect()
                            }
                        }
                    }
                }

                bluetoothDevice.connectGatt(context, false, gattCallback)
            }
        suspend fun getSubType(wrappedScanResult: ScanResultWrapper): SamsungDeviceType {
            val (deviceName, appearance, manufacturerName) = connectAndRetrieveCharacteristics(
                ATTrackingDetectionApplication.getAppContext(),
                wrappedScanResult.deviceAddress
            )

            val advertisedName = wrappedScanResult.advertisedName
            val hasUWB = wrappedScanResult.uwbCapable

            return when {
                hasUWB == true && (deviceName == "Smart Tag2" || advertisedName == "Smart Tag2" || appearance == 576) -> SamsungDeviceType.SMART_TAG_2
                hasUWB == false && manufacturerName == "SOLUM" -> SamsungDeviceType.SOLUM
                hasUWB == true && (deviceName == "Smart Tag" || advertisedName == "Smart Tag") -> SamsungDeviceType.SMART_TAG_1_PLUS
                hasUWB == false && (deviceName == "Smart Tag" || advertisedName == "Smart Tag") -> SamsungDeviceType.SMART_TAG_1
                else -> SamsungDeviceType.UNKNOWN
            }
        }

        fun getUwbAvailability(scanResult: ScanResult): Boolean? {
            val serviceData = scanResult.scanRecord?.getServiceData(offlineFindingServiceUUID)

            if (serviceData != null && serviceData.size >= 12) {
                return getBitsFromByte(serviceData[12], 2)
            }

            return null
        }

        override fun getConnectionState(scanResult: ScanResult): ConnectionState {
            val serviceData = scanResult.scanRecord?.getServiceData(offlineFindingServiceUUID)

            if (serviceData != null && serviceData.isNotEmpty()) {
                // Little Endian (5,6,7) --> (2,1,0)
                val bit5 = getBitsFromByte(serviceData[0], 2)
                val bit6 = getBitsFromByte(serviceData[0],1)
                val bit7 = getBitsFromByte(serviceData[0],0)

                return if (!bit5 && bit6 && bit7) {
                    Timber.d("Samsung Device in Overmature Offline Mode")
                    ConnectionState.OVERMATURE_OFFLINE
                } else if (!bit5 && bit6 && !bit7) {
                    Timber.d("Samsung: Offline Mode")
                    ConnectionState.OFFLINE
                } else if (!bit5 && !bit6 && bit7) {
                    Timber.d("Samsung: Premature Offline Mode")
                    ConnectionState.PREMATURE_OFFLINE
                } else {
                    ConnectionState.CONNECTED
                }
            }

            return ConnectionState.UNKNOWN
        }

        override fun getBatteryState(scanResult: ScanResult): BatteryState {
            val serviceData = scanResult.scanRecord?.getServiceData(offlineFindingServiceUUID)

            if (serviceData != null && serviceData.size >= 12) {
                val bit6 = getBitsFromByte(serviceData[12],1)
                val bit7 = getBitsFromByte(serviceData[12],0)

                return if (bit6 && bit7) {
                    Timber.d("Samsung Device Battery State: FULL")
                    BatteryState.FULL
                } else if (bit6 && !bit7) {
                    Timber.d("Samsung Device Battery State: MEDIUM")
                    BatteryState.MEDIUM
                } else if (!bit6 && bit7) {
                    Timber.d("Samsung Device Battery State: LOW")
                    BatteryState.LOW
                } else {
                    Timber.d("Samsung Device Battery State: VERY_LOW")
                    BatteryState.VERY_LOW
                }
            }

            return BatteryState.UNKNOWN
        }

        override fun getPublicKey(scanResult: ScanResult): String {
            try {
                val serviceData = scanResult.scanRecord?.getServiceData(offlineFindingServiceUUID)

                fun ByteArray.toHexString() = joinToString("") { "%02x".format(it) }

                if (serviceData != null && serviceData.size >= 12) {
                    return byteArrayOf(serviceData[4], serviceData[5], serviceData[6], serviceData[7], serviceData[8], serviceData[9], serviceData[10], serviceData[11]).toHexString()
                }
            } catch (e: Exception) {
                Timber.e(e, "Error getting public key")
            }
            return scanResult.device.address
        }
    }
}