package de.seemoo.at_tracking_detection.database.models.device

import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import de.seemoo.at_tracking_detection.util.risk.RiskLevelEvaluator

interface DeviceContext {
    val bluetoothFilter: ScanFilter

    val deviceType: DeviceType

    val defaultDeviceName: String

    /** Minimum time the device needs to be following in seconds */
    val minTrackingTime: Int
        get() = 30 * 60

    val statusByteDeviceType: UInt

    val numberOfDaysToBeConsideredForTrackingDetection: Long
        get() = RiskLevelEvaluator.RELEVANT_DAYS

    val numberOfLocationsToBeConsideredForTrackingDetection: Int
        get() = RiskLevelEvaluator.getLocationsAtLeastTrackedBeforeAlarm()

    fun getConnectionState(scanResult: ScanResult): ConnectionState {
        return ConnectionState.UNKNOWN
    }

    fun getBatteryState(scanResult: ScanResult): BatteryState {
        return BatteryState.UNKNOWN
    }

    fun getPublicKey(scanResult: ScanResult): String{
        return scanResult.device.address
    }
}