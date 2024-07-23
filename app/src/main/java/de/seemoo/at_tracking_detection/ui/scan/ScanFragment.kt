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
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.database.models.device.types.SamsungDeviceType
import de.seemoo.at_tracking_detection.databinding.FragmentScanBinding
import de.seemoo.at_tracking_detection.util.ble.BLEScanner
import timber.log.Timber

@AndroidEntryPoint
class ScanFragment : Fragment() {
    private val scanViewModel: ScanViewModel by viewModels()

    private val bluetoothDeviceAdapterHighRisk = BluetoothDeviceAdapter()
    private val bluetoothDeviceAdapterLowRisk = BluetoothDeviceAdapter()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding: FragmentScanBinding =
            DataBindingUtil.inflate(inflater, R.layout.fragment_scan, container, false)

        binding.adapterHighRisk = bluetoothDeviceAdapterHighRisk
        binding.adapterLowRisk = bluetoothDeviceAdapterLowRisk
        binding.lifecycleOwner = viewLifecycleOwner
        binding.vm = scanViewModel

        scanViewModel.bluetoothDeviceListHighRisk.observe(viewLifecycleOwner) {newList ->
            bluetoothDeviceAdapterHighRisk.submitList(newList)
        }
        scanViewModel.bluetoothDeviceListLowRisk.observe(viewLifecycleOwner) {newList ->
            bluetoothDeviceAdapterLowRisk.submitList(newList)
        }

        scanViewModel.scanFinished.observe(viewLifecycleOwner) {
            if (it) {
                binding.buttonStartStopScan.setImageResource(R.drawable.ic_baseline_play_arrow_24)
            } else {
                binding.buttonStartStopScan.setImageResource(R.drawable.ic_baseline_stop_24)
            }
        }

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        startBluetoothScan()

        val bluetoothButton = view.findViewById<Button>(R.id.open_ble_settings_button)
        bluetoothButton.setOnClickListener {
            context?.let { BLEScanner.openBluetoothSettings(it) }
        }

        scanViewModel.scanFinished.observe(viewLifecycleOwner) {scanFinished ->
            if (scanFinished) {
                stopBluetoothScan()
            } else {
                startBluetoothScan()
            }
        }

        val startStopButton = view.findViewById<FloatingActionButton>(R.id.button_start_stop_scan)
        startStopButton.setOnClickListener {
            if (scanViewModel.scanFinished.value == true) {
                scanViewModel.scanFinished.postValue(false)
            } else {
                scanViewModel.scanFinished.postValue(true)
            }
        }

        val infoButton = view.findViewById<ImageButton>(R.id.info_button)
        infoButton.setOnClickListener {
            toggleInfoLayoutVisibility(view)
        }
    }

    override fun onStart() {
        super.onStart()
        scanViewModel.bluetoothEnabled.postValue(BLEScanner.isBluetoothOn())
    }

    private fun toggleInfoLayoutVisibility(view: View) {
        val infoLayout = view.findViewById<LinearLayout>(R.id.info_layout)
        infoLayout.visibility = if (infoLayout.visibility == View.VISIBLE) View.GONE else View.VISIBLE
    }

    private val scanCallback: ScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result)
            result?.let {
                scanViewModel.addScanResult(it)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            Timber.e("BLE Scan failed. $errorCode")
            stopBluetoothScan()
            view?.let {
                Snackbar.make(
                    it,
                    R.string.ble_service_connection_error,
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun startBluetoothScan() {
        // Start a scan if the BLEScanner is not already running
        if (!BLEScanner.isScanning) {
            BLEScanner.startBluetoothScan(this.requireContext())
        }

        // Register the current fragment as a callback
        if (this.scanCallback !in BLEScanner.callbacks) {
            BLEScanner.registerCallback(this.scanCallback)
        }
        scanViewModel.scanFinished.postValue(false)

        // Show to the user that no devices have been found
        Handler(Looper.getMainLooper()).postDelayed({
            // Stop scanning if no device was detected
            if (scanViewModel.isListEmpty.value == true) {
                scanViewModel.scanFinished.postValue(true)
                stopBluetoothScan()
            }
        }, SCAN_DURATION)
    }

    private fun stopBluetoothScan() {
        // We just unregister the callback, but keep the scanner running
        // until the app is closed / moved to background
        BLEScanner.unregisterCallback(this.scanCallback)
        scanViewModel.scanFinished.postValue(true)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopBluetoothScan()
    }

    override fun onResume() {
        super.onResume()
        if (scanViewModel.scanFinished.value == false) {
            startBluetoothScan()
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
        val samsungSubDeviceTypeMap: MutableMap<String, SamsungDeviceType> = HashMap()
        val deviceNameMap: MutableMap<String, String> = HashMap()
    }
}