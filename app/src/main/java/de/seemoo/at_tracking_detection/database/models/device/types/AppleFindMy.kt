package de.seemoo.at_tracking_detection.database.models.device.types

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.DrawableRes
import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.database.models.device.BatteryState
import de.seemoo.at_tracking_detection.database.models.device.Connectable
import de.seemoo.at_tracking_detection.database.models.device.ConnectionState
import de.seemoo.at_tracking_detection.database.models.device.Device
import de.seemoo.at_tracking_detection.database.models.device.DeviceContext
import de.seemoo.at_tracking_detection.database.models.device.DeviceType
import de.seemoo.at_tracking_detection.ui.scan.ScanFragment
import de.seemoo.at_tracking_detection.ui.scan.ScanResultWrapper
import de.seemoo.at_tracking_detection.util.Utility
import de.seemoo.at_tracking_detection.util.ble.BluetoothEvent
import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

open class AppleFindMy(val id: Int) : Device(), Connectable {

    // Three different types of Sound Playing Protocol possible, used in this priority
    // DULT: modern approach using the DULT standard
    // FindMy: Apple’s normal Find My protocol (fd44 service) for most devices (Third Party Trackers, Airpods, etc.)
    // AirTag: the original AirTag protocol, used by first gen AirTags
    private enum class SoundProtocol { DULT, FINDMY, AIRTAG }
    private var selectedSoundProtocol: SoundProtocol? = null

    override val imageResource: Int
        @DrawableRes
        get() = R.drawable.ic_chipolo

    override val defaultDeviceNameWithId: String
        get() = ATTrackingDetectionApplication.getAppContext().resources.getString(R.string.device_name_find_my_device_apple)
            .format(id)

    override val deviceContext: DeviceContext
        get() = AppleFindMy

    @SuppressLint("MissingPermission")
    private fun writeCharacteristic(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
        enableNotifications: Boolean = true
    ) {
        if (enableNotifications) {
            val notifOk = gatt.setCharacteristicNotification(characteristic, true)
            Timber.d("setCharacteristicNotification for ${characteristic.uuid}: $notifOk")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val result = gatt.writeCharacteristic(
                characteristic, value, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            )
            Timber.d("writeCharacteristic (API33+) for ${characteristic.uuid} → statusCode=$result, value=${value.toHexString()}")
        } else {
            @Suppress("DEPRECATION")
            characteristic.value = value
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            @Suppress("DEPRECATION")
            val queued = gatt.writeCharacteristic(characteristic)
            Timber.d("writeCharacteristic (legacy) for ${characteristic.uuid} → queued=$queued, value=${value.toHexString()}")
        }
    }

    /** Send the appropriate stop-sound command based on the negotiated protocol. */
    @SuppressLint("MissingPermission")
    private fun stopSound(gatt: BluetoothGatt) {
        Timber.d("stopSound called, protocol=$selectedSoundProtocol")
        val (serviceKey, charUUID, stopOpcode) = when (selectedSoundProtocol) {
            SoundProtocol.DULT ->
                Triple(DULT_SOUND_SERVICE_UUID.toString(), DULT_SOUND_CHARACTERISTIC, DULT_STOP_SOUND_OPCODE)
            SoundProtocol.FINDMY ->
                Triple(FINDMY_SOUND_SERVICE, FINDMY_SOUND_CHARACTERISTIC, FINDMY_STOP_SOUND_OPCODE)
            else -> {
                Timber.d("stopSound: AirTag protocol — no explicit stop command, will rely on disconnect")
                return
            }
        }

        val service = gatt.services.firstOrNull {
            it.uuid.toString().lowercase().contains(serviceKey.lowercase())
        } ?: run {
            Timber.w("stopSound: sound service not found (key=$serviceKey)")
            return
        }

        val characteristic = service.getCharacteristic(charUUID) ?: run {
            Timber.w("stopSound: sound characteristic not found ($charUUID)")
            return
        }

        Timber.d("stopSound: writing stop opcode ${stopOpcode.toHexString()} to ${characteristic.uuid}")
        writeCharacteristic(gatt, characteristic, stopOpcode, enableNotifications = true)
    }

    override val bluetoothGattCallback: BluetoothGattCallback
        get() = object : BluetoothGattCallback() {

            @SuppressLint("MissingPermission")
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                val newStateStr = when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> "CONNECTED"
                    BluetoothProfile.STATE_DISCONNECTED -> "DISCONNECTED"
                    else -> "state=$newState"
                }
                Timber.d("onConnectionStateChange: status=$status, newState=$newStateStr, protocol=$selectedSoundProtocol")

                when (status) {
                    BluetoothGatt.GATT_SUCCESS -> when (newState) {
                        BluetoothProfile.STATE_CONNECTED -> {
                            Timber.d("GATT connected — discovering services")
                            gatt.discoverServices()
                        }
                        BluetoothProfile.STATE_DISCONNECTED -> {
                            Timber.d("GATT disconnected cleanly")
                            sendBluetoothEvent(BluetoothEvent.Disconnected)
                        }
                        else -> Timber.d("Unhandled connection state: $newState")
                    }
                    19 -> {
                        // 0x13 = remote device closed connection
                        // For AirTag this is the normal "sound done" signal
                        Timber.d("Remote device terminated connection (status 19) — treating as completion")
                        sendBluetoothEvent(BluetoothEvent.EventCompleted)
                    }
                    else -> {
                        Timber.e("GATT connection failed with status=$status")
                        sendBluetoothEvent(BluetoothEvent.EventFailed)
                    }
                }
            }

            @SuppressLint("MissingPermission")
            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    Timber.e("onServicesDiscovered failed with status=$status")
                    disconnect(gatt)
                    sendBluetoothEvent(BluetoothEvent.EventFailed)
                    return
                }

                val uuids = gatt.services.map { it.uuid.toString() }
                Timber.d("onServicesDiscovered: ${uuids.size} services found: $uuids")

                // Priority 1: DULT
                val dultService = gatt.getService(DULT_SOUND_SERVICE_UUID)
                Timber.d("DULT service (${DULT_SOUND_SERVICE_UUID}) found=${dultService != null}")
                if (dultService != null) {
                    val characteristic = dultService.getCharacteristic(DULT_SOUND_CHARACTERISTIC)
                    Timber.d("DULT characteristic (${DULT_SOUND_CHARACTERISTIC}) found=${characteristic != null}")
                    if (characteristic != null) {
                        selectedSoundProtocol = SoundProtocol.DULT
                        Timber.i("▶ Sound protocol selected: DULT (Sound_Start 0x0300 LE)")
                        writeCharacteristic(gatt, characteristic, DULT_START_SOUND_OPCODE, enableNotifications = true)
                        sendBluetoothEvent(BluetoothEvent.EventRunning)
                        return
                    }
                }

                // Priority 2: Apple Find My sound service (fd44)
                val findMyService = gatt.services.firstOrNull {
                    it.uuid.toString().lowercase().contains(FINDMY_SOUND_SERVICE.lowercase())
                }
                Timber.d("FindMy service (contains '%s') found=%b%s",
                    FINDMY_SOUND_SERVICE, findMyService != null,
                    if (findMyService != null) ", uuid=${findMyService.uuid}" else ""
                )
                if (findMyService != null) {
                    val characteristic = findMyService.getCharacteristic(FINDMY_SOUND_CHARACTERISTIC)
                    Timber.d("FindMy characteristic (${FINDMY_SOUND_CHARACTERISTIC}) found=${characteristic != null}")
                    if (characteristic != null) {
                        selectedSoundProtocol = SoundProtocol.FINDMY
                        Timber.i("▶ Sound protocol selected: Apple FindMy (fd44)")
                        writeCharacteristic(gatt, characteristic, FINDMY_START_SOUND_OPCODE, enableNotifications = true)
                        sendBluetoothEvent(BluetoothEvent.EventRunning)
                        return
                    }
                }

                // Priority 3: AirTag proprietary sound service
                // Important: Does not set notifications
                val airTagService = gatt.getService(AIRTAG_SOUND_SERVICE_UUID)
                Timber.d("AirTag service (${AIRTAG_SOUND_SERVICE_UUID}) found=${airTagService != null}")
                if (airTagService != null) {
                    val characteristic = airTagService.getCharacteristic(AIRTAG_SOUND_CHARACTERISTIC)
                    Timber.d("AirTag characteristic (%s) found=%b%s",
                        AIRTAG_SOUND_CHARACTERISTIC, characteristic != null,
                        if (characteristic != null) ", properties=0x${characteristic.properties.toString(16)}" else ""
                    )
                    if (characteristic != null) {
                        selectedSoundProtocol = SoundProtocol.AIRTAG
                        val value = ByteBuffer.allocate(1).order(ByteOrder.LITTLE_ENDIAN)
                            .put(175.toByte()).array()
                        Timber.i("▶ Sound protocol selected: AirTag proprietary — writing 0xAF to ${characteristic.uuid}")
                        // No setCharacteristicNotification for AirTag — matches original behaviour
                        writeCharacteristic(gatt, characteristic, value, enableNotifications = false)
                        sendBluetoothEvent(BluetoothEvent.EventRunning)
                        return
                    }
                }

                Timber.e("No compatible sound service found among: $uuids")
                disconnect(gatt)
                sendBluetoothEvent(BluetoothEvent.EventFailed)
            }

            @SuppressLint("MissingPermission")
            override fun onCharacteristicWrite(
                gatt: BluetoothGatt?,
                characteristic: BluetoothGattCharacteristic?,
                status: Int
            ) {
                val lastWritten = getLastWrittenValue(characteristic)
                Timber.d("onCharacteristicWrite: status=$status, characteristic=${characteristic?.uuid}, " +
                    "writtenValue=${lastWritten.toHexString()}, protocol=$selectedSoundProtocol")

                when {
                    status == BluetoothGatt.GATT_SUCCESS && gatt != null -> {
                        when (selectedSoundProtocol) {
                            // DULT and FindMy: start → wait 5 s → stop → disconnect
                            SoundProtocol.DULT, SoundProtocol.FINDMY -> {
                                val (startOpcode, stopOpcode, protocolName) =
                                    if (selectedSoundProtocol == SoundProtocol.DULT)
                                        Triple(DULT_START_SOUND_OPCODE, DULT_STOP_SOUND_OPCODE, "DULT")
                                    else
                                        Triple(FINDMY_START_SOUND_OPCODE, FINDMY_STOP_SOUND_OPCODE, "FindMy")

                                when {
                                    lastWritten.contentEquals(startOpcode) -> {
                                        Timber.d("$protocolName: start-sound write confirmed — scheduling stop in 5 s")
                                        Handler(Looper.getMainLooper()).postDelayed({
                                            Timber.d("$protocolName: 5 s elapsed, sending stop-sound command")
                                            stopSound(gatt)
                                        }, 5000)
                                    }
                                    lastWritten.contentEquals(stopOpcode) -> {
                                        Timber.d("$protocolName: stop-sound write confirmed — disconnecting")
                                        disconnect(gatt)
                                        sendBluetoothEvent(BluetoothEvent.EventCompleted)
                                    }
                                    else -> {
                                        Timber.w("$protocolName: unrecognised written value ${lastWritten.toHexString()}")
                                    }
                                }
                            }

                            // AirTag: completion signaled by a property flag OR by remote disconnect (status 19)
                            SoundProtocol.AIRTAG -> {
                                val props = characteristic?.properties ?: 0
                                val callbackMatch = (props and AIRTAG_EVENT_CALLBACK) == AIRTAG_EVENT_CALLBACK
                                Timber.d("AirTag: write confirmed, characteristic.properties=0x${props.toString(16)}, " +
                                    "AIRTAG_EVENT_CALLBACK=0x${AIRTAG_EVENT_CALLBACK.toString(16)}, match=$callbackMatch")
                                if (callbackMatch) {
                                    Timber.d("AirTag: event-callback flag matched — completing")
                                    sendBluetoothEvent(BluetoothEvent.EventCompleted)
                                    disconnect(gatt)
                                } else {
                                    Timber.d("AirTag: event-callback flag NOT matched — waiting for remote disconnect (status 19)")
                                }
                            }

                            null -> {
                                Timber.e("onCharacteristicWrite: selectedSoundProtocol is null — this should not happen")
                                disconnect(gatt)
                                sendBluetoothEvent(BluetoothEvent.EventFailed)
                            }
                        }
                    }

                    status == 133 -> {
                        // GATT_ERROR / connection timeout
                        Timber.e("GATT error 133 (timeout) writing characteristic ${characteristic?.uuid}")
                        sendBluetoothEvent(BluetoothEvent.EventFailed)
                        disconnect(gatt)
                    }

                    else -> {
                        Timber.e("Characteristic write failed: status=$status, uuid=${characteristic?.uuid}")
                        disconnect(gatt)
                        sendBluetoothEvent(BluetoothEvent.EventFailed)
                    }
                }
                super.onCharacteristicWrite(gatt, characteristic, status)
            }

            /** Called for AirTag when the device sends back a read result post-sound. */
            override fun onCharacteristicRead(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
                status: Int
            ) {
                val props = characteristic.properties
                Timber.d("onCharacteristicRead: status=$status, uuid=${characteristic.uuid}, " +
                    "properties=0x${props.toString(16)}, protocol=$selectedSoundProtocol")
                if (status == BluetoothGatt.GATT_SUCCESS &&
                    selectedSoundProtocol == SoundProtocol.AIRTAG &&
                    (props and AIRTAG_EVENT_CALLBACK) == AIRTAG_EVENT_CALLBACK) {
                    Timber.d("AirTag: read confirmed event-callback flag — completing")
                    sendBluetoothEvent(BluetoothEvent.EventCompleted)
                    disconnect(gatt)
                }
            }

            private fun getLastWrittenValue(characteristic: BluetoothGattCharacteristic?): ByteArray {
                return characteristic?.let {
                    @Suppress("DEPRECATION")
                    it.value ?: ByteArray(0)
                } ?: ByteArray(0)
            }
        }

    companion object : DeviceContext {
        // DULT sound service
        // Opcodes are 2-byte little-endian: Sound_Start=0x0300, Sound_Stop=0x0301
        internal val DULT_SOUND_SERVICE_UUID: UUID =
            UUID.fromString("15190001-12F4-C226-88ED-2AC5579F2A85")
        internal val DULT_SOUND_CHARACTERISTIC: UUID =
            UUID.fromString("8E0C0001-1D68-FB92-BF61-48377421680E")
        internal val DULT_START_SOUND_OPCODE = byteArrayOf(0x00, 0x03) // Sound_Start (0x0300 LE)
        internal val DULT_STOP_SOUND_OPCODE  = byteArrayOf(0x01, 0x03) // Sound_Stop  (0x0301 LE)

        // Apple Find My sound service
        internal const val FINDMY_SOUND_SERVICE = "fd44"
        internal val FINDMY_SOUND_CHARACTERISTIC: UUID =
            UUID.fromString("4F860003-943B-49EF-BED4-2F730304427A")
        internal val FINDMY_START_SOUND_OPCODE = byteArrayOf(0x01, 0x00, 0x03)
        internal val FINDMY_STOP_SOUND_OPCODE  = byteArrayOf(0x01, 0x01, 0x03)

        // AirTag sound service
        internal val AIRTAG_SOUND_SERVICE_UUID: UUID =
            UUID.fromString("7DFC9000-7D1C-4951-86AA-8D9728F8D66C")
        internal val AIRTAG_SOUND_CHARACTERISTIC: UUID =
            UUID.fromString("7DFC9001-7D1C-4951-86AA-8D9728F8D66C")
        internal const val AIRTAG_EVENT_CALLBACK = 0x302

        private val GATT_GENERIC_ACCESS_SERVICE =
            UUID.fromString("87290102-3C51-43B1-A1A9-11B9DC38478B")
        private val GATT_DEVICE_NAME_CHARACTERISTIC =
            UUID.fromString("6AA50003-6352-4D57-A7B4-003A416FBB0B")

        override val bluetoothFilter: ScanFilter
            get() = ScanFilter.Builder()
                .setManufacturerData(
                    0x4C,
                    byteArrayOf((0x12).toByte(), (0x19).toByte(), (0x10).toByte()),
                    byteArrayOf((0xFF).toByte(), (0x00).toByte(), (0x18).toByte())
                )
                .build()

        override val deviceType: DeviceType
            get() = DeviceType.FIND_MY

        override val websiteManufacturer: String
            get() = "https://www.apple.com/"

        override val defaultDeviceName: String
            get() = ATTrackingDetectionApplication.getAppContext().resources
                .getString(R.string.apple_find_my_default_name)

        override val statusByteDeviceType: UInt
            get() = 2u

        override fun getConnectionState(scanResult: ScanResult): ConnectionState {
            val mfg: ByteArray? = scanResult.scanRecord?.getManufacturerSpecificData(0x4C)
            if (mfg != null && mfg.size > 2) {
                return if (mfg[1] == (0x19).toByte())
                    ConnectionState.OVERMATURE_OFFLINE
                else
                    ConnectionState.CONNECTED
            }
            return ConnectionState.UNKNOWN
        }

        override fun getBatteryState(scanResult: ScanResult): BatteryState {
            val mfg: ByteArray? = scanResult.scanRecord?.getManufacturerSpecificData(0x4C)
            if (mfg != null && mfg.size >= 3) {
                val batteryLevel = (mfg[2].toInt() shr 6) and 0x03
                return when (batteryLevel) {
                    0x00 -> BatteryState.FULL
                    0x01 -> BatteryState.MEDIUM
                    0x02 -> BatteryState.LOW
                    0x03 -> BatteryState.VERY_LOW
                    else -> BatteryState.UNKNOWN
                }
            }
            return BatteryState.UNKNOWN
        }

        // For AirPods left/right differentiation
        suspend fun getSubTypeName(wrappedScanResult: ScanResultWrapper): String {
            val characteristicsToRead = listOf(
                Triple(GATT_GENERIC_ACCESS_SERVICE, GATT_DEVICE_NAME_CHARACTERISTIC, "string")
            )

            val deviceName = Utility.connectAndRetrieveCharacteristics(
                ATTrackingDetectionApplication.getAppContext(),
                wrappedScanResult.deviceAddress,
                characteristicsToRead
            )[GATT_DEVICE_NAME_CHARACTERISTIC] as? String

            if (!deviceName.isNullOrEmpty()) {
                val deviceNameTrimmed = deviceName.trim()
                if (deviceNameTrimmed == "left" || deviceNameTrimmed == "right") {
                    val airpodsString = ATTrackingDetectionApplication.getAppContext().resources
                        .getString(R.string.airpods)
                    val sideString = ATTrackingDetectionApplication.getAppContext().resources
                        .getString(if (deviceNameTrimmed == "left") R.string.left else R.string.right)
                    return "$airpodsString - $sideString"
                }
                ScanFragment.deviceNameMap[wrappedScanResult.uniqueIdentifier] = deviceName
                return deviceName
            }
            return ATTrackingDetectionApplication.getAppContext().resources
                .getString(R.string.apple_find_my_default_name)
        }

        private fun ByteArray.toHexString(): String =
            joinToString(separator = " ") { "0x%02X".format(it) }
    }
}

