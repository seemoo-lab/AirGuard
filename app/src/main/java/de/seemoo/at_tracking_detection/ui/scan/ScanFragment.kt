package de.seemoo.at_tracking_detection.ui.scan

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.databinding.FragmentScanBinding
import de.seemoo.at_tracking_detection.util.Util

@AndroidEntryPoint
class ScanFragment : Fragment() {

    private val scanViewModel: ScanViewModel by viewModels()

    private var bluetoothLeScanner: BluetoothLeScanner? = null

    private var bluetoothManager: BluetoothManager? = null

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
    }

    private fun startBluetoothScan() {
        bluetoothManager =
            ATTrackingDetectionApplication.getAppContext()
                .getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val scanSettings = Util.buildScanSettings(ScanSettings.SCAN_MODE_LOW_LATENCY)
        bluetoothLeScanner = bluetoothManager?.adapter?.bluetoothLeScanner
        bluetoothLeScanner?.startScan(Util.bleScanFilter, scanSettings, scanCallback)

        Handler(Looper.getMainLooper()).postDelayed({
            // Stop scanning if no device was detected
            if (scanViewModel.isListEmpty.value == true) {
                scanViewModel.scanFinished.postValue(true)
                stopBluetoothScan()
            }
        }, SCAN_DURATION)
    }

    private fun stopBluetoothScan() {
        if (bluetoothManager?.adapter?.state == BluetoothAdapter.STATE_ON) {
            bluetoothLeScanner?.stopScan(scanCallback)
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