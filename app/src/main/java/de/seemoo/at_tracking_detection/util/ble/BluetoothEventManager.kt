package de.seemoo.at_tracking_detection.util.ble

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages Bluetooth connection events using SharedFlow.
 */
@Singleton
class BluetoothEventManager @Inject constructor() {

    private val _events = MutableSharedFlow<BluetoothEvent>(
        replay = 0,
        extraBufferCapacity = 10
    )

    val events: SharedFlow<BluetoothEvent> = _events.asSharedFlow()

    /**
     * Try to send a Bluetooth event (non-suspending)
     * Returns true if successful, false otherwise
     */
    fun trySendEvent(event: BluetoothEvent): Boolean {
        return _events.tryEmit(event)
    }
}

