package de.seemoo.at_tracking_detection.ui.devices

import android.Manifest
import androidx.navigation.NavDirections
import androidx.navigation.fragment.FragmentNavigatorExtras
import androidx.navigation.fragment.findNavController
import com.google.android.material.card.MaterialCardView
import de.seemoo.at_tracking_detection.database.models.device.BaseDevice
import de.seemoo.at_tracking_detection.util.Util

class IgnoredDevicesFragment: DevicesFragment(showDevicesFound = false, showAllDevices = true ) {
    override val deviceItemListener: DeviceAdapter.OnClickListener
        get() = DeviceAdapter.OnClickListener { baseDevice: BaseDevice, materialCardView: MaterialCardView ->
            if (!Util.checkAndRequestPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
                return@OnClickListener
            }
            val directions: NavDirections =
                IgnoredDevicesFragmentDirections
                    .actionNavigationIgnoredDevicesFragmentToTrackingFragment(
                        baseDevice.address
                    )
            val extras = FragmentNavigatorExtras(materialCardView to baseDevice.address)
            findNavController().navigate(directions, extras)
        }
}