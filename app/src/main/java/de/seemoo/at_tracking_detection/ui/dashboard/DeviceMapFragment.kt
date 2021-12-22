package de.seemoo.at_tracking_detection.ui.dashboard

import android.Manifest
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.NavDirections
import androidx.navigation.fragment.findNavController
import dagger.hilt.android.AndroidEntryPoint
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.databinding.FragmentDeviceMapBinding
import de.seemoo.at_tracking_detection.util.RiskLevelEvaluator
import de.seemoo.at_tracking_detection.util.Util
import java.time.LocalDateTime

@AndroidEntryPoint
class DeviceMapFragment : Fragment() {

    private val viewModel: RiskDetailViewModel by viewModels()

    private lateinit var binding: FragmentDeviceMapBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DataBindingUtil.inflate(
            inflater,
            R.layout.fragment_device_map,
            container,
            false
        )
        binding.lifecycleOwner = viewLifecycleOwner
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ViewCompat.setTranslationZ(view, 100f)

        Util.checkAndRequestPermission(Manifest.permission.ACCESS_FINE_LOCATION)


        Util.setGeoPointsFromList(viewModel.discoveredBeacons, view) { beacon ->
            val directions: NavDirections =
                DeviceMapFragmentDirections.actionDeviceMapFragmentToTrackingFragment(
                    -1,
                    beacon.deviceAddress
                )
            findNavController().navigate(directions)
        }
    }

}