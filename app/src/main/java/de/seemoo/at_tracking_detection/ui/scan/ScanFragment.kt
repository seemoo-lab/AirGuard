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
import android.widget.TextView
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.databinding.FragmentScanBinding
import de.seemoo.at_tracking_detection.util.ble.BLEScanner
import timber.log.Timber

@AndroidEntryPoint
class ScanFragment : Fragment() {

    private val scanViewModel: ScanViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding: FragmentScanBinding =
            DataBindingUtil.inflate(inflater, R.layout.fragment_scan, container, false)
        val bluetoothDeviceAdapter = BluetoothDeviceAdapter(childFragmentManager)

        binding.adapter = bluetoothDeviceAdapter
        binding.lifecycleOwner = viewLifecycleOwner
        binding.vm = scanViewModel

        scanViewModel.bluetoothDeviceList.observe(viewLifecycleOwner) {
            bluetoothDeviceAdapter.submitList(it)
            // Ugly workaround because i don't know why this adapter only displays items after a screen wake up...
            bluetoothDeviceAdapter.notifyDataSetChanged()
        }

        scanViewModel.scanFinished.observe(viewLifecycleOwner) {
            if (it) {
                binding.buttonStartStopScan.setText(R.string.scan_start)
            } else {
                binding.buttonStartStopScan.setText(R.string.scan_stop)
            }
        }

        scanViewModel.sortingOrder.observe(viewLifecycleOwner) {
            val bluetoothDeviceListValue = scanViewModel.bluetoothDeviceList.value ?: return@observe
            scanViewModel.sortResults(bluetoothDeviceListValue)
            scanViewModel.bluetoothDeviceList.postValue(bluetoothDeviceListValue)
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

        val startStopButton = view.findViewById<Button>(R.id.button_start_stop_scan)
        startStopButton.setOnClickListener {
            if (scanViewModel.scanFinished.value == true) {
                startBluetoothScan()
            } else {
                stopBluetoothScan()
            }
        }

        val sortBySignalStrength = view.findViewById<TextView>(R.id.sort_option_signal_strength)
        val sortByDetectionOrder = view.findViewById<TextView>(R.id.sort_option_order_detection)
        val sortByAddress = view.findViewById<TextView>(R.id.sort_option_address)
        sortBySignalStrength.setOnClickListener {
            scanViewModel.sortingOrder.postValue(SortingOrder.SIGNAL_STRENGTH)
        }
        sortByDetectionOrder.setOnClickListener {
            scanViewModel.sortingOrder.postValue(SortingOrder.DETECTION_ORDER)
        }
        sortByAddress.setOnClickListener {
            scanViewModel.sortingOrder.postValue(SortingOrder.ADDRESS)
        }
    }

    override fun onStart() {
        super.onStart()
        scanViewModel.bluetoothEnabled.postValue(BLEScanner.isBluetoothOn())
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
                )
            }
        }
    }

    private fun startBluetoothScan() {
        // Start a scan if the BLEScanner is not already running
        if (!BLEScanner.isScanning) {
            BLEScanner.startBluetoothScan(this.requireContext())
        }

        // Register the current fragment as a callback
        BLEScanner.registerCallback(this.scanCallback)
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

    override fun onResume() {
        super.onResume()
        if (scanViewModel.scanFinished.value == false) {
            startBluetoothScan()
        }
    }

    override fun onPause() {
        super.onPause()
        stopBluetoothScan()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopBluetoothScan()
    }

    companion object {
        private const val SCAN_DURATION = 60_000L
    }
}