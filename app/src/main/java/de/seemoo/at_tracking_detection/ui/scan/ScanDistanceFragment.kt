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
import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.database.models.device.BaseDevice.Companion.getBatteryState
import de.seemoo.at_tracking_detection.database.models.device.BaseDevice.Companion.getBatteryStateAsString
import de.seemoo.at_tracking_detection.database.models.device.BaseDevice.Companion.getConnectionState
import de.seemoo.at_tracking_detection.database.models.device.BaseDevice.Companion.getPublicKey
import de.seemoo.at_tracking_detection.database.models.device.ConnectionState
import de.seemoo.at_tracking_detection.database.models.device.DeviceManager
import de.seemoo.at_tracking_detection.database.models.device.DeviceType
import de.seemoo.at_tracking_detection.database.models.device.types.AppleFindMy
import de.seemoo.at_tracking_detection.database.models.device.types.GoogleFindMyNetwork
import de.seemoo.at_tracking_detection.database.models.device.types.GoogleFindMyNetworkType
import de.seemoo.at_tracking_detection.database.models.device.types.PebbleBee
import de.seemoo.at_tracking_detection.database.models.device.types.SamsungFindMyMobile
import de.seemoo.at_tracking_detection.database.models.device.types.SamsungTracker
import de.seemoo.at_tracking_detection.database.models.device.types.SamsungTrackerType
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
    private var subTypeSamsung: SamsungTrackerType? = null
    private var subTypeGoogle: GoogleFindMyNetworkType? = null

//    sealed class SubType {
//        data class SamsungValue(val value: SamsungTrackerType) : SubType()
//        data class GoogleValue(val value: GoogleFindMyNetworkType) : SubType()
//    }

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
                    latestWrappedScanResult = ScanResultWrapper(scanResult)

                    if (deviceType == null) {
                        deviceType = DeviceManager.getDeviceType(scanResult)
                        determineDeviceTypeButtonVisible()
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

                    // setBattery(requireContext(), batteryState)
                    setHeight(connectionQuality)

                    if (viewModel.isFirstScanCallback.value as Boolean) {
                        viewModel.isFirstScanCallback.value = false

                        // TODO: add drawable
                        val samsungSubType: SamsungTrackerType? = subTypeSamsung ?: ScanFragment.samsungSubDeviceTypeMap[latestWrappedScanResult!!.uniqueIdentifier]
                        val deviceName = ScanFragment.deviceNameMap[latestWrappedScanResult!!.uniqueIdentifier]
                        if (samsungSubType != null && samsungSubType != SamsungTrackerType.UNKNOWN) {
                            binding.deviceTypeText.text = SamsungTrackerType.visibleStringFromSubtype(samsungSubType)
                        } else if (deviceName != null && deviceName != "") {
                            binding.deviceTypeText.text = deviceName
                        } else {
                            binding.deviceTypeText.text = DeviceType.userReadableName(
                                latestWrappedScanResult!!
                            )
                        }

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

    private fun determineDeviceTypeButtonVisible() {
        binding.performActionButton.visibility = if (deviceType == DeviceType.SAMSUNG_TRACKER) {
            val samsungSubType: SamsungTrackerType? = subTypeSamsung ?: ScanFragment.samsungSubDeviceTypeMap[latestWrappedScanResult!!.uniqueIdentifier]
            if (samsungSubType == null || samsungSubType == SamsungTrackerType.UNKNOWN) {
                View.VISIBLE
            } else {
                View.GONE
            }
        } else if (deviceType in DeviceManager.appleDevicesWithInfoService) {
            val deviceName = ScanFragment.deviceNameMap[latestWrappedScanResult!!.uniqueIdentifier]
            if (deviceName == null || deviceName == "" || deviceName == ATTrackingDetectionApplication.getAppContext().resources.getString(R.string.apple_find_my_default_name)) {
                View.VISIBLE
            } else {
                View.GONE
            }
        } else if (deviceType == DeviceType.PEBBLEBEE) {
            val deviceName = ScanFragment.deviceNameMap[latestWrappedScanResult!!.uniqueIdentifier]
            if (deviceName == null || deviceName == "" || deviceName == ATTrackingDetectionApplication.getAppContext().resources.getString(R.string.pebblebee_default_name)) {
                View.VISIBLE
            } else {
                View.GONE
            }
        } else if (deviceType == DeviceType.SAMSUNG_FIND_MY_MOBILE) {
            val deviceName = ScanFragment.deviceNameMap[latestWrappedScanResult!!.uniqueIdentifier]
            if (deviceName == null || deviceName == "" || deviceName == ATTrackingDetectionApplication.getAppContext().resources.getString(R.string.samsung_find_my_mobile_name)) {
                View.VISIBLE
            } else {
                View.GONE
            }
        } else if (deviceType == DeviceType.GOOGLE_FIND_MY_NETWORK) {
            val deviceName = ScanFragment.deviceNameMap[latestWrappedScanResult!!.uniqueIdentifier]

            // Also make Owner Information Button Visible
            binding.retrieveOwnerInformationButton.visibility = View.VISIBLE

            if (deviceName == null || deviceName == "") {
                View.VISIBLE
            } else {
                View.GONE
            }
        } else {
            View.GONE
        }
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
                DeviceType.SAMSUNG_TRACKER -> getString(R.string.connection_state_overmature_offline_explanation_samsung)
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

        binding.performActionButton.setOnClickListener {
            determineSubType()
        }

        binding.retrieveOwnerInformationButton.setOnClickListener {
            lifecycleScope.launch {
                val ownerInformationURL = GoogleFindMyNetwork.getOwnerInformationURL(latestWrappedScanResult!!)
                if (ownerInformationURL != null) {
                    context?.let {
                        assumedContext -> Utility.openBrowser(assumedContext, ownerInformationURL.toString(), binding.root)
                    }
                } else {
                    Snackbar.make(
                        binding.root,
                        R.string.retrieve_owner_information_failed,
                        Snackbar.LENGTH_LONG
                    ).show()
                }
            }
        }

        return binding.root
    }

    private fun determineSubType() {
        if (deviceType == DeviceType.SAMSUNG_TRACKER && latestWrappedScanResult != null) {
            binding.performActionButton.visibility = View.GONE
            binding.deviceTypeText.visibility = View.GONE
            binding.progressCircular.visibility = View.VISIBLE
            lifecycleScope.launch {
                subTypeSamsung = SamsungTracker.getSubType(latestWrappedScanResult!!)
                ScanFragment.samsungSubDeviceTypeMap[latestWrappedScanResult!!.uniqueIdentifier] = subTypeSamsung!!
                subTypeSamsung?.let { samsungDeviceType ->
                    val deviceRepository = ATTrackingDetectionApplication.getCurrentApp().deviceRepository
                    val device = deviceRepository.getDevice(latestWrappedScanResult!!.uniqueIdentifier)

                    if (device != null) {
                        device.subDeviceType = SamsungTrackerType.subTypeToString(samsungDeviceType)
                        deviceRepository.update(device)
                    }

                    if (samsungDeviceType == SamsungTrackerType.UNKNOWN) {
                        Snackbar.make(
                            binding.root,
                            R.string.device_determine_failed,
                            Snackbar.LENGTH_LONG
                        ).show()
                        binding.performActionButton.visibility = View.VISIBLE
                    } else {
                        viewModel.displayName.postValue(
                            SamsungTrackerType.visibleStringFromSubtype(
                                samsungDeviceType
                            )
                        )
                    }
                    binding.progressCircular.visibility = View.GONE
                    binding.deviceTypeText.visibility = View.VISIBLE
                }
            }
        } else if (deviceType == DeviceType.GOOGLE_FIND_MY_NETWORK && latestWrappedScanResult != null) {
            binding.performActionButton.visibility = View.GONE
            binding.deviceTypeText.visibility = View.GONE
            binding.progressCircular.visibility = View.VISIBLE

            lifecycleScope.launch {
                // Detect Subtype
                subTypeGoogle = GoogleFindMyNetwork.getSubType(latestWrappedScanResult!!)
                ScanFragment.googleSubDeviceTypeMap[latestWrappedScanResult!!.uniqueIdentifier] = subTypeGoogle!!
                val deviceRepository = ATTrackingDetectionApplication.getCurrentApp().deviceRepository
                val device = deviceRepository.getDevice(latestWrappedScanResult!!.uniqueIdentifier)

                // Retrieve Device Name
                val deviceName = GoogleFindMyNetwork.getDeviceName(latestWrappedScanResult!!)
                if (deviceName != "" && device != null) {
                    ScanFragment.deviceNameMap[latestWrappedScanResult!!.uniqueIdentifier] = deviceName
                    device.name = deviceName
                    deviceRepository.update(device)
                }

                if (device != null) {
                    device.subDeviceType = GoogleFindMyNetworkType.subTypeToString(subTypeGoogle!!)
                    deviceRepository.update(device)
                }

                if (subTypeGoogle == GoogleFindMyNetworkType.UNKNOWN) {
                    Snackbar.make(
                        binding.root,
                        R.string.device_determine_failed,
                        Snackbar.LENGTH_LONG
                    ).show()
                    binding.performActionButton.visibility = View.VISIBLE
                } else {
                    viewModel.displayName.postValue(
                        GoogleFindMyNetworkType.visibleStringFromSubtype(
                            subTypeGoogle!!
                        )
                    )
                }
                binding.progressCircular.visibility = View.GONE
                binding.deviceTypeText.visibility = View.VISIBLE
            }
        } else if (deviceType in DeviceManager.appleDevicesWithInfoService && latestWrappedScanResult != null) {
            binding.performActionButton.visibility = View.GONE
            binding.deviceTypeText.visibility = View.GONE
            binding.progressCircular.visibility = View.VISIBLE
            lifecycleScope.launch {
                val findMyDefaultString = ATTrackingDetectionApplication.getAppContext().resources.getString(R.string.apple_find_my_default_name)
                val deviceName = AppleFindMy.getSubTypeName(latestWrappedScanResult!!)
                if (deviceName != findMyDefaultString) {
                    ScanFragment.deviceNameMap[latestWrappedScanResult!!.uniqueIdentifier] = deviceName
                }

                val deviceRepository = ATTrackingDetectionApplication.getCurrentApp().deviceRepository
                val device = deviceRepository.getDevice(latestWrappedScanResult!!.uniqueIdentifier)

                if (device != null && deviceName != findMyDefaultString) {
                    device.name = deviceName
                    deviceRepository.update(device)
                }

                if (deviceName == findMyDefaultString) {
                    Snackbar.make(
                        binding.root,
                        R.string.device_determine_failed,
                        Snackbar.LENGTH_LONG
                    ).show()
                    binding.performActionButton.visibility = View.VISIBLE
                } else {
                    viewModel.displayName.postValue(deviceName)
                }

                binding.progressCircular.visibility = View.GONE
                binding.deviceTypeText.visibility = View.VISIBLE
            }
        } else if (deviceType == DeviceType.PEBBLEBEE && latestWrappedScanResult != null) {
            binding.performActionButton.visibility = View.GONE
            binding.deviceTypeText.visibility = View.GONE
            binding.progressCircular.visibility = View.VISIBLE
            lifecycleScope.launch {
                val pebblebeeDefaultString = ATTrackingDetectionApplication.getAppContext().resources.getString(R.string.pebblebee_default_name)
                val deviceName = PebbleBee.getSubTypeName(latestWrappedScanResult!!)
                if (deviceName != pebblebeeDefaultString) {
                    ScanFragment.deviceNameMap[latestWrappedScanResult!!.uniqueIdentifier] = deviceName
                }

                val deviceRepository = ATTrackingDetectionApplication.getCurrentApp().deviceRepository
                val device = deviceRepository.getDevice(latestWrappedScanResult!!.uniqueIdentifier)

                if (device != null && deviceName != pebblebeeDefaultString) {
                    device.name = deviceName
                    deviceRepository.update(device)
                }

                if (deviceName == pebblebeeDefaultString) {
                    Snackbar.make(
                        binding.root,
                        R.string.device_determine_failed,
                        Snackbar.LENGTH_LONG
                    ).show()
                    binding.performActionButton.visibility = View.VISIBLE
                } else {
                    viewModel.displayName.postValue(deviceName)
                }

                binding.progressCircular.visibility = View.GONE
                binding.deviceTypeText.visibility = View.VISIBLE
            }
        } else if (deviceType == DeviceType.SAMSUNG_FIND_MY_MOBILE && latestWrappedScanResult != null) {
            binding.performActionButton.visibility = View.GONE
            binding.deviceTypeText.visibility = View.GONE
            binding.progressCircular.visibility = View.VISIBLE
            lifecycleScope.launch {
                val samsungFindMyMobileDefaultString = ATTrackingDetectionApplication.getAppContext().resources.getString(R.string.samsung_find_my_mobile_name)
                val deviceName = SamsungFindMyMobile.getSubTypeName(latestWrappedScanResult!!)
                if (deviceName != samsungFindMyMobileDefaultString) {
                    ScanFragment.deviceNameMap[latestWrappedScanResult!!.uniqueIdentifier] = deviceName
                }

                val deviceRepository = ATTrackingDetectionApplication.getCurrentApp().deviceRepository
                val device = deviceRepository.getDevice(latestWrappedScanResult!!.uniqueIdentifier)

                if (device != null) {
                    device.name = deviceName
                    deviceRepository.update(device)
                }

                viewModel.displayName.postValue(deviceName)

                binding.progressCircular.visibility = View.GONE
                binding.deviceTypeText.visibility = View.VISIBLE
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.isFirstScanCallback.postValue(true)
        determineDeviceTypeButtonVisible()
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