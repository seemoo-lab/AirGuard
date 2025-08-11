package de.seemoo.at_tracking_detection.util.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import androidx.core.content.ContextCompat
import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication
import timber.log.Timber
import java.util.ArrayDeque

object ScanOrchestrator {
    enum class Priority { HIGH, MEDIUM, LOW }

    private const val STOP_START_GRACE_MS = 400L
    private const val THROTTLE_WINDOW_MS = 30_000L
    private const val THROTTLE_MAX_STARTS = 5

    private val appContext: Context
        get() = ATTrackingDetectionApplication.getAppContext()

    private val bluetoothManager: BluetoothManager? by lazy {
        appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    }

    private val handlerThread = HandlerThread("BleScanOrchestrator")
    private val handler: Handler

    @Volatile private var currentCallback: ScanCallback? = null
    @Volatile private var currentFilters: List<ScanFilter>? = null
    @Volatile private var currentSettings: ScanSettings? = null
    @Volatile private var currentPriority: Priority? = null
    @Volatile private var currentOwnerTag: String? = null

    @Volatile private var isStartingOrRunning = false
    @Volatile private var isStopping = false

    // Permanent lease: tag and desired config to auto-resume when idle
    @Volatile private var leaseHolderTag: String? = null
    @Volatile private var leaseFilters: List<ScanFilter>? = null
    @Volatile private var leaseSettings: ScanSettings? = null
    @Volatile private var leaseCallback: ScanCallback? = null

    // timestamps for throttle
    private val recentStarts = ArrayDeque<Long>(THROTTLE_MAX_STARTS + 1)

    init {
        handlerThread.start()
        handler = Handler(handlerThread.looper)
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(appContext, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasScanPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            hasPermission(Manifest.permission.BLUETOOTH_SCAN)
        } else true
    }

    fun getLeScanner(): BluetoothLeScanner? {
        val adapter = bluetoothManager?.adapter ?: return null
        return try { adapter.bluetoothLeScanner } catch (_: Throwable) { null }
    }

    private fun canStartNow(): Boolean {
        val now = System.currentTimeMillis()
        while (true) {
            val head = recentStarts.peekFirst() ?: break
            if (now - head > THROTTLE_WINDOW_MS) {
                recentStarts.removeFirst()
            } else break
        }
        return recentStarts.size < THROTTLE_MAX_STARTS
    }

    private fun recordStart() {
        val now = System.currentTimeMillis()
        recentStarts.addLast(now)
        while (recentStarts.size > THROTTLE_MAX_STARTS) {
            recentStarts.removeFirst()
        }
    }

// Public API

    // Higher priority can preempt lower priority
    fun startScan(
        callerTag: String,
        filters: List<ScanFilter>,
        settings: ScanSettings,
        callback: ScanCallback,
        priority: Priority,
        allowReplaceExisting: Boolean = true
    ) {
        handler.post {
            Timber.d("ScanOrchestrator.startScan by $callerTag priority=$priority")
            if (!hasScanPermission()) {
                Timber.w("BLUETOOTH_SCAN not granted")
                return@post
            }
            val scanner = getLeScanner()
            if (scanner == null) {
                Timber.w("BluetoothLeScanner is null (adapter off or transient)")
                return@post
            }

            // Preemption logic
            if (isStartingOrRunning || isStopping) {
                val runningPrio = currentPriority
                // If current is lower priority than requested, preempt
                val shouldPreempt = runningPrio != null && priorityHigherThan(priority, runningPrio)
                if (shouldPreempt) {
                    Timber.d("Preempting $currentOwnerTag ($runningPrio) with $callerTag ($priority)")
                    internalStop(scanner, currentCallback)
                    handler.postDelayed({
                        internalStart(scanner, filters, settings, callback, priority, callerTag)
                    }, STOP_START_GRACE_MS)
                    return@post
                }

                // If not preempting, only replace if allowed and same or lower priority caller requests it
                if (!allowReplaceExisting) {
                    Timber.d("Scan already running or stopping; ignoring request from $callerTag")
                    return@post
                }
                // If same caller and same or higher priority, we can replace (e.g., change mode)
                if (callerTag == currentOwnerTag) {
                    Timber.d("Replacing running scan with new request from same owner $callerTag")
                    internalStop(scanner, currentCallback)
                    handler.postDelayed({
                        internalStart(scanner, filters, settings, callback, priority, callerTag)
                    }, STOP_START_GRACE_MS)
                } else {
                    Timber.d("Another scan running by $currentOwnerTag with priority=$runningPrio; not replacing with $callerTag ($priority)")
                }
                return@post
            }

            if (!canStartNow()) {
                Timber.w("Scan start throttled: too many starts within window")
                return@post
            }

            internalStart(scanner, filters, settings, callback, priority, callerTag)
        }
    }

    // Permanent lease (LOW priority). It will hold/auto-resume when no higher-priority scan is active.
    fun ensureRunningLease(
        callerTag: String,
        filters: List<ScanFilter>,
        settings: ScanSettings,
        callback: ScanCallback
    ) {
        handler.post {
            leaseHolderTag = callerTag
            leaseFilters = filters
            leaseSettings = settings
            leaseCallback = callback

            if (!hasScanPermission()) {
                Timber.w("ensureRunningLease: BLUETOOTH_SCAN not granted")
                return@post
            }
            val scanner = getLeScanner() ?: run {
                Timber.w("ensureRunningLease: scanner null")
                return@post
            }

            // If a scan is running with higher or equal priority, don't replace.
            val runningPrio = currentPriority
            if (isStartingOrRunning || isStopping) {
                if (runningPrio == Priority.LOW && currentOwnerTag != callerTag) {
                    // Replace another LOW with our lease if desired
                    internalStop(scanner, currentCallback)
                    handler.postDelayed({
                        internalStart(scanner, filters, settings, callback, Priority.LOW, callerTag)
                    }, STOP_START_GRACE_MS)
                }
                return@post
            }

            if (!canStartNow()) {
                Timber.w("ensureRunningLease: throttled; will not start now")
                return@post
            }
            internalStart(scanner, filters, settings, callback, Priority.LOW, callerTag)
        }
    }

    fun releaseLease(callerTag: String) {
        handler.post {
            if (leaseHolderTag == callerTag) {
                leaseHolderTag = null
                leaseFilters = null
                leaseSettings = null
                leaseCallback = null
            }
        }
    }

    fun stopScan(callerTag: String, callback: ScanCallback?) {
        handler.post {
            Timber.d("ScanOrchestrator.stopScan by $callerTag")
            val scanner = getLeScanner()
            if (scanner == null) {
                clearState()
                // Try resume a lease if exists
                tryResumeLease()
                return@post
            }
            internalStop(scanner, callback ?: currentCallback)
            if (leaseHolderTag == callerTag) {
                // If the stop comes from the lease owner, also release lease
                releaseLease(callerTag)
            }
            // Attempt to resume lease if we stopped a higher-priority scan
            handler.postDelayed({ tryResumeLease() }, STOP_START_GRACE_MS)
        }
    }

// Internal operations

    private fun internalStart(
        scanner: BluetoothLeScanner,
        filters: List<ScanFilter>,
        settings: ScanSettings,
        callback: ScanCallback,
        priority: Priority,
        ownerTag: String
    ) {
        if (isStartingOrRunning || isStopping) return
        try {
            isStartingOrRunning = true
            currentCallback = callback
            currentFilters = filters
            currentSettings = settings
            currentPriority = priority
            currentOwnerTag = ownerTag
            recordStart()
            scanner.startScan(filters, settings, callback)
            Timber.d("BLE scan started owner=$ownerTag mode=${settings.scanMode} priority=$priority")
        } catch (sec: SecurityException) {
            Timber.e(sec, "SecurityException starting scan")
            clearState()
        } catch (ise: IllegalStateException) {
            Timber.w(ise, "IllegalStateException starting scan")
            clearState()
        } catch (t: Throwable) {
            Timber.e(t, "Unexpected error starting scan")
            clearState()
        }
    }

    @SuppressLint("MissingPermission")
    private fun internalStop(scanner: BluetoothLeScanner, callback: ScanCallback?) {
        if (callback == null) {
            clearState()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !hasScanPermission()) {
            Timber.w("Missing BLUETOOTH_SCAN for stopScan; clearing state without calling stop")
            clearState()
            return
        }
        if (!isStartingOrRunning && !isStopping) {
            clearState()
            return
        }
        try {
            isStopping = true
            scanner.stopScan(callback)
            Timber.d("BLE scan stopped owner=$currentOwnerTag")
        } catch (t: Throwable) {
            Timber.w(t, "Error stopping scan (ignored)")
        } finally {
            clearState()
        }
    }

    private fun clearState() {
        isStartingOrRunning = false
        isStopping = false
        currentCallback = null
        currentFilters = null
        currentSettings = null
        currentPriority = null
        currentOwnerTag = null
    }

    private fun tryResumeLease() {
        // If nothing running and we have a lease, resume it
        if (isStartingOrRunning || isStopping) return
        val lTag = leaseHolderTag ?: return
        val lFilters = leaseFilters ?: return
        val lSettings = leaseSettings ?: return
        val lCallback = leaseCallback ?: return
        if (!hasScanPermission()) return
        val scanner = getLeScanner() ?: return
        if (!canStartNow()) return

        internalStart(scanner, lFilters, lSettings, lCallback, Priority.LOW, lTag)
    }

    private fun priorityHigherThan(a: Priority, b: Priority): Boolean {
        // HIGH > MEDIUM > LOW
        return when (a) {
            Priority.HIGH -> b != Priority.HIGH
            Priority.MEDIUM -> b == Priority.LOW
            Priority.LOW -> false
        }
    }
}