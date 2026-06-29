package de.seemoo.at_tracking_detection.database.models.device.types

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
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
import de.seemoo.at_tracking_detection.ui.scan.ScanResultWrapper
import de.seemoo.at_tracking_detection.util.Utility
import de.seemoo.at_tracking_detection.util.ble.BluetoothEvent
import de.seemoo.at_tracking_detection.util.ble.DeviceSubTypeDetector
import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

open class AppleFindMy(val id: Int) : Device(), Connectable {

    // Three different types of Sound Playing Protocol possible, used in this priority
    // DULT: modern approach using the DULT standard
    // FindMy: Apple's normal Find My protocol (fd44 service) for most devices (Third Party Trackers, Airpods, etc.)
    // AirTag: the original AirTag protocol, used by first gen AirTags
    enum class SoundProtocol { DULT, FINDMY, AIRTAG }

    // Protocol selected for the current GATT session
    @Volatile private var selectedSoundProtocol: SoundProtocol? = null
    // True once the sound-start command has actually been written to the peripheral.
    @Volatile private var soundStarted: Boolean = false
    // Set to true when we intentionally disconnect the GATT link (e.g. after stopping sound or on error)
    @Volatile private var intentionalDisconnect: Boolean = false
    // Record the last value written to the characteristic so we can identify it in onCharacteristicWrite (needed for API < 33 where write callbacks don't include the value)
    private var lastWrittenValue: ByteArray = ByteArray(0)

    // Timers
    private val handler = Handler(Looper.getMainLooper())
    private var stopSoundRunnable: Runnable? = null
    private var discoveryTimeoutRunnable: Runnable? = null

    // Pending CCD State (for async notification/indication setup)
    private var pendingGatt: BluetoothGatt? = null
    private var pendingCharacteristic: BluetoothGattCharacteristic? = null
    private var pendingValue: ByteArray? = null

    // Priority order of which sound service is used if multiple are present
    // This gets overwritten by subtypes (AirPods and AirTags)
    open val soundProtocolPriority: List<SoundProtocol>
        get() = listOf(SoundProtocol.DULT, SoundProtocol.FINDMY, SoundProtocol.AIRTAG)

    // Connection hint: Which protocol to try first based on advertised service UUIDs
    fun preConnectHint(scanResult: ScanResult) {
        val advertisedUuids = scanResult.scanRecord?.serviceUuids
            ?.mapTo(mutableSetOf()) { it.uuid }
        if (advertisedUuids.isNullOrEmpty()) {
            Timber.d("preConnectHint: no advertised service UUIDs — will use priority list: $soundProtocolPriority")
            return
        }
        val detected = soundProtocolPriority.firstOrNull { proto ->
            when (proto) {
                SoundProtocol.DULT   -> DULT_SOUND_SERVICE_UUID in advertisedUuids
                SoundProtocol.FINDMY -> advertisedUuids.any { it.toString().lowercase().contains(FINDMY_SOUND_SERVICE.lowercase()) }
                SoundProtocol.AIRTAG -> AIRTAG_SOUND_SERVICE_UUID in advertisedUuids
            }
        }
        if (detected != null) {
            Timber.d("preConnectHint: pre-selecting $detected from advertised UUIDs")
            selectedSoundProtocol = detected
        } else {
            Timber.d("preConnectHint: no match in advertised UUIDs --> use priority list")
        }
    }

    // Reset all per-session state and cancel any pending timers or runnables.
    private fun resetSessionState() {
        selectedSoundProtocol = null
        soundStarted = false
        intentionalDisconnect = false
        lastWrittenValue = ByteArray(0)
        stopSoundRunnable?.let { handler.removeCallbacks(it) }; stopSoundRunnable        = null
        discoveryTimeoutRunnable?.let { handler.removeCallbacks(it) }; discoveryTimeoutRunnable = null
        pendingGatt = null
        pendingCharacteristic = null
        pendingValue = null
    }

    // Called when the start-sound command has been sent to the peripheral, either directly (AirTag) or after CCCD setup (DULT/FindMy).
    private fun onSoundCommandSent() {
        soundStarted = true
        sendBluetoothEvent(BluetoothEvent.EventRunning)
    }

    // Writes the given value to the characteristic
    // optionally setting up notifications/indications first if the characteristic supports it
    // For DULT and FindMy protocols, notifications are required to know when to send the stop command, so they are enabled by default
    // For AirTag, no notifications are used and the completion signal is the remote disconnect (status 19)
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

            val cccd = characteristic.getDescriptor(CCCD_UUID)
            if (cccd != null) {
                val cccdValue = if ((characteristic.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0)
                    BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                else
                    BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE

                pendingGatt           = gatt
                pendingCharacteristic = characteristic
                pendingValue          = value

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val result = gatt.writeDescriptor(cccd, cccdValue)
                    Timber.d("writeDescriptor CCCD (API33+) → result=$result, cccdValue=${cccdValue.toHexString()}")
                } else {
                    @Suppress("DEPRECATION")
                    cccd.value = cccdValue
                    @Suppress("DEPRECATION")
                    val queued = gatt.writeDescriptor(cccd)
                    Timber.d("writeDescriptor CCCD (legacy) → queued=$queued, cccdValue=${cccdValue.toHexString()}")
                }
                return // continued in onDescriptorWrite
            }
            Timber.w("No CCCD descriptor on ${characteristic.uuid} — writing characteristic directly")
        }
        writeCharacteristicRaw(gatt, characteristic, value)
        // For the no-CCCD fallback path, signal EventRunning here since onDescriptorWrite won't fire
        if (enableNotifications) onSoundCommandSent()
    }

    // Writes the given value to the characteristic without any CCCD setup (for AirTag or if CCCD is missing)
    @SuppressLint("MissingPermission")
    private fun writeCharacteristicRaw(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray
    ) {
        lastWrittenValue = value
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

    // Send the stop-sound command based on the negotiated protocol.
    // This is sometimes not possible or does not actually stop the sound
    @SuppressLint("MissingPermission")
    private fun stopSound(gatt: BluetoothGatt) {
        Timber.d("stopSound called, protocol=$selectedSoundProtocol")
        val (serviceKey, charUUID, stopOpcode) = when (selectedSoundProtocol) {
            SoundProtocol.DULT ->
                Triple(DULT_SOUND_SERVICE_UUID.toString(), DULT_SOUND_CHARACTERISTIC, DULT_STOP_SOUND_OPCODE)
            SoundProtocol.FINDMY ->
                Triple(FINDMY_SOUND_SERVICE, FINDMY_SOUND_CHARACTERISTIC, FINDMY_STOP_SOUND_OPCODE)
            else -> {
                Timber.d("stopSound: AirTag — no explicit stop, relies on remote disconnect")
                return
            }
        }

        val service = gatt.services.firstOrNull {
            it.uuid.toString().lowercase().contains(serviceKey.lowercase())
        } ?: run {
            Timber.w("stopSound: service not found (key=$serviceKey)")
            return
        }

        val characteristic = service.getCharacteristic(charUUID) ?: run {
            Timber.w("stopSound: characteristic not found ($charUUID)")
            return
        }

        Timber.d("stopSound: writing stop opcode ${stopOpcode.toHexString()} to ${characteristic.uuid}")
        writeCharacteristic(gatt, characteristic, stopOpcode, enableNotifications = true)
    }

    // Attempt to start the given protocol by finding the corresponding service and characteristic, writing the start opcode, and enabling notifications if needed.
    @SuppressLint("MissingPermission")
    private fun tryStartProtocol(gatt: BluetoothGatt, protocol: SoundProtocol): Boolean {
        Timber.d("tryStartProtocol: trying $protocol")
        return when (protocol) {

            SoundProtocol.DULT -> {
                val service = gatt.getService(DULT_SOUND_SERVICE_UUID)
                Timber.d("DULT service ($DULT_SOUND_SERVICE_UUID) found=${service != null}")
                val characteristic = service?.getCharacteristic(DULT_SOUND_CHARACTERISTIC)
                Timber.d("DULT characteristic ($DULT_SOUND_CHARACTERISTIC) found=${characteristic != null}")
                if (characteristic == null) return false
                selectedSoundProtocol = SoundProtocol.DULT
                Timber.i("Sound protocol selected: DULT")
                // EventRunning is deferred until after CCCD is confirmed in onDescriptorWrite
                writeCharacteristic(gatt, characteristic, DULT_START_SOUND_OPCODE, enableNotifications = true)
                true
            }

            SoundProtocol.FINDMY -> {
                val service = gatt.services.firstOrNull {
                    it.uuid.toString().lowercase().contains(FINDMY_SOUND_SERVICE.lowercase())
                }
                Timber.d("FindMy service found=${service != null}${if (service != null) ", uuid=${service.uuid}" else ""}")
                val characteristic = service?.getCharacteristic(FINDMY_SOUND_CHARACTERISTIC)
                Timber.d("FindMy characteristic ($FINDMY_SOUND_CHARACTERISTIC) found=${characteristic != null}")
                if (characteristic == null) return false
                selectedSoundProtocol = SoundProtocol.FINDMY
                Timber.i("Sound protocol selected: FindMy (fd44)")
                writeCharacteristic(gatt, characteristic, FINDMY_START_SOUND_OPCODE, enableNotifications = true)
                true
            }

            SoundProtocol.AIRTAG -> {
                val service = gatt.getService(AIRTAG_SOUND_SERVICE_UUID)
                Timber.d("AirTag service ($AIRTAG_SOUND_SERVICE_UUID) found=${service != null}")
                val characteristic = service?.getCharacteristic(AIRTAG_SOUND_CHARACTERISTIC)
                Timber.d("null%s", if (characteristic != null) ", properties=0x${characteristic.properties.toString(16)}" else "")
                if (characteristic == null) return false
                selectedSoundProtocol = SoundProtocol.AIRTAG
                Timber.i("Sound protocol selected: AirTag")
                // AirTag uses no notifications — write the trigger byte (0xAF) directly.
                // Completion is signaled by the device closing the connection (status 19).
                val value = ByteBuffer.allocate(1).order(ByteOrder.LITTLE_ENDIAN).put(175.toByte()).array()
                writeCharacteristicRaw(gatt, characteristic, value)
                onSoundCommandSent()
                true
            }
        }
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
                            Timber.d("GATT connected — starting service discovery")
                            val started = gatt.discoverServices()
                            if (!started) {
                                Timber.e("discoverServices() returned false — aborting")
                                intentionalDisconnect = true
                                disconnect(gatt)
                                sendBluetoothEvent(BluetoothEvent.EventFailed)
                                return
                            }
                            // 10-second timeout so the UI never hangs if discovery stalls
                            discoveryTimeoutRunnable = Runnable {
                                Timber.e("Service discovery timed out after 10 s")
                                intentionalDisconnect = true
                                disconnect(gatt)
                                resetSessionState()
                                sendBluetoothEvent(BluetoothEvent.EventFailed)
                            }.also { handler.postDelayed(it, 10_000) }
                        }

                        BluetoothProfile.STATE_DISCONNECTED -> {
                            gatt.close()
                            val wasIntentional = intentionalDisconnect
                            resetSessionState()
                            if (!wasIntentional) {
                                Timber.d("GATT disconnected unexpectedly — notifying UI")
                                sendBluetoothEvent(BluetoothEvent.Disconnected)
                            } else {
                                Timber.d("GATT disconnected after intentional session end — suppressing Disconnected event")
                            }
                        }

                        else -> Timber.d("Unhandled connection state: $newState")
                    }

                    19 -> {
                        gatt.close()
                        val wasAirTagCompletion = selectedSoundProtocol == SoundProtocol.AIRTAG && soundStarted
                        Timber.d("Status 19 — AirTag completion=$wasAirTagCompletion " +
                            "(protocol=$selectedSoundProtocol, soundStarted=$soundStarted)")
                        resetSessionState()
                        sendBluetoothEvent(
                            if (wasAirTagCompletion) BluetoothEvent.EventCompleted
                            else BluetoothEvent.EventFailed
                        )
                    }

                    else -> {
                        Timber.e("GATT connection error: status=$status")
                        gatt.close()
                        resetSessionState()
                        sendBluetoothEvent(BluetoothEvent.EventFailed)
                    }
                }
            }

            @SuppressLint("MissingPermission")
            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                // Cancel the discovery timeout regardless of outcome
                discoveryTimeoutRunnable?.let { handler.removeCallbacks(it) }
                discoveryTimeoutRunnable = null

                if (status != BluetoothGatt.GATT_SUCCESS) {
                    Timber.e("onServicesDiscovered failed with status=$status")
                    intentionalDisconnect = true
                    disconnect(gatt)
                    sendBluetoothEvent(BluetoothEvent.EventFailed)
                    return
                }

                val uuids = gatt.services.map { it.uuid.toString() }
                Timber.d("onServicesDiscovered: ${uuids.size} services: $uuids")

                // Build effective priority: pre-hinted protocol (from preConnectHint)
                val hint = selectedSoundProtocol
                val effectivePriority = if (hint != null) {
                    Timber.d("Pre-hint active ($hint) — trying it first")
                    listOf(hint) + soundProtocolPriority.filter { it != hint }
                } else {
                    Timber.d("No pre-hint — using device priority: $soundProtocolPriority")
                    soundProtocolPriority
                }

                for (protocol in effectivePriority) {
                    if (tryStartProtocol(gatt, protocol)) return
                }

                Timber.e("No compatible sound service found among: $uuids")
                intentionalDisconnect = true
                disconnect(gatt)
                sendBluetoothEvent(BluetoothEvent.EventFailed)
            }

            @SuppressLint("MissingPermission")
            override fun onDescriptorWrite(
                gatt: BluetoothGatt,
                descriptor: BluetoothGattDescriptor,
                status: Int
            ) {
                if (descriptor.uuid != CCCD_UUID) return // only care about CCCD writes

                val pg = pendingGatt
                val pc = pendingCharacteristic
                val pv = pendingValue
                pendingGatt           = null
                pendingCharacteristic = null
                pendingValue          = null

                if (pg == null || pc == null || pv == null) {
                    Timber.w("onDescriptorWrite: CCCD ack but no pending characteristic write — ignoring")
                    return
                }
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    Timber.w("CCCD write failed (status=$status) — proceeding with characteristic write anyway")
                }
                Timber.d("CCCD confirmed — writing start opcode to ${pc.uuid}")
                writeCharacteristicRaw(pg, pc, pv)
                onSoundCommandSent()
            }

            @SuppressLint("MissingPermission")
            override fun onCharacteristicWrite(
                gatt: BluetoothGatt?,
                characteristic: BluetoothGattCharacteristic?,
                status: Int
            ) {
                Timber.d("onCharacteristicWrite: status=$status, uuid=${characteristic?.uuid}, " +
                    "writtenValue=${lastWrittenValue.toHexString()}, protocol=$selectedSoundProtocol")

                when {
                    status == BluetoothGatt.GATT_SUCCESS && gatt != null -> {
                        when (selectedSoundProtocol) {

                            SoundProtocol.DULT, SoundProtocol.FINDMY -> {
                                val (startOpcode, stopOpcode, name) =
                                    if (selectedSoundProtocol == SoundProtocol.DULT)
                                        Triple(DULT_START_SOUND_OPCODE, DULT_STOP_SOUND_OPCODE, "DULT")
                                    else
                                        Triple(FINDMY_START_SOUND_OPCODE, FINDMY_STOP_SOUND_OPCODE, "FindMy")

                                when {
                                    lastWrittenValue.contentEquals(startOpcode) -> {
                                        Timber.d("$name: start confirmed — scheduling stop in 5 s")
                                        val runnable = Runnable {
                                            Timber.d("$name: 5 s elapsed, sending stop command")
                                            stopSound(gatt)
                                        }
                                        stopSoundRunnable = runnable
                                        handler.postDelayed(runnable, 5_000)
                                    }
                                    lastWrittenValue.contentEquals(stopOpcode) -> {
                                        Timber.d("$name: stop confirmed — completing session")
                                        intentionalDisconnect = true
                                        disconnect(gatt)
                                        sendBluetoothEvent(BluetoothEvent.EventCompleted)
                                    }
                                    else ->
                                        Timber.w("$name: unrecognised write value ${lastWrittenValue.toHexString()}")
                                }
                            }

                            // AirTag: write was accepted by the device; the actual completion
                            // signal is the device closing the link (status 19 in
                            // onConnectionStateChange). Nothing further needed here.
                            SoundProtocol.AIRTAG ->
                                Timber.d("AirTag: write confirmed — waiting for remote disconnect (status 19)")

                            null -> {
                                Timber.e("onCharacteristicWrite: no protocol selected — aborting")
                                intentionalDisconnect = true
                                disconnect(gatt)
                                sendBluetoothEvent(BluetoothEvent.EventFailed)
                            }
                        }
                    }

                    status == 133 -> {
                        Timber.e("GATT error 133 (timeout) on ${characteristic?.uuid}")
                        intentionalDisconnect = true
                        disconnect(gatt)
                        sendBluetoothEvent(BluetoothEvent.EventFailed)
                    }

                    else -> {
                        Timber.e("Characteristic write failed: status=$status, uuid=${characteristic?.uuid}")
                        intentionalDisconnect = true
                        disconnect(gatt)
                        sendBluetoothEvent(BluetoothEvent.EventFailed)
                    }
                }
                super.onCharacteristicWrite(gatt, characteristic, status)
            }
        }

    override val imageResource: Int
        @DrawableRes
        get() = R.drawable.ic_chipolo

    override val defaultDeviceNameWithId: String
        get() = ATTrackingDetectionApplication.getAppContext().resources
            .getString(R.string.device_name_find_my_device_apple).format(id)

    override val deviceContext: DeviceContext
        get() = AppleFindMy

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

        // Standard CCCD descriptor UUID
        internal val CCCD_UUID: UUID =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

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
                DeviceSubTypeDetector.deviceNameMap[wrappedScanResult.uniqueIdentifier] = deviceName
                return deviceName
            }
            return ATTrackingDetectionApplication.getAppContext().resources
                .getString(R.string.apple_find_my_default_name)
        }

        internal fun ByteArray.toHexString(): String =
            joinToString(separator = " ") { "0x%02X".format(it) }
    }
}

