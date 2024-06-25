package de.seemoo.at_tracking_detection.ui.scan

import android.animation.ObjectAnimator
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.animation.addListener
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.navArgs
import com.google.android.material.snackbar.Snackbar
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.database.models.device.BaseDevice.Companion.getBatteryState
import de.seemoo.at_tracking_detection.database.models.device.BaseDevice.Companion.getBatteryStateAsString
import de.seemoo.at_tracking_detection.database.models.device.BaseDevice.Companion.getConnectionState
import de.seemoo.at_tracking_detection.database.models.device.BaseDevice.Companion.getPublicKey
import de.seemoo.at_tracking_detection.database.models.device.ConnectionState
import de.seemoo.at_tracking_detection.database.models.device.DeviceManager
import de.seemoo.at_tracking_detection.database.models.device.DeviceType
import de.seemoo.at_tracking_detection.database.models.device.types.SamsungDevice
import de.seemoo.at_tracking_detection.database.models.device.types.SamsungDeviceType
import de.seemoo.at_tracking_detection.databinding.FragmentScanDistanceBinding
import de.seemoo.at_tracking_detection.util.Utility
import de.seemoo.at_tracking_detection.util.ble.BLEScanner
import kotlinx.coroutines.launch
import timber.log.Timber

class ScanDistanceFragment : Fragment() {
    private val viewModel: ScanDistanceViewModel by viewModels()
    private val safeArgs: ScanDistanceFragmentArgs by navArgs()

    private var deviceAddress: String? = null
    private var deviceType: DeviceType? = null
    private var latestWrappedScanResult: ScanResultWrapper? = null
    private var subType: SamsungDeviceType? = null

    private var oldAnimationValue = 0f
    private val animationDuration = 1000L

    private lateinit var binding: FragmentScanDistanceBinding

    private val scanCallback: ScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result)
            result?.let {scanResult ->
                val filteredIdentifier = safeArgs.deviceAddress

                if (filteredIdentifier == null) {
                    showSearchMessage()
                }

                if (getPublicKey(scanResult) == filteredIdentifier){
                    if (deviceType == null) {
                        deviceType = DeviceManager.getDeviceType(scanResult)
                    }

                    val connectionState = getConnectionState(scanResult, deviceType!!)
                    viewModel.connectionState.postValue(connectionState)
                    val connectionStateString = getConnectionStateExplanation(connectionState, deviceType!!)
                    viewModel.connectionStateString.postValue(connectionStateString)

                    val batteryState = getBatteryState(scanResult, deviceType!!)
                    val batteryStateString = getBatteryStateAsString(scanResult, deviceType!!)
                    viewModel.batteryStateString.postValue(batteryStateString)
                    viewModel.batteryState.postValue(batteryState)
                    val connectionQuality = Utility.dbmToPercent(scanResult.rssi).toFloat()
                    val displayedConnectionQuality = (connectionQuality * 100).toInt()
                    viewModel.connectionQuality.postValue(displayedConnectionQuality)

                    latestWrappedScanResult = ScanResultWrapper(scanResult)
                    binding.deviceTypeText.text = DeviceType.userReadableName(
                        latestWrappedScanResult!!
                    )

                    // setBattery(requireContext(), batteryState)
                    setHeight(connectionQuality)

                    if (viewModel.isFirstScanCallback.value as Boolean) {
                        viewModel.isFirstScanCallback.value = false
                        removeSearchMessage()
                    }
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
                ).show()
            }
        }
    }

    private fun removeSearchMessage() {
        binding.scanResultLoadingBar.visibility = View.GONE
        binding.searchingForDevice.visibility = View.GONE
        binding.connectionQuality.visibility = View.VISIBLE
        binding.deviceTypeLayout.visibility = View.VISIBLE
        binding.connectionStateLayout.visibility = View.VISIBLE
        binding.scanExplanationLayout.visibility = View.VISIBLE
        binding.deviceNotFound.visibility = View.GONE
    }

    private fun showSearchMessage() {
        binding.scanResultLoadingBar.visibility = View.VISIBLE
        binding.searchingForDevice.visibility = View.VISIBLE
        binding.connectionQuality.visibility = View.GONE
        binding.scanExplanationLayout.visibility = View.GONE
        binding.deviceTypeLayout.visibility = View.GONE
        binding.connectionStateLayout.visibility = View.GONE
        binding.deviceNotFound.visibility = View.GONE
    }

    private fun deviceNotFound() {
        binding.scanResultLoadingBar.visibility = View.GONE
        binding.searchingForDevice.visibility = View.GONE
        binding.connectionQuality.visibility = View.GONE
        binding.scanExplanationLayout.visibility = View.GONE
        binding.deviceTypeLayout.visibility = View.GONE
        binding.connectionStateLayout.visibility = View.GONE
        binding.deviceNotFound.visibility = View.VISIBLE

        setHeight(1f, 100L)
    }

    private fun setHeight(connectionQuality: Float, speed: Long = animationDuration) {
        val viewHeight = binding.backgroundBar.height
        val targetHeight: Float = connectionQuality * viewHeight * (-1) + viewHeight

        ObjectAnimator.ofFloat(
            binding.backgroundBar,
            "translationY",
            oldAnimationValue,
            targetHeight
        ).apply {
            cancel() // cancels any old animation
            duration = speed
            addListener(onEnd = {
                // only changes the value after the animation is done
                oldAnimationValue = targetHeight
            })
            start()
        }
    }

    private fun getConnectionStateExplanation(connectionState: ConnectionState, deviceType: DeviceType): String {
        return when (connectionState) {
            ConnectionState.OVERMATURE_OFFLINE -> when(deviceType) {
                DeviceType.SAMSUNG_DEVICE -> getString(R.string.connection_state_overmature_offline_explanation_samsung)
                DeviceType.CHIPOLO -> getString(R.string.connection_state_overmature_offline_explanation_chipolo)
                else -> getString(R.string.connection_state_overmature_offline_explanation)
            }
            ConnectionState.CONNECTED -> getString(R.string.connection_state_connected_explanation)
            ConnectionState.OFFLINE -> getString(R.string.connection_state_offline_explanation)
            ConnectionState.PREMATURE_OFFLINE -> when(deviceType) {
                DeviceType.CHIPOLO -> getString(R.string.connection_state_premature_offline_explanation_chipolo)
                else -> getString(R.string.connection_state_premature_offline_explanation)
            }
            ConnectionState.UNKNOWN -> getString(R.string.connection_state_unknown_explanation)
        }
    }

    private fun startBluetoothScan() {
        // Start a scan if the BLEScanner is not already running
        if (!BLEScanner.isScanning) {
            BLEScanner.startBluetoothScan(this.requireContext())
        }

        // Register the current fragment as a callback
        BLEScanner.registerCallback(this.scanCallback)

        // Show to the user that no devices have been found
        Handler(Looper.getMainLooper()).postDelayed({
            // Stop scanning if no device was detected
            if(viewModel.isFirstScanCallback.value as Boolean) {
                stopBluetoothScan()
                deviceNotFound()
            }
        }, SCAN_DURATION)
    }

    private fun stopBluetoothScan() {
        // We just unregister the callback, but keep the scanner running
        // until the app is closed / moved to background
        BLEScanner.unregisterCallback(this.scanCallback)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DataBindingUtil.inflate(
            inflater,
            R.layout.fragment_scan_distance,
            container,
            false
        )
        binding.lifecycleOwner = viewLifecycleOwner
        binding.vm = viewModel

        // This is called deviceAddress but contains the ID
        deviceAddress = safeArgs.deviceAddress
        viewModel.deviceAddress.postValue(deviceAddress)

        viewModel.isFirstScanCallback.postValue(true)
        showSearchMessage()

        startBluetoothScan()

        binding.performActionButton.visibility = if (deviceType == DeviceType.SAMSUNG_DEVICE) View.VISIBLE else View.GONE
        binding.performActionButton.setOnClickListener {
            performAction()
        }

        return binding.root
    }

    private fun performAction() {
        // Use CoroutineScope to call the suspend function
        if (deviceType == DeviceType.SAMSUNG_DEVICE && latestWrappedScanResult != null) {
            lifecycleScope.launch {
                subType = SamsungDevice.getSubType(latestWrappedScanResult!!)
                subType?.let {
                    binding.subType.visibility = View.VISIBLE
                    binding.performActionButton.visibility = View.GONE
                    viewModel.subType.postValue(SamsungDeviceType.subTypeToString(it))
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.isFirstScanCallback.postValue(true)
        showSearchMessage()
        startBluetoothScan()
    }

    override fun onPause() {
        super.onPause()
        showSearchMessage()
        stopBluetoothScan()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopBluetoothScan()
    }

    companion object {
        private const val SCAN_DURATION = 30_000L
    }

}