package de.seemoo.at_tracking_detection.util.ble

sealed class BluetoothEvent {
    // Device is attempting to connect
    data object Connecting : BluetoothEvent()

    // Sound is currently playing
    data object EventRunning : BluetoothEvent()

    // Device has disconnected
    data object Disconnected : BluetoothEvent()

    // Sound completed successfully
    data object EventCompleted : BluetoothEvent()

    // Connection or sound playing failed
    data object EventFailed : BluetoothEvent()
}

