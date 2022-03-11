package de.seemoo.at_tracking_detection.ui.scan

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.database.models.device.DeviceManager
import de.seemoo.at_tracking_detection.databinding.FragmentScanBinding
import de.seemoo.at_tracking_detection.util.Util
import de.seemoo.at_tracking_detection.util.ble.BLEScanCallback
import timber.log.Timber

@AndroidEntryPoint
class ScanFragment : Fragment() {

    private val scanViewModel: ScanViewModel by viewModels()

    private var bluetoothManager: BluetoothManager? = null
    private var isScanning = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
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
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        startBluetoothScan()
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
        if (isScanning) { return }
        bluetoothManager =
            ATTrackingDetectionApplication.getAppContext()
                .getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val scanSettings =
            ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()

        bluetoothManager?.let {
            val isBluetoothEnabled = it.adapter.state == BluetoothAdapter.STATE_ON
            val hasScanPermission =
                Build.VERSION.SDK_INT < Build.VERSION_CODES.S || Util.checkAndRequestPermission(
                    Manifest.permission.BLUETOOTH_SCAN
                )
            if (isBluetoothEnabled && hasScanPermission) {
                BLEScanCallback.startScanning(it.adapter.bluetoothLeScanner, DeviceManager.scanFilter, scanSettings, scanCallback)
                isScanning = true

                Handler(Looper.getMainLooper()).postDelayed({
                    // Stop scanning if no device was detected
                    if (scanViewModel.isListEmpty.value == true) {
                        scanViewModel.scanFinished.postValue(true)
                        stopBluetoothScan()
                    }
                }, SCAN_DURATION)
            }
        }
    }

    private fun stopBluetoothScan() {
        bluetoothManager?.let {
            if (it.adapter.state == BluetoothAdapter.STATE_ON) {
                BLEScanCallback.stopScanning(it.adapter.bluetoothLeScanner)
                isScanning = false
            }
        }
    }

    override fun onResume() {
        super.onResume()
        startBluetoothScan()
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
        private const val SCAN_DURATION = 10000L
    }
}