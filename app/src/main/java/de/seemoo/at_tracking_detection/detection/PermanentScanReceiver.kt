package de.seemoo.at_tracking_detection.detection

import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import de.seemoo.at_tracking_detection.ui.scan.ScanResultWrapper
import de.seemoo.at_tracking_detection.util.SharedPrefs
import de.seemoo.at_tracking_detection.util.Utility.BLELogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.LocalDateTime

/**
 * BroadcastReceiver for PendingIntent-based BLE scan results.
 *
 * Used by PermanentBluetoothScanner on Android 15+ where callback-based
 * background scanning is restricted by the system.
 *
 * The system delivers scan
 * results via this receiver even if the app process has been killed, ensuring
 * continuous tracker detection.
 *
 * On Android 12-14 the callback-based approach in PermanentBluetoothScanner
 * is used instead.
 */
class PermanentScanReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return

        // Check for scan error
        val errorCode = intent.getIntExtra(BluetoothLeScanner.EXTRA_ERROR_CODE, 0)
        if (errorCode != 0) {
            BLELogger.e("PendingIntent BLE scan error: $errorCode")
            if (errorCode == 2 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                SharedPrefs.showSamsungAndroid15BugNotification = true
            } else {
                SharedPrefs.showGenericBluetoothBugNotification = true
            }
            return
        }

        // Extract scan results from the Intent
        val results: List<ScanResult> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra(
                BluetoothLeScanner.EXTRA_LIST_SCAN_RESULT,
                ScanResult::class.java
            )
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra(BluetoothLeScanner.EXTRA_LIST_SCAN_RESULT)
        } ?: return

        if (results.isEmpty()) return

        SharedPrefs.showSamsungAndroid15BugNotification = false
        SharedPrefs.showGenericBluetoothBugNotification = false

        // Process results asynchronously; goAsync() keeps the receiver alive
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                for (scanResult in results) {
                    try {
                        val wrappedScanResult = ScanResultWrapper(scanResult)
                        val device = BackgroundBluetoothScanner.DiscoveredDevice(
                            wrappedScanResult, LocalDateTime.now()
                        )
                        PermanentBluetoothScanner.foundTracker(device)
                    } catch (t: Throwable) {
                        Timber.w(t, "Error processing single PendingIntent scan result")
                    }
                }
            } catch (t: Throwable) {
                Timber.e(t, "Error processing PendingIntent scan results batch")
            } finally {
                pendingResult.finish()
            }
        }
    }
}

