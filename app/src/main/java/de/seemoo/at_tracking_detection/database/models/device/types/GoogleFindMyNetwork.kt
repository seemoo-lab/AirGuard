package de.seemoo.at_tracking_detection.database.models.device.types

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
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
import de.seemoo.at_tracking_detection.database.models.device.types.GoogleFindMyNetworkType.SMARTPHONE
import de.seemoo.at_tracking_detection.database.models.device.types.GoogleFindMyNetworkType.TAG
import de.seemoo.at_tracking_detection.database.models.device.types.GoogleFindMyNetworkType.UNKNOWN
import de.seemoo.at_tracking_detection.ui.scan.ScanFragment
import de.seemoo.at_tracking_detection.ui.scan.ScanResultWrapper
import de.seemoo.at_tracking_detection.util.Utility
import de.seemoo.at_tracking_detection.util.ble.BluetoothConstants
import timber.log.Timber
import java.net.URL
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

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

                val characteristic = service.getCharacteristic(GOOGLE_SOUND_CHARACTERISTIC_UUID)

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

                val uuid = GOOGLE_SOUND_CHARACTERISTIC_UUID
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
        private val GOOGLE_SOUND_SERVICE_UUID: UUID = UUID.fromString("15190001-12F4-C226-88ED-2AC5579F2A85")
        internal val GOOGLE_SOUND_CHARACTERISTIC_UUID: UUID = UUID.fromString("8E0C0001-1D68-FB92-BF61-48377421680E")
        internal val GOOGLE_DESCRIPTOR_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
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
                0x02 -> SMARTPHONE
                0x06 -> TAG
                else -> UNKNOWN
            }
        }

        suspend fun getDeviceName(wrappedScanResult: ScanResultWrapper): String {
            val errorCaseName = GoogleFindMyNetworkType.visibleStringFromSubtype(getSubType(wrappedScanResult))
            val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
            val context: Context = ATTrackingDetectionApplication.getAppContext()

            if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
                Timber.e("Bluetooth adapter is null or not enabled")
                return errorCaseName
            } else if (!BluetoothAdapter.checkBluetoothAddress(wrappedScanResult.deviceAddress)) {
                Timber.e("Invalid Bluetooth address")
                return errorCaseName
            }

            val device = bluetoothAdapter.getRemoteDevice(wrappedScanResult.deviceAddress)
            val dataToSend = GOOGLE_RETRIEVE_NAME_OPCODE

            return try {
                // Await the result of the GATT operation
                val receivedValue = connectToDeviceAndWriteToIndication(context, device, dataToSend)

                // If receivedValue is not empty, return the device name, else fallback to error case
                receivedValue?.let {
                    val nameBytes = it.drop(2).toByteArray() // Drop the first two bytes
                    val decodedBytes = String(nameBytes, Charsets.UTF_8)
                    ScanFragment.deviceNameMap[wrappedScanResult.uniqueIdentifier] = decodedBytes
                    nameReplacementLayer(decodedBytes)
                } ?: errorCaseName
            } catch (e: Exception) {
                Timber.e("Error during connectAndWrite: ${e.message}")
                errorCaseName
            }
        }

        suspend fun getOwnerInformationURL(wrappedScanResult: ScanResultWrapper): URL? {
            val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
            val context: Context = ATTrackingDetectionApplication.getAppContext()

            if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
                Timber.e("Bluetooth adapter is null or not enabled")
                return null
            } else if (!BluetoothAdapter.checkBluetoothAddress(wrappedScanResult.deviceAddress)) {
                Timber.e("Invalid Bluetooth address")
                return null
            }

            val device = bluetoothAdapter.getRemoteDevice(wrappedScanResult.deviceAddress)
            val dataToSend = GOOGLE_GET_OWNER_INFORMATION_OPCODE

            try {
                // Await the result of the GATT operation
                val retrievedData = connectToDeviceAndWriteToIndication(context, device, dataToSend)

                if (retrievedData == null) {
                    Timber.e("Failed to retrieve owner information characteristic")
                    return null
                }

                val ownerHex = retrievedData.joinToString("") { "%02x".format(it) }
                Timber.d("Owner information hex: $ownerHex")
                val ownerHexShortened = ownerHex.drop(4) // Assuming first 4 bytes are not needed
                Timber.d("Owner information hex shortened: $ownerHexShortened")

                // Build the URL with the extracted information
                val url = URL("https://spot-pa.googleapis.com/lookup?e=$ownerHexShortened")

                Timber.d("Owner information URL: $url")
                // Validate the URL by checking for a 404 error
                return if (Utility.isValidURL(url)) {
                    Timber.d("Valid owner information URL: $url")
                    url
                } else {
                    Timber.e("Owner information URL is invalid (404): $url")
                    null
                }

            } catch (e: Exception) {
                Timber.e("Error during connectAndWrite: ${e.message}")
                return null
            }
        }

        @SuppressLint("MissingPermission")
        suspend fun connectToDeviceAndWriteToIndication(
            context: Context,
            device: BluetoothDevice,
            dataToSend: ByteArray
        ): ByteArray? = suspendCoroutine { continuation ->
            device.connectGatt(context, false, object : BluetoothGattCallback() {
                override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        // Discover services after successful connection
                        gatt?.discoverServices()
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        gatt?.close()
                        continuation.resume(null)
                    }
                }

                override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        val service = gatt?.getService(GOOGLE_SOUND_SERVICE_UUID)
                        val characteristic = service?.getCharacteristic(GOOGLE_SOUND_CHARACTERISTIC_UUID)
                        if (characteristic == null) {
                            continuation.resume(null)
                            return
                        }

                        val indicationsSuccessfullySet = gatt.setCharacteristicNotification(characteristic, true)
                        Timber.d("Indications set: $indicationsSuccessfullySet")

                        val descriptor = characteristic.getDescriptor(GOOGLE_DESCRIPTOR_UUID)

                        // Write data to the descriptor
                        if (Build.VERSION.SDK_INT >= 33) {
                            gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)
                        } else {
                            @Suppress("DEPRECATION")
                            descriptor.value = BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                            @Suppress("DEPRECATION")
                            gatt.writeDescriptor(descriptor)
                        }
                    }
                }

                override fun onCharacteristicWrite(
                    gatt: BluetoothGatt?,
                    characteristic: BluetoothGattCharacteristic?,
                    status: Int
                ) {
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        continuation.resume(null)
                    }
                }

                override fun onDescriptorWrite(gatt: BluetoothGatt?, descriptor: BluetoothGattDescriptor?, status: Int) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        val service = gatt?.getService(GOOGLE_SOUND_SERVICE_UUID)
                        val characteristicToWrite = service?.getCharacteristic(GOOGLE_SOUND_CHARACTERISTIC_UUID)
                        if (characteristicToWrite == null) {
                            continuation.resume(null)
                            return
                        }

                        if (Build.VERSION.SDK_INT >= 33) {
                            gatt.writeCharacteristic(characteristicToWrite, dataToSend, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
                        } else {
                            @Suppress("DEPRECATION")
                            characteristicToWrite.value = dataToSend
                            characteristicToWrite.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                            @Suppress("DEPRECATION")
                            (gatt.writeCharacteristic(characteristicToWrite))
                        }
                    } else {
                        continuation.resume(null)
                    }
                }

                override fun onCharacteristicChanged(
                    gatt: BluetoothGatt,
                    characteristic: BluetoothGattCharacteristic,
                    value: ByteArray
                ) {
                    // Resume the coroutine with the received value
                    continuation.resume(value)
                }
            })
        }

        override fun getConnectionState(scanResult: ScanResult): ConnectionState {
            val serviceData = scanResult.scanRecord?.getServiceData(offlineFindingServiceUUID)

            if (serviceData != null) {
                // The last bit of the first byte indicates the offline mode
                // 0 --> Device was connected in the last 4 hours
                // 1 --> Last Connection with owner device was longer than 4 hours ago

                val statusBit = Utility.getBitsFromByte(serviceData[0], 0)

                return if (statusBit) {
                    ConnectionState.OVERMATURE_OFFLINE
                } else {
                    ConnectionState.PREMATURE_OFFLINE
                }
            }

            return ConnectionState.UNKNOWN
        }

        fun nameReplacementLayer(name: String): String {
            return when {
                name.contains("motorola", ignoreCase = true) -> "Moto Tag"
                name.contains("TBD-Gray", ignoreCase = false) -> "Moto Tag"
                else -> name
            }
        }

        fun getGoogleManufacturerFromNameString(name: String): GoogleFindMyNetworkManufacturer {
            Timber.d("Name: $name")
            return when {
                name.contains("pebblebee", ignoreCase = true) -> GoogleFindMyNetworkManufacturer.PEBBLEBEE
                name.contains("chipolo", ignoreCase = true) -> GoogleFindMyNetworkManufacturer.CHIPOLO
                name.contains("motorola", ignoreCase = true) -> GoogleFindMyNetworkManufacturer.MOTOROLA
                name.contains("moto tag", ignoreCase = true) -> GoogleFindMyNetworkManufacturer.MOTOROLA
                name.contains("TBD-Gray", ignoreCase = false) -> GoogleFindMyNetworkManufacturer.MOTOROLA
                name.contains("eufy", ignoreCase = true) -> GoogleFindMyNetworkManufacturer.EUFY
                name.contains("jio", ignoreCase = true) -> GoogleFindMyNetworkManufacturer.JIO
                name.contains("rolling square", ignoreCase = true) -> GoogleFindMyNetworkManufacturer.ROLLING_SQUARE
                name.contains("hama", ignoreCase = true) -> GoogleFindMyNetworkManufacturer.HAMA
                else -> GoogleFindMyNetworkManufacturer.UNKNOWN
            }
        }

        fun getGoogleDrawableFromNameString(name: String): Int {
            return when {
                name.contains("pebblebee clip", ignoreCase = true) -> R.drawable.ic_pebblebee_clip
                name.contains("chipolo one", ignoreCase = true) -> R.drawable.ic_chipolo
                name.contains("motorola", ignoreCase = true) -> R.drawable.ic_moto_tag
                name.contains("moto tag", ignoreCase = true) -> R.drawable.ic_moto_tag
                name.contains("TBD-Gray", ignoreCase = false) -> R.drawable.ic_moto_tag
                else -> R.drawable.ic_chipolo
            }
        }

        fun getGoogleInformationRetrievalText(manufacturer: GoogleFindMyNetworkManufacturer): String {
            Timber.d("Manufacturer: $manufacturer")
            return when (manufacturer) {
                GoogleFindMyNetworkManufacturer.PEBBLEBEE -> ATTrackingDetectionApplication.getAppContext().resources.getString(R.string.retrieve_owner_information_explanation_pebblebee)
                GoogleFindMyNetworkManufacturer.CHIPOLO -> ATTrackingDetectionApplication.getAppContext().resources.getString(R.string.retrieve_owner_information_explanation_chipolo)
                GoogleFindMyNetworkManufacturer.MOTOROLA -> ATTrackingDetectionApplication.getAppContext().resources.getString(R.string.retrieve_owner_information_explanation_motorola)
                GoogleFindMyNetworkManufacturer.HAMA -> ATTrackingDetectionApplication.getAppContext().resources.getString(R.string.retrieve_owner_information_explanation_hama)
                else -> ATTrackingDetectionApplication.getAppContext().resources.getString(R.string.retrieve_owner_information_explanation_unknown)
            }
        }

        fun getAlternativeIdentifier(scanResult: ScanResult): String? {
            // TODO: check if this is correct
            try {
                val serviceData = scanResult.scanRecord?.getServiceData(offlineFindingServiceUUID)

                fun ByteArray.toHexString() = joinToString("") { "%02x".format(it) }

                return serviceData?.let { serviceData ->
                    val startIndex = 1 // Skip first Byte
                    val endIndex = when {
                        serviceData.size >= 34 -> startIndex + 32
                        serviceData.size >= 22 -> startIndex + 20
                        else -> return null
                    }

                    serviceData.slice(startIndex until endIndex).toByteArray().toHexString()
                }
            } catch (e: Exception) {
                Timber.e(e, "Error getting unique identifier of Google Tracker")
            }

            return null
        }
    }

}