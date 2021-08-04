package de.seemoo.at_tracking_detection.ui.devices

import android.os.Bundle
import android.transition.TransitionInflater
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.interpolator.view.animation.LinearOutSlowInInterpolator
import androidx.navigation.NavDirections
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.card.MaterialCardView
import dagger.hilt.android.AndroidEntryPoint
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.databinding.FragmentDeviceDetailBinding
import de.seemoo.at_tracking_detection.util.Util
import java.util.concurrent.TimeUnit

@AndroidEntryPoint
class DeviceDetailFragment : Fragment() {

    private val devicesViewModel: DevicesViewModel by viewModels()

    private val safeArgs: DeviceDetailFragmentArgs by navArgs()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding: FragmentDeviceDetailBinding =
            DataBindingUtil.inflate(inflater, R.layout.fragment_device_detail, container, false)
        val device = devicesViewModel.getDevice(safeArgs.deviceAddress)
        binding.lifecycleOwner = viewLifecycleOwner
        binding.deviceBeaconCount = devicesViewModel.getDeviceBeaconsCount(safeArgs.deviceAddress)
        binding.device = device

        sharedElementEnterTransition =
            TransitionInflater.from(context).inflateTransition(android.R.transition.move)
                .setInterpolator(LinearOutSlowInInterpolator()).setDuration(500)
        postponeEnterTransition(100, TimeUnit.MILLISECONDS)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        devicesViewModel.getMarkerLocations(safeArgs.deviceAddress).let {
            Util.setGeoPointsFromList(it, view, true)
        }
        val device = view.findViewById<MaterialCardView>(R.id.device_item)
        device.setOnClickListener {
            val directions: NavDirections =
                DeviceDetailFragmentDirections.actionDeviceDetailToTrackingFragment(
                    -1,
                    safeArgs.deviceAddress
                )
            findNavController().navigate(directions)
        }
    }
}