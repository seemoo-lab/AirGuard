package de.seemoo.at_tracking_detection.ui.devices

import android.Manifest
import android.os.Bundle
import androidx.navigation.NavDirections
import androidx.navigation.fragment.FragmentNavigatorExtras
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.card.MaterialCardView
import de.seemoo.at_tracking_detection.database.models.device.BaseDevice
import de.seemoo.at_tracking_detection.util.Utility

class FoundDevicesFragment: DevicesFragment(
    showDevicesFound = true
) {
    private val safeArgs: FoundDevicesFragmentArgs by navArgs()

    override fun onCreate(savedInstanceState: Bundle?) {
        this.showAllDevices = safeArgs.showAllDevices
        this.deviceType = safeArgs.deviceType
        this.deviceType2 = safeArgs.deviceType2
        super.onCreate(savedInstanceState)
    }

    override val deviceItemListener: DeviceAdapter.OnClickListener
        get() = DeviceAdapter.OnClickListener { baseDevice: BaseDevice, materialCardView: MaterialCardView ->
            if (!Utility.checkAndRequestPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
                return@OnClickListener
            }
            val directions: NavDirections =
                FoundDevicesFragmentDirections
                .actionNavigationDevicesToTrackingFragment(
                    baseDevice.address
                )
            val extras = FragmentNavigatorExtras(materialCardView to baseDevice.address)
            findNavController().navigate(directions, extras)
        }

}