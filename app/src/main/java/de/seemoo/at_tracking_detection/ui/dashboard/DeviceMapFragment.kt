package de.seemoo.at_tracking_detection.ui.dashboard

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.marginBottom
import androidx.core.view.updateLayoutParams
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import dagger.hilt.android.AndroidEntryPoint
import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.database.models.Location
import de.seemoo.at_tracking_detection.databinding.FragmentDeviceMapBinding
import de.seemoo.at_tracking_detection.util.MapUtils
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

    private var isLegendVisible = false

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

        setTitle()

        // Setup initial state for legend
        binding.legendFab.visibility = View.VISIBLE
        binding.legendContainer.visibility = View.INVISIBLE
        isLegendVisible = false

        val footerText = if (deviceAddress.isNullOrEmpty()) {
            getString(R.string.map_footer_all_devices, RiskLevelEvaluator.RELEVANT_DAYS_RISK_LEVEL)
        } else {
            getString(R.string.map_footer_device)
        }
        binding.legendFooterText.text = footerText

        setupLegendInteractions()

        // Handle Edge-to-Edge and Insets
        handleWindowInsets()

        // Connectivity Check
        val connectivityManager = context?.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkCapabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)

        if (networkCapabilities == null || !networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            viewModel.isMapLoading.postValue(false)
            viewModel.hideMapShowNoInternetInstead.postValue(true)
            binding.legendFab.visibility = View.GONE
            return
        }

        ViewCompat.setTranslationZ(view, 100f)
        val map: MapView = view.findViewById(R.id.map)

        MapUtils.basicMapSetup(map)

        Utility.checkAndRequestPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        viewModel.isMapLoading.postValue(true)
        // MapUtils.enableMyLocationOverlay(map) // This enables the blue location dot on the map

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
                // Only show device info popup when viewing all devices (aka deviceAddress is null or empty)
                val showDeviceInfoOnClick = deviceAddress.isNullOrEmpty()
                MapUtils.setGeoPointsFromListOfLocations(
                    locationList,
                    map, 
                    showDeviceInfoOnClick,
                    onMarkerClick = { deviceAddress ->
                        // Navigate to tracking fragment when marker info window is clicked
                        val action = DeviceMapFragmentDirections.actionDeviceMapFragmentToTrackingFragment(deviceAddress)
                        findNavController().navigate(action)
                    },
                    onMoreTrackersClick = { locationId ->
                        // Navigate to devices fragment with location filter
                        val action = DeviceMapFragmentDirections.actionDeviceMapFragmentToDevicesFound(
                            showDevicesFound = true,
                            showAllDevices = true,
                            locationId = locationId
                        )
                        findNavController().navigate(action)
                    },
                    onMapClick = {
                        // Close legend if map is clicked
                        if (isLegendVisible) {
                            hideLegend()
                        }
                    }
                )
            } finally {
                viewModel.isMapLoading.postValue(false)
            }
        }
    }

    private fun handleWindowInsets() {
        // Prevent double padding on root view from MainActivity's global listener
        binding.root.post {
            ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
                insets
            }
        }

        val legendCard = binding.legendContainer
        val fab = binding.legendFab

        // Define a standard margin (16dp)
        val standardMargin = android.util.TypedValue.applyDimension(
            android.util.TypedValue.COMPLEX_UNIT_DIP,
            16f,
            resources.displayMetrics
        ).toInt()

        // Apply ONLY the standard margin.
        ViewCompat.setOnApplyWindowInsetsListener(legendCard) { view, insets ->
            view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = standardMargin
            }
            insets
        }

        ViewCompat.setOnApplyWindowInsetsListener(fab) { view, insets ->
            view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = standardMargin
            }
            insets
        }
    }

    private fun setupLegendInteractions() {
        binding.legendFab.setOnClickListener { showLegend() }
        binding.legendCollapseButton.setOnClickListener { hideLegend() }
    }

    private fun showLegend() {
        if (isLegendVisible) return
        isLegendVisible = true

        // Animation: Fade out FAB
        binding.legendFab.animate()
            .alpha(0f)
            .scaleX(0.1f)
            .scaleY(0.1f)
            .setDuration(200)
            .withEndAction { binding.legendFab.visibility = View.INVISIBLE }
            .start()

        // Animation: Reveal Card
        binding.legendContainer.apply {
            visibility = View.VISIBLE
            alpha = 0f
            scaleX = 0.8f
            scaleY = 0.8f
            animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(250)
                .setListener(null)
                .start()
        }
    }

    private fun hideLegend() {
        if (!isLegendVisible) return
        isLegendVisible = false

        // Animation: Hide Card
        binding.legendContainer.animate()
            .alpha(0f)
            .scaleX(0.8f)
            .scaleY(0.8f)
            .setDuration(200)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    binding.legendContainer.visibility = View.INVISIBLE
                }
            })
            .start()

        // Animation: Fade in FAB
        binding.legendFab.apply {
            visibility = View.VISIBLE
            alpha = 0f
            scaleX = 0.1f
            scaleY = 0.1f
            animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(250)
                .start()
        }
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