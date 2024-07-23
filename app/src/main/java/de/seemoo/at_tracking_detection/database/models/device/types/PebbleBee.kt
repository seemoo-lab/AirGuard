package de.seemoo.at_tracking_detection.database.models.device.types

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanFilter
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import androidx.annotation.DrawableRes
import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.database.models.device.Connectable
import de.seemoo.at_tracking_detection.database.models.device.Device
import de.seemoo.at_tracking_detection.database.models.device.DeviceContext
import de.seemoo.at_tracking_detection.database.models.device.DeviceType
import de.seemoo.at_tracking_detection.ui.scan.ScanResultWrapper
import de.seemoo.at_tracking_detection.util.ble.BluetoothConstants
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class PebbleBee (val id: Int) : Device(), Connectable {
    override val imageResource: Int
        @DrawableRes
        get() = R.drawable.ic_baseline_device_unknown_24

    override val defaultDeviceNameWithId: String
        get() = ATTrackingDetectionApplication.getAppContext().resources.getString(R.string.device_name_pebblebee)
            .format(id)

    override val deviceContext: DeviceContext
        get() = PebbleBee

    override val bluetoothGattCallback: BluetoothGattCallback
        get() = object : BluetoothGattCallback() {
            @SuppressLint("MissingPermission")
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                when (status) {
                    BluetoothGatt.GATT_SUCCESS -> {
                        when (newState) {
                            BluetoothProfile.STATE_CONNECTED -> {
                                Timber.d("Connected to gatt device!")
                                gatt.discoverServices()
                            }
                            BluetoothProfile.STATE_DISCONNECTED -> {
                                broadcastUpdate(BluetoothConstants.ACTION_GATT_DISCONNECTED)
                                Timber.d("Disconnected from gatt device!")
                            }
                            else -> {
                                Timber.d("Connection state changed to $newState")
                            }
                        }
                    }
                    else -> {
                        Timber.e("Failed to connect to bluetooth device! Status: $status")
                        broadcastUpdate(BluetoothConstants.ACTION_EVENT_FAILED)
                    }
                }
            }

            @SuppressLint("MissingPermission")
            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    Timber.e("Service discovery failed with status $status")
                    return
                }

                val uuids = gatt.services.map { it.uuid }
                Timber.d("Found UUIDS $uuids")

                val service = gatt.services.firstOrNull {
                    it.uuid.toString().lowercase().contains(PEBBLEBEE_SOUND_SERVICE.lowercase())
                }

                if (service == null) {
                    Timber.e("Playing sound service not found!")
                    disconnect(gatt)
                    broadcastUpdate(BluetoothConstants.ACTION_EVENT_FAILED)
                    return
                }

                val characteristics = service.characteristics.map { it.uuid }
                Timber.d("Found characteristics $characteristics")

                val characteristic = service.getCharacteristic(PEBBLEBEE_SOUND_CHARACTERISTIC)
                if (characteristic == null) {
                    Timber.e("Characteristic not found!")
                    disconnect(gatt)
                    broadcastUpdate(BluetoothConstants.ACTION_EVENT_FAILED)
                    return
                }

                gatt.setCharacteristicNotification(characteristic, true)
                if (Build.VERSION.SDK_INT >= 33) {
                    characteristic.writeType
                    gatt.writeCharacteristic(characteristic, PEBBLEBEE_START_SOUND_OPCODE, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
                } else {
                    @Suppress("DEPRECATION")
                    characteristic.value = PEBBLEBEE_START_SOUND_OPCODE
                    @Suppress("DEPRECATION")
                    gatt.writeCharacteristic(characteristic)
                }

                Timber.d("Playing sound on Find My device with ${characteristic.uuid}")
                broadcastUpdate(BluetoothConstants.ACTION_EVENT_RUNNING)
            }

            @SuppressLint("MissingPermission")
            fun stopSoundOnPebbleBeeDevice(gatt: BluetoothGatt) {
                val service = gatt.services.firstOrNull {
                    it.uuid.toString().lowercase().contains(
                        PEBBLEBEE_SOUND_SERVICE
                    )
                }

                if (service == null) {
                    Timber.d("Sound service not found")
                    return
                }

                val uuid = PEBBLEBEE_SOUND_CHARACTERISTIC
                val characteristic = service.getCharacteristic(uuid)
                characteristic.let {
                    gatt.setCharacteristicNotification(it, true)
                    if (Build.VERSION.SDK_INT >= 33) {
                        it.writeType
                        gatt.writeCharacteristic(it, PEBBLEBEE_STOP_SOUND_OPCODE, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
                    } else {
                        // Deprecated since 33
                        @Suppress("DEPRECATION")
                        it.value = PEBBLEBEE_STOP_SOUND_OPCODE
                        @Suppress("DEPRECATION")
                        gatt.writeCharacteristic(it)
                    }
                    Timber.d("Stopping sound on Find My device with ${it.uuid}")
                }
            }

            @SuppressLint("MissingPermission")
            override fun onCharacteristicWrite(
                gatt: BluetoothGatt?,
                characteristic: BluetoothGattCharacteristic?,
                status: Int
            ) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Timber.d("Finished writing to characteristic")
                    if (characteristic?.value.contentEquals(PEBBLEBEE_START_SOUND_OPCODE) && gatt != null) {
                        Handler(Looper.getMainLooper()).postDelayed({
                            stopSoundOnPebbleBeeDevice(gatt)
                        }, 5000)
                    }

                    if (characteristic?.value.contentEquals(PEBBLEBEE_STOP_SOUND_OPCODE)) {
                        disconnect(gatt)
                        broadcastUpdate(BluetoothConstants.ACTION_EVENT_COMPLETED)
                    }

                } else {
                    Timber.d("Writing to characteristic failed ${characteristic?.uuid}")
                    disconnect(gatt)
                    broadcastUpdate(BluetoothConstants.ACTION_EVENT_FAILED)
                }
                super.onCharacteristicWrite(gatt, characteristic, status)
            }
        }

    companion object : DeviceContext {
        internal const val PEBBLEBEE_SOUND_SERVICE = "FA25"
        internal val PEBBLEBEE_SOUND_CHARACTERISTIC = UUID.fromString("00002C02-0000-1000-8000-00805f9b34fb")
        internal val PEBBLEBEE_START_SOUND_OPCODE = byteArrayOf(0x01)
        internal val PEBBLEBEE_STOP_SOUND_OPCODE = byteArrayOf(0x02)

        internal val GATT_DEVICE_INFORMATION_SERVICE = UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb")
        internal val GATT_DEVICE_TYPE_CHARACTERISTIC = UUID.fromString("00002a24-0000-1000-8000-00805f9b34fb")

        override val bluetoothFilter: ScanFilter
            get() = ScanFilter.Builder()
                .setServiceUuid(offlineFindingServiceUUID)
                .build()

        override val deviceType: DeviceType
            get() = DeviceType.PEBBLEBEE

        override val defaultDeviceName: String
            get() = ATTrackingDetectionApplication.getAppContext().resources.getString(R.string.pebblebee_default_name)

        override val websiteManufacturer: String
            get() = "https://pebblebee.com/"

        override val statusByteDeviceType: UInt
            get() = 0u

        val offlineFindingServiceUUID: ParcelUuid = ParcelUuid.fromString("0000FA25-0000-1000-8000-00805F9B34FB")

        @SuppressLint("MissingPermission")
        private suspend fun connectAndRetrieveCharacteristics(context: Context, deviceAddress: String): String? =
            suspendCancellableCoroutine { continuation ->
                val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
                val bluetoothAdapter = bluetoothManager.adapter
                val bluetoothDevice = bluetoothAdapter.getRemoteDevice(deviceAddress)

                val gattCallback = object : BluetoothGattCallback() {
                    var deviceName: String? = null

                    @SuppressLint("MissingPermission")
                    override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
                        if (newState == BluetoothProfile.STATE_CONNECTED) {
                            Timber.d("Connected to GATT server.")
                            gatt?.discoverServices()
                        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                            Timber.d("Disconnected from GATT server.")
                            if (continuation.isActive) {
                                continuation.resume(deviceName)
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
                                GATT_DEVICE_TYPE_CHARACTERISTIC -> {
                                    if (characteristic != null) {
                                        deviceName = characteristic.getStringValue(0)
                                    }
                                }
                            }
                        } else {
                            Timber.w("Failed to read characteristic: $status")
                        }
                        gatt?.let { readNextCharacteristic(it) }
                    }

                    @SuppressLint("MissingPermission")
                    private fun readNextCharacteristic(gatt: BluetoothGatt) {
                        when {
                            deviceName == null -> {
                                val char = gatt.getService(GATT_DEVICE_INFORMATION_SERVICE)?.getCharacteristic(
                                    GATT_DEVICE_TYPE_CHARACTERISTIC
                                )
                                if (char != null) {
                                    gatt.readCharacteristic(char)
                                } else {
                                    deviceName = "Unknown"
                                    readNextCharacteristic(gatt)
                                }
                            }
                            else -> {
                                if (continuation.isActive) {
                                    continuation.resume(deviceName)
                                }
                                gatt.disconnect()
                            }
                        }
                    }
                }

                bluetoothDevice.connectGatt(context, false, gattCallback)
            }

        suspend fun getSubTypeName(wrappedScanResult: ScanResultWrapper): String {
            val deviceName = connectAndRetrieveCharacteristics(
                ATTrackingDetectionApplication.getAppContext(),
                wrappedScanResult.deviceAddress
            )

            if (!deviceName.isNullOrEmpty()) {
                val advName = wrappedScanResult.advertisedName
                if (advName != null && advName.startsWith("PB - ") && advName.length == 9) {
                    return deviceName + advName.takeLast(7)
                }
                return deviceName
            } else {
                return ATTrackingDetectionApplication.getAppContext().resources.getString(R.string.pebblebee_default_name)
            }

        }
    }
}