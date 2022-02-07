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
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavDirections
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import dagger.hilt.android.AndroidEntryPoint
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.database.models.Beacon
import de.seemoo.at_tracking_detection.databinding.FragmentDeviceMapBinding
import de.seemoo.at_tracking_detection.util.Util
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.osmdroid.views.MapView

@AndroidEntryPoint
class DeviceMapFragment : Fragment() {

    private val viewModel: RiskDetailViewModel by viewModels()
    private val safeArgs: DeviceMapFragmentArgs by navArgs()

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
        val map: MapView = view.findViewById(R.id.map)

        Util.checkAndRequestPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        viewModel.isMapLoading.postValue(true)
        lifecycleScope.launch {
            var beaconList = listOf<Beacon>()
            if (safeArgs.showAllDevices) {
                viewModel.allBeacons().collect { beaconList = it }
            } else {
                beaconList = viewModel.discoveredBeacons
            }

            Util.setGeoPointsFromList(beaconList, map) { beacon ->
                val directions: NavDirections =
                    DeviceMapFragmentDirections.actionDeviceMapFragmentToTrackingFragment(
                        beacon.deviceAddress,
                        -1
                    )
                findNavController().navigate(directions)
            }
        }.invokeOnCompletion {
            viewModel.isMapLoading.postValue(false)
        }
    }

}