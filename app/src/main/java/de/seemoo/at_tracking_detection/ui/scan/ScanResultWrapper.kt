package de.seemoo.at_tracking_detection.ui.scan

import android.bluetooth.le.ScanResult
import de.seemoo.at_tracking_detection.database.models.device.BaseDevice.Companion.getConnectionState
import de.seemoo.at_tracking_detection.database.models.device.BaseDevice.Companion.getPublicKey
import de.seemoo.at_tracking_detection.database.models.device.DeviceManager.getDeviceType

data class ScanResultWrapper(val scanResult: ScanResult){
    val deviceAddress: String = scanResult.device.address
    var rssi: Int = scanResult.rssi
    var txPower: Int = scanResult.txPower
    var isConnectable: Boolean = scanResult.isConnectable
    val uniqueIdentifier = getPublicKey(scanResult)  // either public key or MAC-Address
    val deviceType = getDeviceType(scanResult)
    var connectionState = getConnectionState(scanResult)

    override fun hashCode(): Int {
        return scanResult.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        return super.equals(other)
    }
}