package de.seemoo.at_tracking_detection.ui.scan

import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.core.view.isVisible
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.database.models.Scan
import de.seemoo.at_tracking_detection.database.models.device.types.GoogleFindMyNetworkType
import de.seemoo.at_tracking_detection.database.models.device.types.SamsungTrackerType
import de.seemoo.at_tracking_detection.database.repository.ScanRepository
import de.seemoo.at_tracking_detection.databinding.FragmentScanBinding
import de.seemoo.at_tracking_detection.detection.BackgroundBluetoothScanner.getScanMode
import de.seemoo.at_tracking_detection.util.SharedPrefs
import de.seemoo.at_tracking_detection.util.ble.BLEScanner
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

@AndroidEntryPoint
class ScanFragment : Fragment() {
    private val scanViewModel: ScanViewModel by viewModels()
    private val bluetoothDeviceAdapterHighRisk = BluetoothDeviceAdapter()
    private val bluetoothDeviceAdapterLowRisk = BluetoothDeviceAdapter()
    private val scanRepository: ScanRepository
        get() = ATTrackingDetectionApplication.getCurrentApp().scanRepository
    private var scanId: Long = 0
    private var hasActiveScan = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding: FragmentScanBinding =
            DataBindingUtil.inflate(inflater, R.layout.fragment_scan, container, false)

        binding.apply {
            adapterHighRisk = bluetoothDeviceAdapterHighRisk
            adapterLowRisk = bluetoothDeviceAdapterLowRisk
            lifecycleOwner = viewLifecycleOwner
            vm = scanViewModel
        }

        scanViewModel.apply {
            bluetoothDeviceListHighRisk.observe(viewLifecycleOwner) { newList ->
                bluetoothDeviceAdapterHighRisk.submitList(newList)
            }
            bluetoothDeviceListLowRisk.observe(viewLifecycleOwner) { newList ->
                bluetoothDeviceAdapterLowRisk.submitList(newList)
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

        view.findViewById<Button>(R.id.open_ble_settings_button).setOnClickListener {
            context?.let { BLEScanner.openBluetoothSettings(it) }
        }

        view.findViewById<FloatingActionButton>(R.id.button_start_stop_scan).setOnClickListener {
            if (scanViewModel.scanFinished.value == true) {
                startBluetoothScanIfNeeded()
            } else {
                stopBluetoothScan()
            }
        }

        view.findViewById<ImageButton>(R.id.info_button).setOnClickListener {
            toggleInfoLayoutVisibility(view)
        }
    }

    override fun onStart() {
        super.onStart()
        scanViewModel.startMonitoringSystemToggles()
        scanViewModel.bluetoothEnabled.postValue(BLEScanner.isBluetoothOn())
    }

    override fun onStop() {
        super.onStop()
        scanViewModel.stopMonitoringSystemToggles()
    }

    private fun readyToScan(): Boolean {
        val btOn = scanViewModel.bluetoothEnabled.value == true
        val locOn = scanViewModel.locationEnabled.value == true
        return btOn && locOn
    }

    private fun toggleInfoLayoutVisibility(view: View) {
        val infoLayout = view.findViewById<LinearLayout>(R.id.info_layout)

        val duration = 200L
        val density = view.context.resources.displayMetrics.density
        val slidePx = (-10 * density)

        if (infoLayout.isVisible) {
            infoLayout.animate()
                .alpha(0f)
                .scaleX(0.95f)
                .scaleY(0.95f)
                .translationY(slidePx)
                .setDuration(duration)
                .withEndAction {
                    infoLayout.visibility = View.GONE
                    // reset properties for next show
                    infoLayout.alpha = 1f
                    infoLayout.scaleX = 1f
                    infoLayout.scaleY = 1f
                    infoLayout.translationY = 0f
                }
                .start()
        } else {
            infoLayout.alpha = 0f
            infoLayout.scaleX = 0.95f
            infoLayout.scaleY = 0.95f
            infoLayout.translationY = slidePx
            infoLayout.visibility = View.VISIBLE

            infoLayout.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .translationY(0f)
                .setDuration(duration)
                .start()
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result)
            SharedPrefs.showSamsungAndroid15BugNotification = false
            result?.let { scanViewModel.addScanResult(it) }
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

        scanViewModel.scanFinished.postValue(false)

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
                scan.apply {
                    endDate = now
                    duration = ChronoUnit.SECONDS.between(startDate, now).toInt()
                    noDevicesFound = (scanViewModel.bluetoothDeviceListHighRisk.value?.size ?: 0) +
                            (scanViewModel.bluetoothDeviceListLowRisk.value?.size ?: 0)
                }
                scanRepository.update(scan)
            }
        }

        scanViewModel.scanFinished.postValue(true)
        hasActiveScan = false
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopBluetoothScan()
    }

    override fun onResume() {
        super.onResume()
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

    companion object {
        private const val SCAN_DURATION = 60_000L
        val samsungSubDeviceTypeMap: MutableMap<String, SamsungTrackerType> = HashMap()
        val googleSubDeviceTypeMap: MutableMap<String, GoogleFindMyNetworkType> = HashMap()
        val googleExactTagDeterminedMap: MutableMap<String, Boolean> = HashMap()
        val deviceNameMap: MutableMap<String, String> = HashMap()
    }
}