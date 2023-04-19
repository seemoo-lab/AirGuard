package de.seemoo.at_tracking_detection.ui.scan

import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.navArgs
import com.google.android.material.snackbar.Snackbar
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.database.models.device.BaseDevice
import de.seemoo.at_tracking_detection.util.ble.BLEScanner
import de.seemoo.at_tracking_detection.database.models.device.types.SamsungDevice.Companion.getPublicKey
import de.seemoo.at_tracking_detection.databinding.FragmentScanBinding
import de.seemoo.at_tracking_detection.databinding.FragmentScanDistanceBinding
import timber.log.Timber

class ScanDistanceFragment : Fragment() {
    private val scanDistanceViewModel: ScanDistanceViewModel by viewModels()

    // TODO: does not work because not called via Navigation??? val safeArgs: ScanDistanceFragmentArgs by navArgs()

    private val scanCallback: ScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result)
            result?.let {
                // TODO: we need here the public key from the previous fragment
                val publicKey = "" // TODO: from navArgs???

                if (getPublicKey(it) == publicKey){
                    scanDistanceViewModel.setScanResult(it)
                }

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

        /* TODO: modify or remove
        // Show to the user that no devices have been found
        Handler(Looper.getMainLooper()).postDelayed({
            // Stop scanning if no device was detected
            if (scanViewModel.isListEmpty.value == true) {
                scanViewModel.scanFinished.postValue(true)
                stopBluetoothScan()
            }
        }, ScanFragment.SCAN_DURATION)

         */
    }

    private fun stopBluetoothScan() {
        // We just unregister the callback, but keep the scanner running
        // until the app is closed / moved to background
        BLEScanner.unregisterCallback(this.scanCallback)
    }

    /* TODO: check if this is correct
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val binding: FragmentScanDistanceBinding = DataBindingUtil.inflate(inflater, R.layout.fragment_scan_distance, container, false)
        val bluetoothDeviceAdapter = BluetoothDeviceAdapter(childFragmentManager) // TODO: check: should this be the same Adapter as in ScanFragment?

        binding.adapter = bluetoothDeviceAdapter
        binding.lifecycleOwner = viewLifecycleOwner
        binding.vm = scanDistanceViewModel

        scanDistanceViewModel.bluetoothDevice.observe(viewLifecycleOwner) {
            // TODO: definitily change this
            bluetoothDeviceAdapter.submitList(it)
            // Ugly workaround because i don't know why this adapter only displays items after a screen wake up...
            bluetoothDeviceAdapter.notifyDataSetChanged()
        }

        return inflater.inflate(R.layout.fragment_scan_distance, container, false)
    }

     */

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
        private const val SCAN_DURATION = 15000L
    }

}