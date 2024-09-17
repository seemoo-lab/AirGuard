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
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
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
import java.net.URL
import java.util.UUID
import kotlin.coroutines.resume

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
        private val GOOGLE_SOUND_SERVICE_UUID = UUID.fromString("A3C812F4-0005-1000-8000-001A11000100")
        internal val GOOGLE_SOUND_CHARACTERISTIC = UUID.fromString("8E0C0001-1D68-FB92-BF61-48377421680E")
        internal val GOOGLE_START_SOUND_OPCODE = byteArrayOf(0x00, 0x03)
        internal val GOOGLE_STOP_SOUND_OPCODE = byteArrayOf(0x01, 0x03)
        private val GOOGLE_RETRIEVE_NAME_OPCODE = byteArrayOf(0x05, 0x00)
        private val GOOGLE_GET_OWNER_INFORMATION_OPCODE = byteArrayOf(0x04, 0x04)

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

        suspend fun getDeviceName(wrappedScanResult: ScanResultWrapper): String {
            val retrievedData: ByteArray? = writeRequestAndReadResponse(wrappedScanResult.deviceAddress, GOOGLE_RETRIEVE_NAME_OPCODE)

            if (retrievedData != null && retrievedData.isNotEmpty()) {
                val nameString = retrievedData.toString(Charsets.UTF_8)
                return nameString
            } else {
                return GoogleFindMyNetworkType.visibleStringFromSubtype(getSubType(wrappedScanResult))
            }
        }

        suspend fun getOwnerInformationURL(wrappedScanResult: ScanResultWrapper): URL? {
            // Write the request opcode to the characteristic first
            val retrievedData: ByteArray? = writeRequestAndReadResponse(wrappedScanResult.deviceAddress, GOOGLE_GET_OWNER_INFORMATION_OPCODE)

            if (retrievedData != null) {
                val ownerHex = retrievedData.joinToString("") { "%02x".format(it) }

                // Extract and process the owner information from the characteristic
                val ownerHexShortend = ownerHex.drop(4) // Assuming first 4 bytes are not needed

                // Build the URL with the extracted information
                val url = URL("https://spot-pa.googleapis.com/lookup?e=$ownerHexShortend")

                // Validate the URL by checking for a 404 error
                return if (Utility.isValidURL(url)) {
                    Timber.d("Valid owner information URL: $url")
                    url
                } else {
                    Timber.e("Owner information URL is invalid (404): $url")
                    null
                }
            }

            Timber.e("Failed to retrieve owner information characteristic")
            return null
        }

        @SuppressLint("MissingPermission")
        suspend fun writeRequestAndReadResponse(deviceAddress: String, command: ByteArray): ByteArray? = suspendCancellableCoroutine { continuation ->
            val context = ATTrackingDetectionApplication.getAppContext()
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val bluetoothAdapter = bluetoothManager.adapter
            val bluetoothDevice = bluetoothAdapter.getRemoteDevice(deviceAddress)

            val gattCallback = object : BluetoothGattCallback() {
                @SuppressLint("MissingPermission")
                override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        Timber.d("Connected to GATT server for writing.")
                        gatt?.discoverServices()
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        Timber.d("Disconnected from GATT server.")
                        continuation.resume(null)
                    }
                }

                @SuppressLint("MissingPermission")
                override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        gatt?.let { gattInstance ->
                            val service = gattInstance.getService(GOOGLE_SOUND_SERVICE_UUID)
                            val characteristic = service?.getCharacteristic(GOOGLE_SOUND_CHARACTERISTIC)

                            if (characteristic != null) {
                                // Write command
                                characteristic.value = command
                                gattInstance.writeCharacteristic(characteristic)
                            } else {
                                Timber.e("Characteristic not found")
                                continuation.resume(null)
                                gattInstance.disconnect()
                            }
                        }
                    } else {
                        Timber.e("Failed to discover services: $status")
                        continuation.resume(null)
                    }
                }

                override fun onCharacteristicWrite(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        Timber.d("Successfully wrote characteristic. Now reading response.")
                        // After successful write, initiate a read
                        gatt?.readCharacteristic(characteristic)
                    } else {
                        Timber.e("Failed to write characteristic: $status")
                        continuation.resume(null)
                        gatt?.disconnect()
                    }
                }

                override fun onCharacteristicRead(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        Timber.d("Successfully read characteristic")
                        // Return the value read from the characteristic
                        continuation.resume(characteristic?.value)
                    } else {
                        Timber.e("Failed to read characteristic: $status")
                        continuation.resume(null)
                    }
                    gatt?.disconnect()
                }
            }

            bluetoothDevice.connectGatt(context, false, gattCallback)
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