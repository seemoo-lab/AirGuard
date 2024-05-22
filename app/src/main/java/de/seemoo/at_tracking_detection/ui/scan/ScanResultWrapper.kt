package de.seemoo.at_tracking_detection.ui.scan

import android.bluetooth.le.ScanResult
import androidx.databinding.ObservableField
import de.seemoo.at_tracking_detection.database.models.device.BaseDevice.Companion.getConnectionState
import de.seemoo.at_tracking_detection.database.models.device.BaseDevice.Companion.getPublicKey
import de.seemoo.at_tracking_detection.database.models.device.DeviceManager.getDeviceType

data class ScanResultWrapper(val scanResult: ScanResult){
    val deviceAddress: String = scanResult.device.address
    val rssi: ObservableField<Int> = ObservableField(scanResult.rssi) // This is so the image can update itself live
    var rssiValue: Int = scanResult.rssi
    var txPower: Int = scanResult.txPower
    var isConnectable: Boolean = scanResult.isConnectable
    val deviceType = getDeviceType(scanResult)
    val uniqueIdentifier = getPublicKey(scanResult, deviceType)  // either public key or MAC-Address
    var connectionState = getConnectionState(scanResult, deviceType)
    val serviceUuids = scanResult.scanRecord?.serviceUuids?.map { it.toString() }?.toList()
    val mfg = scanResult.scanRecord?.bytes

    override fun hashCode(): Int {
        return scanResult.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        return super.equals(other)
    }
}