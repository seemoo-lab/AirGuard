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

    val numberOfHoursToBeConsideredForTrackingDetection: Long
        get() = RiskLevelEvaluator.RELEVANT_HOURS_TRACKING

    val numberOfLocationsToBeConsideredForTrackingDetectionLow: Int
        get() = RiskLevelEvaluator.NUMBER_OF_LOCATIONS_BEFORE_ALARM_LOW

    val numberOfLocationsToBeConsideredForTrackingDetectionMedium: Int
        get() = RiskLevelEvaluator.NUMBER_OF_LOCATIONS_BEFORE_ALARM_MEDIUM

    val numberOfLocationsToBeConsideredForTrackingDetectionHigh: Int
        get() = RiskLevelEvaluator.NUMBER_OF_LOCATIONS_BEFORE_ALARM_HIGH
    
    val websiteManufacturer: String
        get() = "https://www.seemoo.tu-darmstadt.de/"

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