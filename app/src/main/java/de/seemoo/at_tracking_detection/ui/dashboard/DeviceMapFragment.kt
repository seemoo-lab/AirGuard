package de.seemoo.at_tracking_detection.ui.dashboard

import android.Manifest
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
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
    private var isLegendExpanded = false

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
        binding.vm = viewModel

        deviceAddress = safeArgs.deviceAddress
        viewModel.deviceAddress.postValue(deviceAddress)

        setTitle()

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ViewCompat.setTranslationZ(view, 100f)
        val map: MapView = view.findViewById(R.id.map)

        Utility.basicMapSetup(map)

        Utility.checkAndRequestPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        viewModel.isMapLoading.postValue(true)
        // Utility.enableMyLocationOverlay(map) // This enables the blue location dot on the map
        setTitle()

        binding.legendContent.visibility = View.INVISIBLE

        binding.legendContainer.post {
            val widthSpec = View.MeasureSpec.makeMeasureSpec(binding.legendContainer.width - binding.legendContainer.paddingStart - binding.legendContainer.paddingEnd, View.MeasureSpec.EXACTLY)
            val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            binding.legendContent.measure(widthSpec, heightSpec)
            val contentHeight = binding.legendContent.measuredHeight
            // Shift container down by content height so only header remains visible
            binding.legendContainer.translationY = contentHeight.toFloat()
            isLegendExpanded = false
            // Set arrow to up
            binding.legendCollapseButton.rotation = 270f
            binding.legendContent.visibility = View.INVISIBLE
        }

        // Setup legend toggle functionality
        setupLegendToggle()

        lifecycleScope.launch {
            val locationRepository = ATTrackingDetectionApplication.getCurrentApp().locationRepository
            val relevantTrackingDate = RiskLevelEvaluator.relevantTrackingDateForRiskCalculation
            val locationList: List<Location> = if (!deviceAddress.isNullOrEmpty()) {
                locationRepository.getLocationsForBeacon(deviceAddress!!)
                // Old Code: locationRepository.getLocationsForBeaconSince(deviceAddress!!, relevantTrackingDate)
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

    private fun setupLegendToggle() {
        // Toggle when tapping header title or arrow
        binding.legendHeaderTitle.setOnClickListener {
            if (isLegendExpanded) hideLegend() else showLegend()
        }
        binding.legendCollapseButton.setOnClickListener {
            if (isLegendExpanded) hideLegend() else showLegend()
        }
    }

    private fun showLegend() {
        if (isLegendExpanded) return
        isLegendExpanded = true

        binding.legendContent.visibility = View.VISIBLE

        // Animate the whole container upward to reveal content. translationY -> 0
        binding.legendContainer.animate()
            .translationY(0f)
            .setDuration(300)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()

        // Rotate arrow to point down
        binding.legendCollapseButton.animate()
            .rotation(90f)
            .setDuration(300)
            .start()
    }

    private fun hideLegend() {
        if (!isLegendExpanded) return
        isLegendExpanded = false

        // Re-measure in case layout changed
        val widthSpec = View.MeasureSpec.makeMeasureSpec(binding.legendContainer.width - binding.legendContainer.paddingStart - binding.legendContainer.paddingEnd, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        binding.legendContent.measure(widthSpec, heightSpec)
        val contentHeight = binding.legendContent.measuredHeight

        // Animate container back down so only header is visible
        binding.legendContainer.animate()
            .translationY(contentHeight.toFloat())
            .setDuration(300)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction {
                binding.legendContent.visibility = View.INVISIBLE
            }
            .start()

        // Rotate arrow to point up
        binding.legendCollapseButton.animate()
            .rotation(270f)
            .setDuration(300)
            .start()
    }

    override fun onResume() {
        super.onResume()
        setTitle()
        val map: MapView = view?.findViewById(R.id.map) ?: return
        map.onResume()
    }

    override fun onPause() {
        super.onPause()
        val map: MapView = view?.findViewById(R.id.map) ?: return
        map.onPause()
    }

    fun setTitle() {
        if (deviceAddress != null) {
            (requireActivity() as AppCompatActivity).supportActionBar?.title = getString(R.string.title_devices_map_device, deviceAddress)
        } else {
            (requireActivity() as AppCompatActivity).supportActionBar?.title = getString(R.string.title_device_map)
        }
    }
}