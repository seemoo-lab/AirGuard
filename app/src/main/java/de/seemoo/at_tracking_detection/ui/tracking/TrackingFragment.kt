package de.seemoo.at_tracking_detection.ui.tracking

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.cardview.widget.CardView
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.navigation.NavDirections
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.databinding.FragmentTrackingBinding
import de.seemoo.at_tracking_detection.ui.MainActivity
import de.seemoo.at_tracking_detection.util.Util
import de.seemoo.at_tracking_detection.util.ble.BluetoothLeService
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class TrackingFragment : Fragment() {

    @Inject
    lateinit var trackingViewModel: TrackingViewModel

    @Inject
    lateinit var applicationContext: Context

    private var notificationId: Int = -1

    private lateinit var gattServiceIntent: Intent

    private val safeArgs: TrackingFragmentArgs by navArgs()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding: FragmentTrackingBinding =
            DataBindingUtil.inflate(inflater, R.layout.fragment_tracking, container, false)

        gattServiceIntent = Intent(context, BluetoothLeService::class.java)
        binding.lifecycleOwner = viewLifecycleOwner
        binding.vm = trackingViewModel
        trackingViewModel.notificationId.postValue(safeArgs.notificationId)
        trackingViewModel.deviceAddress.postValue(safeArgs.deviceAddress)
        trackingViewModel.notificationId.observe(viewLifecycleOwner) {
            notificationId = it
        }
        return binding.root
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activity?.onBackPressedDispatcher?.addCallback(this) {
            if (safeArgs.notificationId != -1) {
                val intent = Intent(context, MainActivity::class.java)
                intent.apply {
                    flags =
                        Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                startActivity(intent)
            } else {
                findNavController().navigateUp()
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val feedbackButton = view.findViewById<CardView>(R.id.tracking_feedback)
        val playSoundCard = view.findViewById<CardView>(R.id.tracking_play_sound)

        feedbackButton.setOnClickListener {
            val directions: NavDirections =
                TrackingFragmentDirections.actionTrackingFragmentToFeedbackFragment(notificationId)
            findNavController().navigate(directions)
        }

        playSoundCard.setOnClickListener {
            val device = trackingViewModel.getDevice(safeArgs.deviceAddress)
            if (device != null && device.connectable) {
                toggleSound()
            } else {
                Snackbar.make(
                    view,
                    getString(R.string.tracking_device_not_connectable),
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }

        trackingViewModel.getMarkerLocations().observe(viewLifecycleOwner) {
            Util.setGeoPointsFromList(it, view, true)
        }
        trackingViewModel.soundPlaying.observe(viewLifecycleOwner) {
            if (!it) {
                try {
                    applicationContext.unbindService(serviceConnection)
                } catch (e: IllegalArgumentException) {
                    Timber.e("Tried to unbind an unbound service!")
                } finally {
                    Timber.d("Service connection unregistered")
                }
            }
        }
    }

    private fun toggleSound() {
        trackingViewModel.error.postValue(false)
        if (trackingViewModel.soundPlaying.value == false) {
            trackingViewModel.connecting.postValue(true)
            applicationContext.bindService(
                gattServiceIntent,
                serviceConnection,
                Context.BIND_AUTO_CREATE
            )
        } else {
            Timber.d("Sound already playing! Stopping sound...")
            trackingViewModel.soundPlaying.postValue(false)
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
                    trackingViewModel.deviceAddress.observe(viewLifecycleOwner) { address ->
                        it.connect(address)
                    }
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            trackingViewModel.soundPlaying.postValue(false)
            trackingViewModel.connecting.postValue(false)
        }
    }
}