package de.seemoo.at_tracking_detection.util.ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication
import timber.log.Timber

object BluetoothStateMonitor {
    interface Listener { fun onBluetoothStateChanged(enabled: Boolean) }

    private val ctx: Context get() = ATTrackingDetectionApplication.getAppContext()
    private val listeners = mutableSetOf<Listener>()
    private var registered = false

    fun isBluetoothEnabled(): Boolean {
        val mgr = ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?
        return mgr?.adapter?.isEnabled == true
    }

    fun addListener(listener: Listener) {
        listeners.add(listener)
        ensureRegistered()
        // Also emit current immediately
        listener.onBluetoothStateChanged(isBluetoothEnabled())
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
        if (listeners.isEmpty()) unregister()
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (BluetoothAdapter.ACTION_STATE_CHANGED == intent.action) {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                val enabled = state == BluetoothAdapter.STATE_ON
                Timber.d("Bluetooth state changed: $state (enabled=$enabled)")
                listeners.forEach { it.onBluetoothStateChanged(enabled) }
            }
        }
    }

    private fun ensureRegistered() {
        if (!registered) {
            ctx.registerReceiver(receiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
            registered = true
        }
    }

    private fun unregister() {
        if (registered) {
            try { ctx.unregisterReceiver(receiver) } catch (_: Throwable) {}
            registered = false
        }
    }
}
