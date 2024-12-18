package de.seemoo.at_tracking_detection.ui.tracking

import android.annotation.SuppressLint
import android.content.*
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.text.InputFilter
import android.transition.TransitionInflater
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.cardview.widget.CardView
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.interpolator.view.animation.LinearOutSlowInInterpolator
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.NavDirections
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.database.models.device.Connectable
import de.seemoo.at_tracking_detection.database.models.device.DeviceManager
import de.seemoo.at_tracking_detection.database.models.device.DeviceType
import de.seemoo.at_tracking_detection.databinding.FragmentTrackingBinding
import de.seemoo.at_tracking_detection.util.Utility
import de.seemoo.at_tracking_detection.util.ble.BluetoothConstants
import de.seemoo.at_tracking_detection.util.ble.BluetoothLeService
import kotlinx.coroutines.launch
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.views.MapView
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@AndroidEntryPoint
class TrackingFragment : Fragment() {

    @Inject
    lateinit var trackingViewModel: TrackingViewModel

    private var notificationId: Int = -1

    private val safeArgs: TrackingFragmentArgs by navArgs()

    lateinit var mapView: MapView

    private var isReceiverRegistered = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding: FragmentTrackingBinding =
            DataBindingUtil.inflate(inflater, R.layout.fragment_tracking, container, false)

        binding.lifecycleOwner = viewLifecycleOwner
        binding.vm = trackingViewModel
        val notificationId = safeArgs.notificationId
        // This is called deviceAddress but contains the ID not necessarily the address
        val deviceAddress = safeArgs.deviceAddress
        val deviceTypeAsString = safeArgs.deviceTypeAsString
        val deviceType: DeviceType = DeviceManager.stringToDeviceType(deviceTypeAsString)
        trackingViewModel.notificationId.postValue(notificationId)
        trackingViewModel.deviceAddress.postValue(deviceAddress)
        trackingViewModel.loadDevice(safeArgs.deviceAddress, deviceType)
        trackingViewModel.notificationId.observe(viewLifecycleOwner) {
            this.notificationId = it
        }

        sharedElementEnterTransition =
            TransitionInflater.from(context).inflateTransition(android.R.transition.move)
                .setInterpolator(LinearOutSlowInInterpolator()).setDuration(500)
        postponeEnterTransition(100, TimeUnit.MILLISECONDS)

        return binding.root
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onResume() {
        super.onResume()

        zoomToMarkers()

        val activity = ATTrackingDetectionApplication.getCurrentActivity() ?: return

        LocalBroadcastManager.getInstance(activity)
            .registerReceiver(gattUpdateReceiver, DeviceManager.gattIntentFilter)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.registerReceiver(
                gattUpdateReceiver,
                DeviceManager.gattIntentFilter,
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            activity.registerReceiver(
                gattUpdateReceiver,
                DeviceManager.gattIntentFilter
            )
        }
        isReceiverRegistered = true

        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        if (isReceiverRegistered) {
            context?.unregisterReceiver(gattUpdateReceiver)
            isReceiverRegistered = false
        }
        mapView.onPause()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activity?.onBackPressedDispatcher?.addCallback(this) {
            if (safeArgs.notificationId != -1) {
                activity?.finish()
            } else {
                findNavController().navigateUp()
            }
        }
    }

    private fun initializeMap() {
        Utility.basicMapSetup(mapView)

        mapView.addMapListener(object : MapListener {
            override fun onZoom(event: ZoomEvent?): Boolean {
                if (mapView.zoomLevelDouble >= 0 && mapView.zoomLevelDouble <= mapView.maxZoomLevel) {
                    zoomToMarkers()
                }
                return true
            }

            override fun onScroll(event: ScrollEvent?): Boolean {
                return true
            }
        })

        if (trackingViewModel.markerLocations.value != null && !trackingViewModel.isMapLoading.value!!) {
            zoomToMarkers()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mapView = view.findViewById(R.id.map)

        view.post {
            initializeMap()
        }

        trackingViewModel.deviceType.observe(viewLifecycleOwner) { deviceType ->
            view.findViewById<TextView>(R.id.identifier_explanation).text =
                Utility.getExplanationTextForDeviceType(deviceType)
        }

        view.findViewById<View>(R.id.manufacturer_website).setOnClickListener {
            trackingViewModel.clickOnWebsite(requireContext())
        }

        view.findViewById<CardView>(R.id.tracking_feedback).setOnClickListener {
            navigateToFeedbackFragment()
        }

        view.findViewById<CardView>(R.id.tracking_detail_scan).setOnClickListener {
            trackingViewModel.deviceAddress.value?.let { deviceAddress ->
                navigateToScanDistance(deviceAddress)
            }
        }

        view.findViewById<CardView>(R.id.tracking_observation).setOnClickListener {
            trackingViewModel.deviceAddress.value?.let { deviceAddress ->
                navigateToObserveTracker(deviceAddress)
            }
        }

        view.findViewById<CardView>(R.id.tracking_play_sound).setOnClickListener {
            handlePlaySound()
        }

        Utility.enableMyLocationOverlay(mapView)

        trackingViewModel.markerLocations.observe(viewLifecycleOwner) { beacons ->
            lifecycleScope.launch {
                trackingViewModel.isMapLoading.postValue(true)
                val locationList = Utility.fetchLocationListFromBeaconList(beacons)
                Utility.setGeoPointsFromListOfLocations(locationList, mapView)
                trackingViewModel.isMapLoading.postValue(false)
            }
        }

        trackingViewModel.soundPlaying.observe(viewLifecycleOwner) { isPlaying ->
            if (!isPlaying) {
                try {
                    requireContext().unbindService(serviceConnection)
                } catch (e: IllegalArgumentException) {
                    Timber.e("Tried to unbind an unbound service!")
                } finally {
                    Timber.d("Service connection unregistered")
                }
            }
        }

        addInteractions(view)

        val deviceNameTextView = view.findViewById<TextView>(R.id.device_name)

        deviceNameTextView.setOnClickListener {
            val device = trackingViewModel.device.value
            if (device != null) {
                val editName = EditText(context).apply {
                    maxLines = 1
                    filters = arrayOf(InputFilter.LengthFilter(MAX_CHARACTER_LIMIT))
                    setText(device.getDeviceNameWithID())
                }
                MaterialAlertDialogBuilder(requireContext())
                    .setIcon(R.drawable.ic_baseline_edit_24)
                    .setTitle(getString(R.string.devices_edit_title)).setView(editName)
                    .setNegativeButton(getString(R.string.cancel_button), null)
                    .setPositiveButton(R.string.ok_button) { _, _ ->
                        val newName = editName.text.toString()
                        if (newName.isNotEmpty()) {
                            device.name = newName
                            lifecycleScope.launch {
                                val deviceRepository = ATTrackingDetectionApplication.getCurrentApp().deviceRepository
                                deviceRepository.update(device)
                                Timber.d("Renamed device to ${device.name}")
                            }
                            deviceNameTextView.text = newName
                        } else {
                            Toast.makeText(context, R.string.device_name_cannot_be_empty, Toast.LENGTH_SHORT).show()
                        }
                    }
                    .show()
            }
        }
    }

    private fun zoomToMarkers() {
        lifecycleScope.launch {
            trackingViewModel.isMapLoading.postValue(true)
            val locationList = Utility.fetchLocationListFromBeaconList(trackingViewModel.markerLocations.value ?: emptyList())
            Utility.setGeoPointsFromListOfLocations(locationList, mapView)
            trackingViewModel.isMapLoading.postValue(false)
        }
    }

    private fun navigateToFeedbackFragment() {
        val directions: NavDirections =
            TrackingFragmentDirections.actionTrackingFragmentToFeedbackFragment(notificationId)
        findNavController().navigate(directions)
    }

    private fun navigateToScanDistance(deviceAddress: String) {
        val directions: NavDirections =
            TrackingFragmentDirections.actionTrackingToScanDistance(deviceAddress)
        findNavController().navigate(directions)
    }

    private fun navigateToObserveTracker(deviceAddress: String) {
        val directions: NavDirections =
            TrackingFragmentDirections.actionTrackingToObserveTracker(deviceAddress)
        findNavController().navigate(directions)
    }

    private fun handlePlaySound() {
        if (!Utility.checkAndRequestPermission(android.Manifest.permission.BLUETOOTH_CONNECT)) {
            return
        }

        val baseDevice = trackingViewModel.device.value
        if (baseDevice != null && baseDevice.device is Connectable) {
            toggleSound()
        } else {
            Snackbar.make(
                requireView(),
                getString(R.string.tracking_device_not_connectable),
                Snackbar.LENGTH_LONG
            ).show()
        }
    }

    private fun addInteractions(view: View) {
        val button = view.findViewById<ImageButton>(R.id.open_map_button)

        button.setOnClickListener {
            val direction = TrackingFragmentDirections.actionTrackingFragmentToDeviceMapFragment(showAllDevices = false, deviceAddress = trackingViewModel.deviceAddress.value)
            findNavController().navigate(direction)
        }

        val overlay = view.findViewById<View>(R.id.map_overlay)
        overlay.setOnClickListener {
            val direction = TrackingFragmentDirections.actionTrackingFragmentToDeviceMapFragment(showAllDevices = false, deviceAddress = trackingViewModel.deviceAddress.value)
            findNavController().navigate(direction)
        }
    }

    private fun toggleSound() {
        trackingViewModel.error.postValue(false)
        if (trackingViewModel.soundPlaying.value == false) {
            trackingViewModel.connecting.postValue(true)
            val gattServiceIntent = Intent(context, BluetoothLeService::class.java)
            requireContext().bindService(
                gattServiceIntent,
                serviceConnection,
                Context.BIND_AUTO_CREATE
            )
        } else {
            Timber.d("Sound already playing! Stopping sound...")
            trackingViewModel.soundPlaying.postValue(false)
        }
    }

    private val gattUpdateReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothConstants.ACTION_EVENT_RUNNING -> {
                    trackingViewModel.soundPlaying.postValue(
                        true
                    )
                    trackingViewModel.connecting.postValue(false)
                }
                BluetoothConstants.ACTION_GATT_DISCONNECTED -> {
                    trackingViewModel.soundPlaying.postValue(false)
                    trackingViewModel.connecting.postValue(false)
                }
                BluetoothConstants.ACTION_EVENT_FAILED -> {
                    trackingViewModel.error.postValue(true)
                    trackingViewModel.connecting.postValue(false)
                    trackingViewModel.soundPlaying.postValue(false)
                }
                BluetoothConstants.ACTION_EVENT_COMPLETED -> trackingViewModel.soundPlaying.postValue(
                    false
                )
            }
        }
    }

    private val serviceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Timber.d("Trying to connect to ble device!")
            val bluetoothService = (service as BluetoothLeService.LocalBinder).getService()
            bluetoothService.let {
                if (!it.init()) {
                    Timber.e("Unable to init bluetooth")
                    trackingViewModel.error.postValue(true)
                } else {
                    Timber.d("Device is ready to connect!")
                    trackingViewModel.device.observe(viewLifecycleOwner) { baseDevice ->
                        if (baseDevice != null) {
                            it.connect(baseDevice)
                        }
                    }
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            trackingViewModel.soundPlaying.postValue(false)
            trackingViewModel.connecting.postValue(false)
        }
    }

    companion object {
        const val MAX_CHARACTER_LIMIT = 255
    }
}