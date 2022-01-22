package de.seemoo.at_tracking_detection.database.models.device

import android.bluetooth.le.ScanFilter

interface DeviceContext {
    val bluetoothFilter: ScanFilter

    val bleDeviceFilter: BleDeviceFilter

    val deviceType: DeviceType
}