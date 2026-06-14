package de.seemoo.at_tracking_detection.ui.scan

import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.marginBottom
import androidx.core.view.updatePadding
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ConcatAdapter
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.database.models.Scan
import de.seemoo.at_tracking_detection.database.repository.ScanRepository
import de.seemoo.at_tracking_detection.databinding.FragmentScanBinding
import de.seemoo.at_tracking_detection.detection.BackgroundBluetoothScanner.getScanMode
import de.seemoo.at_tracking_detection.util.SharedPrefs
import de.seemoo.at_tracking_detection.util.ble.BLEScanner
import de.seemoo.at_tracking_detection.util.ble.DeviceSubTypeDetector
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap

@AndroidEntryPoint
class ScanFragment : Fragment() {
    private val scanViewModel: ScanViewModel by viewModels()
    private val bluetoothDeviceAdapterHighRisk = BluetoothDeviceAdapter()
    private val bluetoothDeviceAdapterLowRisk = BluetoothDeviceAdapter()
    private val separatorAdapter = ScanSeparatorAdapter()
    private val scanRepository: ScanRepository
        get() = ATTrackingDetectionApplication.getCurrentApp()?.scanRepository
            ?: error("ATTrackingDetectionApplication not initialized")
    private var scanId: Long = 0
    private var hasActiveScan = false

    // Automatic Detection of Devices / Subdevices through connection
    // Tracks addresses already enqueued
    private val detectionAttempted: MutableSet<String> = ConcurrentHashMap.newKeySet()
    // Detection Queue
    private val detectionQueue = Channel<ScanResultWrapper>(Channel.UNLIMITED)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding: FragmentScanBinding =
            DataBindingUtil.inflate(inflater, R.layout.fragment_scan, container, false)

        binding.apply {
            lifecycleOwner = viewLifecycleOwner
            vm = scanViewModel
        }

        // Use a single ConcatAdapter: high-risk devices → separator → low-risk devices.
        val concatAdapter = ConcatAdapter(
            bluetoothDeviceAdapterHighRisk,
            separatorAdapter,
            bluetoothDeviceAdapterLowRisk
        )
        binding.scanResultRecycler.adapter = concatAdapter

        scanViewModel.apply {
            bluetoothDeviceListHighRisk.observe(viewLifecycleOwner) { newList ->
                bluetoothDeviceAdapterHighRisk.submitList(newList)
            }
            bluetoothDeviceListLowRisk.observe(viewLifecycleOwner) { newList ->
                bluetoothDeviceAdapterLowRisk.submitList(newList)
            }
            // Show the separator only when there are low-risk devices to separate from.
            lowRiskIsEmpty.observe(viewLifecycleOwner) { isEmpty ->
                separatorAdapter.setVisible(!isEmpty)
            }
            scanFinished.observe(viewLifecycleOwner) { isFinished ->
                binding.buttonStartStopScan.setImageResource(
                    if (isFinished) R.drawable.ic_baseline_play_arrow_24
                    else R.drawable.ic_baseline_stop_24
                )
            }
        }

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Start the sequential detection queue processor
        startDetectionQueueProcessor()

        // Handle Edge-to-Edge padding for include views
        val emptyScanExplanation = view.findViewById<View>(R.id.include_scan_empty_explanation)
        val bluetoothDisabled = view.findViewById<View>(R.id.include_bluetooth_disabled)
        val locationDisabled = view.findViewById<View>(R.id.include_location_disabled)

        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val navView = requireActivity().findViewById<BottomNavigationView>(R.id.main_nav_view)
            val navHeight = if (navView != null && navView.height > 0)
                navView.height + navView.marginBottom
            else
                bars.bottom + (88 * resources.displayMetrics.density).toInt()

            emptyScanExplanation.updatePadding(top = bars.top, bottom = navHeight)
            bluetoothDisabled.updatePadding(top = bars.top, bottom = navHeight)
            locationDisabled.updatePadding(top = bars.top, bottom = navHeight)

            insets
        }

        view.findViewById<Button>(R.id.open_ble_settings_button).setOnClickListener {
            context?.let { BLEScanner.openBluetoothSettings(it) }
        }
        view.findViewById<Button>(R.id.open_location_settings_button)?.setOnClickListener {
            val settingsIntent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            try {
                startActivity(settingsIntent)
            } catch (_: Exception) {
                // Fallback to general settings if location page is unavailable
                try { startActivity(Intent(Settings.ACTION_SETTINGS)) } catch (_: Exception) {}
            }
        }

        view.findViewById<FloatingActionButton>(R.id.button_start_stop_scan).setOnClickListener {
            if (scanViewModel.scanFinished.value == true) {
                startBluetoothScanIfNeeded()
            } else {
                stopBluetoothScan()
            }
        }

        view.findViewById<FloatingActionButton>(R.id.button_sort_scan).setOnClickListener {
            showSortDialog()
        }
    }

    private fun showSortDialog() {
        val options = arrayOf(
            getString(R.string.scan_sorting_by_recently),
            getString(R.string.scan_sorting_by_name),
            getString(R.string.scan_sorting_by_rssi)
        )
        val currentIndex = when (scanViewModel.sortOrder.value) {
            ScanSortOrder.BY_APPEARANCE -> 0
            ScanSortOrder.BY_NAME -> 1
            ScanSortOrder.BY_SIGNAL_STRENGTH -> 2
            null -> 0
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.scan_sort_dialog_title)
            .setSingleChoiceItems(options, currentIndex) { dialog, which ->
                val order = when (which) {
                    0 -> ScanSortOrder.BY_APPEARANCE
                    1 -> ScanSortOrder.BY_NAME
                    2 -> ScanSortOrder.BY_SIGNAL_STRENGTH
                    else -> ScanSortOrder.BY_APPEARANCE
                }
                scanViewModel.setSortOrder(order)
                dialog.dismiss()
            }
            .show()
    }

    override fun onStart() {
        super.onStart()
        scanViewModel.startMonitoringSystemToggles()
        scanViewModel.refreshBluetoothState()
        scanViewModel.refreshLocationState()
    }

    override fun onStop() {
        super.onStop()
        scanViewModel.stopMonitoringSystemToggles()
    }

    private fun readyToScan(): Boolean {
        return scanViewModel.canScan.value == true
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result)
            if (SharedPrefs.showSamsungAndroid15BugNotification) {
                SharedPrefs.showSamsungAndroid15BugNotification = false
            }
            result?.let { scanResult ->
                val wrapper = scanViewModel.addScanResult(scanResult)
                // Enqueue for GATT subtype detection (when enabled)
                if (SharedPrefs.autoDetectDeviceTypes) {
                    if (DeviceSubTypeDetector.needsDetection(wrapper) && detectionAttempted.add(wrapper.uniqueIdentifier)) {
                        wrapper.detectionStatus = ScanResultWrapper.DetectionStatus.QUEUED
                        detectionQueue.trySend(wrapper)
                    }
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            Timber.e("BLE Scan failed. $errorCode")
            stopBluetoothScan()
            view?.let {
                Snackbar.make(it, R.string.ble_service_connection_error, Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun startBluetoothScanIfNeeded() {
        if (!readyToScan()) return

        if (!hasActiveScan) {
            createNewScanRecord()
            hasActiveScan = true

            // For connection to device Reset detection state so every new scan session starts fresh
            detectionAttempted.clear()
        }

        if (!BLEScanner.isScanning) {
            try {
                BLEScanner.startBluetoothScan(requireContext())
            } catch (e: InterruptedException) {
                Timber.e(e, "Caught InterruptedException")
            }
        }

        if (scanCallback !in BLEScanner.callbacks) {
            BLEScanner.registerCallback(scanCallback)
        }

        scanViewModel.scanFinished.value = false

        Handler(Looper.getMainLooper()).postDelayed({
            if (scanViewModel.isListEmpty.value == true) {
                stopBluetoothScan()
            }
        }, SCAN_DURATION)
    }

    private fun createNewScanRecord() {
        lifecycleScope.launch {
            val scanMode = getScanMode()
            scanId = scanRepository.insert(
                Scan(
                    startDate = LocalDateTime.now(),
                    isManual = true,
                    scanMode = scanMode
                )
            )
        }
    }

    private fun stopBluetoothScan() {
        BLEScanner.unregisterCallback(scanCallback)

        lifecycleScope.launch {
            val scan = scanRepository.scanWithId(scanId.toInt())
            if (scan != null) {
                val now = LocalDateTime.now()
                val deviceCount = (scanViewModel.bluetoothDeviceListHighRisk.value?.size ?: 0) +
                        (scanViewModel.bluetoothDeviceListLowRisk.value?.size ?: 0)
                scan.apply {
                    endDate = now
                    duration = ChronoUnit.SECONDS.between(startDate, now).toInt()
                    noDevicesFound = deviceCount
                }
                scanRepository.update(scan)
            }
        }

        scanViewModel.scanFinished.value = true
        hasActiveScan = false
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopBluetoothScan()
    }

    override fun onResume() {
        super.onResume()
        scanViewModel.refreshBluetoothState()
        scanViewModel.refreshLocationState()
        if (scanViewModel.scanFinished.value == false) {
            startBluetoothScanIfNeeded()
        }
    }

    override fun onPause() {
        super.onPause()
        if (scanViewModel.scanFinished.value == false) {
            stopBluetoothScan()
        }
    }

    private fun startDetectionQueueProcessor() {
        viewLifecycleOwner.lifecycleScope.launch {
            for (wrapper in detectionQueue) {
                if (!SharedPrefs.autoDetectDeviceTypes) continue
                try {
                    val deviceRepository = ATTrackingDetectionApplication.getCurrentApp()?.deviceRepository
                    if (deviceRepository != null) {
                        wrapper.detectionStatus = ScanResultWrapper.DetectionStatus.CONNECTING
                        refreshAdapterItem(wrapper.uniqueIdentifier)
                        DeviceSubTypeDetector.processDetection(wrapper, deviceRepository)
                        wrapper.detectionStatus = ScanResultWrapper.DetectionStatus.IDLE
                        refreshAdapterItem(wrapper.uniqueIdentifier)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // In case of error: simply set to Idle (aka no shimmering animation)
                    wrapper.detectionStatus = ScanResultWrapper.DetectionStatus.IDLE
                    refreshAdapterItem(wrapper.uniqueIdentifier)
                }
            }
        }
    }

    // Post update on main thread if device has new values due to GATT connection
    private fun refreshAdapterItem(uid: String) {
        Handler(Looper.getMainLooper()).post {
            try {
                val highPos = bluetoothDeviceAdapterHighRisk.currentList
                    .indexOfFirst { it.uniqueIdentifier == uid }
                if (highPos >= 0) bluetoothDeviceAdapterHighRisk.notifyItemChanged(highPos)

                val lowPos = bluetoothDeviceAdapterLowRisk.currentList
                    .indexOfFirst { it.uniqueIdentifier == uid }
                if (lowPos >= 0) bluetoothDeviceAdapterLowRisk.notifyItemChanged(lowPos)
            } catch (_: Exception) { /* ignore */ }
        }
    }

    companion object {
        private const val SCAN_DURATION = 60_000L
    }
}