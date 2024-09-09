package de.seemoo.at_tracking_detection.database.models.device.types

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanFilter
import android.os.ParcelUuid
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.DrawableRes
import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.database.models.device.Connectable
import de.seemoo.at_tracking_detection.database.models.device.ConnectionState
import de.seemoo.at_tracking_detection.database.models.device.Device
import de.seemoo.at_tracking_detection.database.models.device.DeviceContext
import de.seemoo.at_tracking_detection.database.models.device.DeviceType
import de.seemoo.at_tracking_detection.ui.scan.ScanResultWrapper
import de.seemoo.at_tracking_detection.util.Utility
import de.seemoo.at_tracking_detection.util.ble.BluetoothConstants
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class GoogleFindMyNetwork(val id: Int) : Device(), Connectable {

    override val imageResource: Int
        @DrawableRes
        get() = R.drawable.ic_chipolo

    override val defaultDeviceNameWithId: String
        get() = ATTrackingDetectionApplication.getAppContext().resources.getString(R.string.device_name_find_my_device_google)
            .format(id)

    override val deviceContext: DeviceContext
        get() = GoogleFindMyNetwork

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
                val uuids = gatt.services.map { it.uuid }
                Timber.d("Found UUIDS $uuids")
                val service = gatt.services.firstOrNull {
                    it.uuid.toString().lowercase().contains(
                        GOOGLE_SOUND_SERVICE.lowercase()
                    )
                }

                if (service == null) {
                    Timber.e("Playing sound service not found!")
                    disconnect(gatt)
                    broadcastUpdate(BluetoothConstants.ACTION_EVENT_FAILED)
                    return
                }

                val characteristic = service.getCharacteristic(GOOGLE_SOUND_CHARACTERISTIC)

                characteristic.let {
                    gatt.setCharacteristicNotification(it, true)
                    if (Build.VERSION.SDK_INT >= 33) {
                        it.writeType
                        gatt.writeCharacteristic(it, GOOGLE_START_SOUND_OPCODE, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
                    } else {
                        // Deprecated since 33
                        @Suppress("DEPRECATION")
                        it.value = GOOGLE_START_SOUND_OPCODE
                        @Suppress("DEPRECATION")
                        gatt.writeCharacteristic(it)
                    }
                    Timber.d("Playing sound on Find My device with ${it.uuid}")
                    broadcastUpdate(BluetoothConstants.ACTION_EVENT_RUNNING)
                }
            }

            @SuppressLint("MissingPermission")
            fun stopSoundOnGoogleDevice(gatt: BluetoothGatt) {
                val service = gatt.services.firstOrNull {
                    it.uuid.toString().lowercase().contains(
                        GOOGLE_SOUND_SERVICE
                    )
                }

                if (service == null) {
                    Timber.d("Sound service not found")
                    return
                }

                val uuid = GOOGLE_SOUND_CHARACTERISTIC
                val characteristic = service.getCharacteristic(uuid)
                characteristic.let {
                    gatt.setCharacteristicNotification(it, true)
                    if (Build.VERSION.SDK_INT >= 33) {
                        it.writeType
                        gatt.writeCharacteristic(it, GOOGLE_STOP_SOUND_OPCODE, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
                    } else {
                        // Deprecated since 33
                        @Suppress("DEPRECATION")
                        it.value = GOOGLE_STOP_SOUND_OPCODE
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
                    if (characteristic?.value.contentEquals(GOOGLE_START_SOUND_OPCODE) && gatt != null) {
                        Handler(Looper.getMainLooper()).postDelayed({
                            stopSoundOnGoogleDevice(gatt)
                        }, 5000)
                    }

                    if (characteristic?.value.contentEquals(GOOGLE_STOP_SOUND_OPCODE)) {
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
        internal const val GOOGLE_SOUND_SERVICE = "12F4"
        internal val GOOGLE_SOUND_SERVICE_UUID = UUID.fromString("A3C812F4-0005-1000-8000-001A11000100")
        internal val SMARTPHONE_SERVICE = UUID.fromString("A3C87600-0005-1000-8000-001A11000100")
        internal val GOOGLE_SOUND_CHARACTERISTIC = UUID.fromString("8E0C0001-1D68-FB92-BF61-48377421680E")
        internal val GOOGLE_START_SOUND_OPCODE = byteArrayOf(0x00, 0x03)
        internal val GOOGLE_STOP_SOUND_OPCODE = byteArrayOf(0x01, 0x03)

        override val deviceType: DeviceType
            get() = DeviceType.GOOGLE_FIND_MY_NETWORK

        override val defaultDeviceName: String
            get() = ATTrackingDetectionApplication.getAppContext().resources.getString(R.string.google_find_my_default_name)

        override val websiteManufacturer: String
            get() = "https://www.google.com/android/find/"

        override val statusByteDeviceType: UInt
            get() = 0u

        override val bluetoothFilter: ScanFilter
            get() = ScanFilter.Builder()
                .setServiceData(
                    offlineFindingServiceUUID,
                    byteArrayOf((0x40).toByte()),
                    byteArrayOf((0x00).toByte()))
                .build()

        val offlineFindingServiceUUID: ParcelUuid = ParcelUuid.fromString("0000FEAA-0000-1000-8000-00805F9B34FB")

        fun getSubType(wrappedScanResult: ScanResultWrapper): GoogleFindMyNetworkType {
            return when (wrappedScanResult.advertisementFlags) {
                0x02 -> GoogleFindMyNetworkType.SMARTPHONE
                0x06 -> GoogleFindMyNetworkType.TAG
                else -> GoogleFindMyNetworkType.UNKNOWN
            }
        }

        @SuppressLint("MissingPermission")
        private suspend fun connectAndRetrieveCharacteristics(context: Context, deviceAddress: String, inputService: UUID, inputCharacteristic: UUID): String? =

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
                                inputCharacteristic -> {
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
                                val char = gatt.getService(inputService)?.getCharacteristic(
                                    inputCharacteristic
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

        suspend fun getDeviceName(wrappedScanResult: ScanResultWrapper): String {
            val deviceName =  connectAndRetrieveCharacteristics(
                ATTrackingDetectionApplication.getAppContext(),
                wrappedScanResult.deviceAddress,
                inputCharacteristic = GOOGLE_SOUND_CHARACTERISTIC,
                inputService = GOOGLE_SOUND_SERVICE_UUID,
            )

            if (!deviceName.isNullOrEmpty()) {
                val advName = wrappedScanResult.advertisedName
                if (advName != null && advName.startsWith("PB - ") && advName.length == 9) {
                    return deviceName + advName.takeLast(7)
                }
                return deviceName
            } else {
                return GoogleFindMyNetworkType.visibleStringFromSubtype(getSubType(wrappedScanResult))
            }
        }

        override fun getConnectionState(scanResult: ScanResult): ConnectionState {
            val serviceData = scanResult.scanRecord?.getServiceData(offlineFindingServiceUUID)

            if (serviceData != null) {
                // The last bit of the first byte indicates the offline mode
                // 0 --> Device was connected in the last 4 hours
                // 1 --> Last Connection with owner device was longer than 4 hours ago

                val statusBit = Utility.getBitsFromByte(serviceData[0], 0)

                return if (statusBit) {
                    Timber.d("Google Find My: Overmature Offline Mode")
                    ConnectionState.OVERMATURE_OFFLINE
                } else {
                    Timber.d("Google Find My: Premature Offline Mode")
                    ConnectionState.PREMATURE_OFFLINE
                }
            }

            return ConnectionState.UNKNOWN
        }
    }

}