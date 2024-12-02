package de.seemoo.at_tracking_detection.ui.dashboard

import android.Manifest
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.navArgs
import dagger.hilt.android.AndroidEntryPoint
import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.database.models.Location
import de.seemoo.at_tracking_detection.databinding.FragmentDeviceMapBinding
import de.seemoo.at_tracking_detection.util.Utility
import de.seemoo.at_tracking_detection.util.risk.RiskLevelEvaluator
import kotlinx.coroutines.launch
import org.osmdroid.views.MapView

@AndroidEntryPoint
class DeviceMapFragment : Fragment() {

    private val viewModel: DeviceMapViewModel by viewModels()
    private val safeArgs: DeviceMapFragmentArgs by navArgs()

    private lateinit var binding: FragmentDeviceMapBinding

    private var deviceAddress: String? = null

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

        deviceAddress = safeArgs.deviceAddress
        viewModel.deviceAddress.postValue(deviceAddress)

        setTitle()

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ViewCompat.setTranslationZ(view, 100f)
        val map: MapView = view.findViewById(R.id.map)

        Utility.checkAndRequestPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        viewModel.isMapLoading.postValue(true)
        Utility.enableMyLocationOverlay(map)
        setTitle()

        lifecycleScope.launch {
            val locationRepository = ATTrackingDetectionApplication.getCurrentApp().locationRepository
            val relevantTrackingDate = RiskLevelEvaluator.relevantTrackingDateForRiskCalculation
            val locationList: List<Location> = if (!deviceAddress.isNullOrEmpty()) {
                locationRepository.getLocationsForBeaconSince(deviceAddress!!, relevantTrackingDate)
            } else {
                locationRepository.locationsSince(relevantTrackingDate)
            }

            try {
                Utility.setGeoPointsFromListOfLocations(locationList, map)
            } finally {
                viewModel.isMapLoading.postValue(false)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        setTitle()
    }

    fun setTitle() {
        if (deviceAddress != null) {
            (requireActivity() as AppCompatActivity).supportActionBar?.title = getString(R.string.title_devices_map_device, deviceAddress)
        } else {
            (requireActivity() as AppCompatActivity).supportActionBar?.title = getString(R.string.title_device_map)
        }
    }
}