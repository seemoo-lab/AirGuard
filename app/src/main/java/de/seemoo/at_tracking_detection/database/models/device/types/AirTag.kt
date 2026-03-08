package de.seemoo.at_tracking_detection.database.models.device.types

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.os.Build
import androidx.annotation.DrawableRes
import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.database.models.device.*
import de.seemoo.at_tracking_detection.util.ble.BluetoothEvent
import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*

class AirTag(val id: Int) : Device(), Connectable {

    override val imageResource: Int
        @DrawableRes
        get() = R.drawable.ic_airtag

    override val defaultDeviceNameWithId: String
        get() = ATTrackingDetectionApplication.getAppContext().resources.getString(R.string.device_name_airtag)
            .format(id)

    override val deviceContext: DeviceContext
        get() = AirTag

    override val bluetoothGattCallback: BluetoothGattCallback
        get() = object : BluetoothGattCallback() {
            @SuppressLint("MissingPermission")
            // Connect permission is checked before this function is called
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                when (status) {
                    BluetoothGatt.GATT_SUCCESS -> {
                        when (newState) {
                            BluetoothProfile.STATE_CONNECTED -> gatt.discoverServices()
                            BluetoothProfile.STATE_DISCONNECTED -> sendBluetoothEvent(
                                BluetoothEvent.Disconnected
                            )
                            else -> {
                                Timber.d("Connection state changed to $newState")
                            }
                        }
                    }
                    19 -> {
                        sendBluetoothEvent(BluetoothEvent.EventCompleted)
                    }
                    else -> sendBluetoothEvent(BluetoothEvent.EventFailed)
                }
            }

            @SuppressLint("MissingPermission")
            // Connect permission is checked before this function is called
            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                val service = gatt.getService(AIR_TAG_SOUND_SERVICE)
                if (service == null) {
                    Timber.e("AirTag sound service not found!")
                    disconnect(gatt)
                    sendBluetoothEvent(BluetoothEvent.EventFailed)
                } else {
                    service.getCharacteristic(AIR_TAG_SOUND_CHARACTERISTIC)
                        .let {
                            // Use modern API for SDK 33+, fallback to deprecated for older versions
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                val value = ByteBuffer.allocate(1).order(ByteOrder.LITTLE_ENDIAN).put(175.toByte()).array()
                                gatt.writeCharacteristic(it, value, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
                            } else {
                                @Suppress("DEPRECATION")
                                it.setValue(175, BluetoothGattCharacteristic.FORMAT_UINT8, 0)
                                @Suppress("DEPRECATION")
                                gatt.writeCharacteristic(it)
                            }
                            sendBluetoothEvent(BluetoothEvent.EventRunning)
                            Timber.d("Playing sound...")
                        }
                }
                super.onServicesDiscovered(gatt, status)
            }

            override fun onCharacteristicWrite(
                gatt: BluetoothGatt?,
                characteristic: BluetoothGattCharacteristic?,
                status: Int
            ) {
                when (status) {
                    BluetoothGatt.GATT_SUCCESS if characteristic != null -> {
                        when (characteristic.properties and AIR_TAG_EVENT_CALLBACK) {
                            AIR_TAG_EVENT_CALLBACK -> {
                                sendBluetoothEvent(
                                    BluetoothEvent.EventCompleted
                                )
                                disconnect(gatt)
                            }
                        }
                    }
                    133 -> {
                        // GATT_ERROR, Timeout
                        sendBluetoothEvent(BluetoothEvent.EventFailed)
                        disconnect(gatt)
                    }
                    else -> {
                        disconnect(gatt)
                    }
                }
                super.onCharacteristicWrite(gatt, characteristic, status)
            }

            override fun onCharacteristicRead(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
                status: Int
            ) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    when (characteristic.properties and AIR_TAG_EVENT_CALLBACK) {
                        AIR_TAG_EVENT_CALLBACK -> {
                            sendBluetoothEvent(
                                BluetoothEvent.EventCompleted
                            )
                            disconnect(gatt)
                        }
                    }
                }
            }
        }

    companion object : DeviceContext {
        private val AIR_TAG_SOUND_SERVICE = UUID.fromString("7DFC9000-7D1C-4951-86AA-8D9728F8D66C")
        private val AIR_TAG_SOUND_CHARACTERISTIC =
            UUID.fromString("7DFC9001-7D1C-4951-86AA-8D9728F8D66C")
        private const val AIR_TAG_EVENT_CALLBACK = 0x302

        override val bluetoothFilter: ScanFilter
            get() = ScanFilter.Builder()
                .setManufacturerData(
                    0x4C,
                    // Only Offline Devices:
                    // byteArrayOf((0x12).toByte(), (0x19).toByte(), (0x10).toByte()),
                    // byteArrayOf((0xFF).toByte(), (0xFF).toByte(), (0x18).toByte())
                    // All Devices:
                    byteArrayOf((0x12).toByte(), (0x19).toByte(), (0x10).toByte()),
                    byteArrayOf((0xFF).toByte(), (0x00).toByte(), (0x18).toByte())
                )
                .build()

        override val deviceType: DeviceType
            get() = DeviceType.AIRTAG

        override val defaultDeviceName: String
            get() = ATTrackingDetectionApplication.getAppContext().resources.getString(R.string.airtag_default_name)

        override val statusByteDeviceType: UInt
            get() = 1u

        override val websiteManufacturer: String
            get() = "https://www.apple.com/airtag/"

        override fun getBatteryState(scanResult: ScanResult): BatteryState {
            val mfg: ByteArray? = scanResult.scanRecord?.getManufacturerSpecificData(0x4C)

            if (mfg != null && mfg.size >= 3) {
                val status = mfg[2] // Extract the status byte

                // Bits 6-7: Battery level
                val batteryLevel = (status.toInt() shr 6) and 0x03

                // Full: 0, Medium 1, Low 2, Very Low 3
                when (batteryLevel) {
                    0x00 -> return BatteryState.FULL
                    0x01 -> return BatteryState.MEDIUM
                    0x02 -> return BatteryState.LOW
                    0x03 -> return BatteryState.VERY_LOW
                }
            }

            return BatteryState.UNKNOWN
        }
    }
}