package de.seemoo.at_tracking_detection.ui.tracking

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.databinding.FragmentObserveTrackerBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import de.seemoo.at_tracking_detection.worker.ScheduleWorkersReceiver


class ObserveTrackerFragment: Fragment() {
    private val viewModel: ObserveTrackerViewModel by viewModels()
    private val safeArgs: ObserveTrackerFragmentArgs by navArgs()

    private var deviceAddress: String? = null

    private lateinit var binding: FragmentObserveTrackerBinding

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (deviceAddress != null) {
            val deviceRepository = ATTrackingDetectionApplication.getCurrentApp()!!.deviceRepository
            deviceRepository.let {
                val baseDevice = deviceRepository.getDevice(deviceAddress!!)
                if (baseDevice?.deviceType != null) {
                    val canBeIgnored = baseDevice.deviceType.canBeIgnored()
                    if (!canBeIgnored) {
                        val text = view.findViewById<TextView>(R.id.changing_id_text)
                        text.visibility = View.VISIBLE
                    }
                }
            }
        }

        val observationButton = view.findViewById<Button>(R.id.start_observation_button)
        observationButton.setOnClickListener {
            val deviceRepository = ATTrackingDetectionApplication.getCurrentApp()?.deviceRepository ?: return@setOnClickListener

            val coroutineScope = CoroutineScope(Dispatchers.Main)

            // Because update is a suspend function, we need a coroutine
            val coroutine = coroutineScope.launch {
                var settingObservationSuccessful = false // Flag to indicate the condition

                try {
                    withContext(Dispatchers.IO) {
                        val device = deviceRepository.getDevice(deviceAddress ?: return@withContext) ?: return@withContext

                        if (device.nextObservationNotification == null || device.nextObservationNotification!!.isBefore(LocalDateTime.now())) {
                            val observationDuration = ScheduleWorkersReceiver.OBSERVATION_DURATION

                            device.nextObservationNotification = LocalDateTime.now().plusHours(observationDuration)
                            device.currentObservationDuration = observationDuration

                            deviceRepository.update(device)
                            ScheduleWorkersReceiver.scheduleWorker(requireContext(), device.address)

                            settingObservationSuccessful = true // Set flag to true
                        }
                    }
                } catch (e: Exception) {
                    // Handle any exceptions here
                    e.printStackTrace()
                }

                // Perform actions outside the coroutine based on the flag
                if (settingObservationSuccessful) {
                    val text = R.string.observe_tracker_success
                    val toastDuration = Toast.LENGTH_SHORT
                    val toast = Toast.makeText(requireContext(), text, toastDuration)
                    withContext(Dispatchers.Main) {
                        toast.show()
                    }

                    findNavController().popBackStack()
                } else {
                    val text = R.string.observe_tracker_failure
                    val toastDuration = Toast.LENGTH_SHORT
                    val toast = Toast.makeText(requireContext(), text, toastDuration)
                    withContext(Dispatchers.Main) {
                        toast.show()
                    }
                }
            }

            // Ensure coroutine cancellation if needed
            viewLifecycleOwner.lifecycle.addObserver(object : LifecycleObserver {
                @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
                fun onDestroy() {
                    coroutine.cancel()
                }
            })
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DataBindingUtil.inflate(
            inflater,
            R.layout.fragment_observe_tracker,
            container,
            false
        )
        binding.lifecycleOwner = viewLifecycleOwner
        binding.vm = viewModel

        deviceAddress = safeArgs.deviceAddress

        return binding.root
    }
}