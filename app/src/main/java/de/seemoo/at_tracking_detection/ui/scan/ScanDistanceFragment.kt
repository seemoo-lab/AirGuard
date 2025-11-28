package de.seemoo.at_tracking_detection.ui.scan

import android.animation.ObjectAnimator
import android.app.AlertDialog
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.animation.addListener
import androidx.core.content.ContextCompat
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
import de.seemoo.at_tracking_detection.database.models.device.BaseDevice.Companion.getUniqueIdentifier
import de.seemoo.at_tracking_detection.database.models.device.BatteryState
import de.seemoo.at_tracking_detection.database.models.device.ConnectionState
import de.seemoo.at_tracking_detection.database.models.device.DeviceManager
import de.seemoo.at_tracking_detection.database.models.device.DeviceType
import de.seemoo.at_tracking_detection.database.models.device.types.AppleFindMy
import de.seemoo.at_tracking_detection.database.models.device.types.GoogleFindMyNetwork
import de.seemoo.at_tracking_detection.database.models.device.types.GoogleFindMyNetworkManufacturer
import de.seemoo.at_tracking_detection.database.models.device.types.GoogleFindMyNetworkType
import de.seemoo.at_tracking_detection.database.models.device.types.PebbleBee
import de.seemoo.at_tracking_detection.database.models.device.types.SamsungFindMyMobile
import de.seemoo.at_tracking_detection.database.models.device.types.SamsungTracker
import de.seemoo.at_tracking_detection.database.models.device.types.SamsungTrackerType
import de.seemoo.at_tracking_detection.databinding.FragmentScanDistanceBinding
import de.seemoo.at_tracking_detection.util.SharedPrefs
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

                if (getUniqueIdentifier(scanResult) == filteredIdentifier){
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

                    setBattery(batteryState)
                    setHeight(connectionQuality)

                    if (viewModel.isFirstScanCallback.value as Boolean) {
                        viewModel.isFirstScanCallback.value = false

                        // TODO: add drawable
                        val samsungSubType: SamsungTrackerType? = subTypeSamsung ?: ScanFragment.samsungSubDeviceTypeMap[latestWrappedScanResult!!.uniqueIdentifier]
                        val googleSubType: GoogleFindMyNetworkType? = subTypeGoogle ?: ScanFragment.googleSubDeviceTypeMap[latestWrappedScanResult!!.uniqueIdentifier]
                        val deviceName = ScanFragment.deviceNameMap[latestWrappedScanResult!!.uniqueIdentifier]
                        val deviceRepository = ATTrackingDetectionApplication.getCurrentApp().deviceRepository
                        val device = deviceRepository.getDevice(latestWrappedScanResult!!.uniqueIdentifier)
                        val deviceNameFromDB = device?.name

                        if (device?.deviceType == DeviceType.GOOGLE_FIND_MY_NETWORK && googleSubType == GoogleFindMyNetworkType.TAG && deviceNameFromDB != null && deviceNameFromDB != "") {
                            defineRetrieveOwnerOnClickBehaviour(deviceNameFromDB)
                        }

                        if (samsungSubType != null && samsungSubType != SamsungTrackerType.UNKNOWN) {
                            Timber.d("Display Name - Samsung Subtype: $samsungSubType")
                            viewModel.displayName.postValue(SamsungTrackerType.visibleStringFromSubtype(samsungSubType))
                        } else if (deviceName != null && deviceName != "") {
                            Timber.d("Display Name - Device Name: $deviceName")
                            binding.deviceTypeText.text = deviceName
                        } else if (deviceNameFromDB != null && deviceNameFromDB != "") {
                            Timber.d("Display Name - Device Name from DB: $deviceNameFromDB")
                            binding.deviceTypeText.text = deviceNameFromDB
                        } else if (googleSubType != null) {
                            Timber.d("Display Name - Google Subtype: $googleSubType")
                            viewModel.displayName.postValue(GoogleFindMyNetworkType.visibleStringFromSubtype(googleSubType))
                        } else {
                            Timber.d("Display Name - Default")
                            binding.deviceTypeText.text = DeviceType.userReadableNameDefault(
                                latestWrappedScanResult!!.deviceType
                            )
                        }

                        updateDeviceIcon()
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
        binding.deviceIcon.visibility = View.VISIBLE
        binding.batteryLayout.visibility = if (SharedPrefs.advancedMode) View.VISIBLE else View.GONE
        binding.deviceTypeLayout.visibility = View.VISIBLE
        binding.connectionStateLayout.visibility = View.VISIBLE
        binding.scanExplanationLayout.visibility = View.VISIBLE
        binding.deviceNotFound.visibility = View.GONE
    }

    private fun showSearchMessage() {
        binding.scanResultLoadingBar.visibility = View.VISIBLE
        binding.searchingForDevice.visibility = View.VISIBLE
        binding.connectionQuality.visibility = View.GONE
        binding.deviceIcon.visibility = View.GONE
        binding.batteryLayout.visibility = View.GONE
        binding.scanExplanationLayout.visibility = View.GONE
        binding.deviceTypeLayout.visibility = View.GONE
        binding.connectionStateLayout.visibility = View.GONE
        binding.deviceNotFound.visibility = View.GONE
    }

    private fun deviceNotFound() {
        binding.scanResultLoadingBar.visibility = View.GONE
        binding.searchingForDevice.visibility = View.GONE
        binding.connectionQuality.visibility = View.GONE
        binding.deviceIcon.visibility = View.GONE
        binding.batteryLayout.visibility = View.GONE
        binding.scanExplanationLayout.visibility = View.GONE
        binding.deviceTypeLayout.visibility = View.GONE
        binding.connectionStateLayout.visibility = View.GONE
        binding.deviceNotFound.visibility = View.VISIBLE

        setHeight(1f, 100L)
    }

    private fun readyToScan(): Boolean {
        val btOn = viewModel.bluetoothEnabled.value == true
        val locOn = viewModel.locationEnabled.value == true
        return btOn && locOn
    }

    private fun determineDeviceTypeButtonVisible() {
        if (latestWrappedScanResult == null) {
            binding.performActionButton.visibility = View.GONE
            binding.retrieveOwnerInformationButton.visibility = View.GONE
            return
        }

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
            val subType = GoogleFindMyNetwork.getSubType(latestWrappedScanResult!!)
            ScanFragment.googleSubDeviceTypeMap[latestWrappedScanResult!!.uniqueIdentifier] = subType

            if (subType == GoogleFindMyNetworkType.TAG) {
                // Check if this is a safe Google tracker (PREMATURE_OFFLINE connection state)
                val connectionState = latestWrappedScanResult!!.connectionState
                val isSafeGoogleTracker = connectionState == ConnectionState.PREMATURE_OFFLINE

                val savedGoogleExactTag = ScanFragment.googleExactTagDeterminedMap[latestWrappedScanResult!!.uniqueIdentifier]
                val deviceNameEmpty = deviceName == null || deviceName == ""

                // For safe Google trackers (PREMATURE_OFFLINE), hide both buttons
                if (isSafeGoogleTracker) {
                    binding.retrieveOwnerInformationButton.visibility = View.GONE
                    View.GONE
                } else {
                    // For unsafe trackers, determine which of the buttons should be shown
                    val showPerformActionButton = deviceNameEmpty || savedGoogleExactTag == null || !savedGoogleExactTag
                    if (showPerformActionButton) {
                        binding.retrieveOwnerInformationButton.visibility = View.GONE
                        View.VISIBLE
                    } else {
                        binding.retrieveOwnerInformationButton.visibility = View.VISIBLE
                        View.GONE
                    }
                }
            } else {
                binding.retrieveOwnerInformationButton.visibility = View.GONE
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

    private fun updateDeviceIcon() {
        latestWrappedScanResult?.let { wrappedScanResult ->
            val deviceRepository = ATTrackingDetectionApplication.getCurrentApp().deviceRepository
            val deviceFromDb = deviceRepository.getDevice(wrappedScanResult.uniqueIdentifier)

            val drawable = if (deviceFromDb != null) {
                deviceFromDb.getDrawable()
            } else {
                DeviceType.getImageDrawable(wrappedScanResult).let { ContextCompat.getDrawable(requireContext(), it) }
            }
            binding.deviceIcon.setImageDrawable(drawable)
        }
    }

    private fun setBattery(batteryState: BatteryState) {
        when(batteryState) {
            BatteryState.FULL -> {
                binding.batterySymbol.setImageDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.ic_battery_full_24))
            }
            BatteryState.MEDIUM -> {
                binding.batterySymbol.setImageDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.ic_battery_medium_24))
            }
            BatteryState.LOW -> {
                binding.batterySymbol.setImageDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.ic_battery_low_24))
            }
            BatteryState.VERY_LOW -> {
                binding.batterySymbol.setImageDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.ic_battery_very_low_24))
            }
            else -> {
                binding.batterySymbol.setImageDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.ic_battery_unknown_24))
            }
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
        if (!readyToScan()) return

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

        binding.batterySymbol.setOnClickListener {
            val text = when (viewModel.batteryState.value) {
                BatteryState.FULL -> R.string.battery_full
                BatteryState.MEDIUM -> R.string.battery_medium
                BatteryState.VERY_LOW -> R.string.battery_very_low
                BatteryState.LOW -> R.string.battery_low
                else -> R.string.battery_unknown
            }
            val duration = Toast.LENGTH_SHORT
            Toast.makeText(requireContext(), text, duration).show()
        }

        return binding.root
    }

    private fun defineRetrieveOwnerOnClickBehaviour(deviceNameFromDB: String? = null) {
        binding.retrieveOwnerInformationButton.setOnClickListener {
            val builder = AlertDialog.Builder(requireContext())
            builder.setTitle(R.string.retrieve_owner_information_alert_title)

            val displayName: String = deviceNameFromDB ?: viewModel.displayName.value ?: ""
            val manufacturer: GoogleFindMyNetworkManufacturer = GoogleFindMyNetwork.getGoogleManufacturerFromNameString(displayName)
            val explanationText: String = GoogleFindMyNetwork.getGoogleInformationRetrievalText(manufacturer)

            builder.setMessage(explanationText)

            builder.setPositiveButton(R.string.retrieve_owner_information_alert_next) { _, _ ->
                lifecycleScope.launch {
                    binding.performActionButton.visibility = View.GONE
                    binding.progressCircular.visibility = View.VISIBLE
                    binding.deviceTypeText.visibility = View.GONE
                    val ownerInformationURL = GoogleFindMyNetwork.getOwnerInformationURL(latestWrappedScanResult!!)
                    if (ownerInformationURL != null) {
                        try {
                            requireContext().let { assumedContext ->
                                Timber.d("Opening browser with URL: $ownerInformationURL")
                                Utility.openBrowser(assumedContext, ownerInformationURL.toString(), binding.root)
                            }
                        } catch (e: Exception) {
                            Timber.e("Error launching browser: ${e.localizedMessage}")
                            Snackbar.make(
                                binding.root,
                                R.string.retrieve_owner_information_failed,
                                Snackbar.LENGTH_LONG
                            ).show()
                        }
                    } else {
                        Timber.e("Owner information URL is null")
                        Snackbar.make(
                            binding.root,
                            R.string.retrieve_owner_information_failed,
                            Snackbar.LENGTH_LONG
                        ).show()
                    }
                    binding.retrieveOwnerInformationButton.visibility = View.VISIBLE
                    binding.progressCircular.visibility = View.GONE
                    binding.deviceTypeText.visibility = View.VISIBLE

                    subTypeGoogle = GoogleFindMyNetwork.getSubType(latestWrappedScanResult!!)
                    val errorCaseName = GoogleFindMyNetworkType.visibleStringFromSubtype(subTypeGoogle!!)
                    if (binding.deviceTypeText.text == errorCaseName) {
                        binding.performActionButton.visibility = View.VISIBLE
                    }
                }
            }

            builder.setNegativeButton(R.string.retrieve_owner_information_alert_close) { dialog, _ ->
                dialog.dismiss()
            }

            val dialog = builder.create()

            dialog.setOnDismissListener {
                binding.retrieveOwnerInformationButton.visibility = View.VISIBLE
            }

            dialog.show()
        }
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
                    updateDeviceIcon()
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
                val errorCaseName = GoogleFindMyNetworkType.visibleStringFromSubtype(subTypeGoogle!!)
                ScanFragment.googleSubDeviceTypeMap[latestWrappedScanResult!!.uniqueIdentifier] = subTypeGoogle!!

                if (latestWrappedScanResult!!.connectionState in DeviceManager.unsafeConnectionState){
                    val deviceRepository = ATTrackingDetectionApplication.getCurrentApp().deviceRepository
                    val device = deviceRepository.getDevice(latestWrappedScanResult!!.uniqueIdentifier)
                    val deviceName = GoogleFindMyNetwork.getDeviceName(latestWrappedScanResult!!)

                    if (device != null && deviceName != errorCaseName) {
                        if (deviceName != "") {
                            device.name = deviceName
                        }
                        device.subDeviceType = GoogleFindMyNetworkType.subTypeToString(subTypeGoogle!!)
                        deviceRepository.update(device)
                    }

                    if (deviceName == errorCaseName || deviceName == "") {
                        Snackbar.make(
                            binding.root,
                            R.string.device_determine_failed,
                            Snackbar.LENGTH_LONG
                        ).show()
                        binding.performActionButton.visibility = View.VISIBLE
                    } else {
                        viewModel.displayName.postValue(deviceName)
                        if (subTypeGoogle == GoogleFindMyNetworkType.TAG) {
                            ScanFragment.googleExactTagDeterminedMap[latestWrappedScanResult!!.uniqueIdentifier] = true
                            defineRetrieveOwnerOnClickBehaviour()
                            binding.retrieveOwnerInformationButton.visibility = View.VISIBLE
                        }
                    }
                }

                updateDeviceIcon()
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

                val deviceRepository = ATTrackingDetectionApplication.getCurrentApp().deviceRepository
                val device = deviceRepository.getDevice(latestWrappedScanResult!!.uniqueIdentifier)

                if (device != null && deviceName != findMyDefaultString) {
                    device.name = deviceName
                    deviceRepository.update(device)
                }

                if (deviceName == findMyDefaultString || deviceName == "") {
                    Snackbar.make(
                        binding.root,
                        R.string.device_determine_failed,
                        Snackbar.LENGTH_LONG
                    ).show()
                    binding.performActionButton.visibility = View.VISIBLE
                } else {
                    viewModel.displayName.postValue(deviceName)
                }

                updateDeviceIcon()
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

                val deviceRepository = ATTrackingDetectionApplication.getCurrentApp().deviceRepository
                val device = deviceRepository.getDevice(latestWrappedScanResult!!.uniqueIdentifier)

                if (device != null && deviceName != pebblebeeDefaultString) {
                    device.name = deviceName
                    deviceRepository.update(device)
                }

                if (deviceName == pebblebeeDefaultString || deviceName == "") {
                    Snackbar.make(
                        binding.root,
                        R.string.device_determine_failed,
                        Snackbar.LENGTH_LONG
                    ).show()
                    binding.performActionButton.visibility = View.VISIBLE
                } else {
                    viewModel.displayName.postValue(deviceName)
                }

                    updateDeviceIcon()
                binding.progressCircular.visibility = View.GONE
                binding.deviceTypeText.visibility = View.VISIBLE
            }
        } else if (deviceType == DeviceType.SAMSUNG_FIND_MY_MOBILE && latestWrappedScanResult != null) {
            binding.performActionButton.visibility = View.GONE
            binding.deviceTypeText.visibility = View.GONE
            binding.progressCircular.visibility = View.VISIBLE
            lifecycleScope.launch {
                val deviceName = SamsungFindMyMobile.getSubTypeName(latestWrappedScanResult!!)

                val deviceRepository = ATTrackingDetectionApplication.getCurrentApp().deviceRepository
                val device = deviceRepository.getDevice(latestWrappedScanResult!!.uniqueIdentifier)

                if (device != null) {
                    device.name = deviceName
                    deviceRepository.update(device)
                }

                viewModel.displayName.postValue(deviceName)

                updateDeviceIcon()
                binding.progressCircular.visibility = View.GONE
                binding.deviceTypeText.visibility = View.VISIBLE
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshLocationState()
        viewModel.isFirstScanCallback.postValue(true)
        determineDeviceTypeButtonVisible()
        showSearchMessage()
        startBluetoothScan()
    }

    override fun onStart() {
        super.onStart()
        viewModel.startMonitoringSystemToggles()
    }

    override fun onStop() {
        super.onStop()
        viewModel.stopMonitoringSystemToggles()
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