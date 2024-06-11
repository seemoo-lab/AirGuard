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
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import de.seemoo.at_tracking_detection.worker.ScheduleWorkersReceiver


class ObserveTrackerFragment: Fragment() {
    private val viewModel: ObserveTrackerViewModel by viewModels()
    private val safeArgs: ObserveTrackerFragmentArgs by navArgs()

    private var deviceAddress: String? = null

    private lateinit var binding: FragmentObserveTrackerBinding

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

//        if (deviceAddress != null) {
//            val text = view.findViewById<TextView>(R.id.changing_id_text)
//            text.visibility = View.VISIBLE
//        }

        val observationButton = view.findViewById<Button>(R.id.start_observation_button)

        if (deviceAddress != null) {
            val deviceRepository = ATTrackingDetectionApplication.getCurrentApp()!!.deviceRepository
            deviceRepository.let {
                val baseDevice = deviceRepository.getDevice(deviceAddress!!)

                if (baseDevice?.nextObservationNotification != null && baseDevice.nextObservationNotification!!.isAfter(LocalDateTime.now())) {
                    val explanationText = view.findViewById<TextView>(R.id.explanation_text)
                    explanationText.text = getString(R.string.observe_tracker_stop_observation_explanation)

                    observationButton.text = getString(R.string.observe_tracker_stop_observation)

                    observationButton.setOnClickListener {
                        val coroutineScope = CoroutineScope(Dispatchers.Main)

                        // Because update is a suspend function, we need a coroutine
                        val coroutine = coroutineScope.launch {
                            try {
                                withContext(Dispatchers.IO) {
                                    val device = deviceRepository.getDevice(deviceAddress ?: return@withContext) ?: return@withContext

                                    device.nextObservationNotification = null
                                    device.currentObservationDuration = null

                                    deviceRepository.update(device)
                                    // ScheduleWorkersReceiver.cancelWorker(requireContext(), device.address)
                                }
                            } catch (e: Exception) {
                                // Handle any exceptions here
                                e.printStackTrace()
                            }

                            val text = R.string.observe_tracker_stopped
                            val toastDuration = Toast.LENGTH_SHORT
                            val toast = Toast.makeText(requireContext(), text, toastDuration)
                            withContext(Dispatchers.Main) {
                                toast.show()
                            }

                            findNavController().popBackStack()
                        }

                        // Ensure coroutine cancellation if needed
                        viewLifecycleOwner.lifecycle.addObserver(object : LifecycleEventObserver {
                            override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                                if (event == Lifecycle.Event.ON_DESTROY) {
                                    coroutine.cancel()
                                }
                            }
                        })
                    }
                } else {
                    if (baseDevice?.deviceType != null) {
                        val canBeIgnored = baseDevice.deviceType.canBeIgnored()
                        if (!canBeIgnored) {
                            val text = view.findViewById<TextView>(R.id.changing_id_text)
                            text.visibility = View.VISIBLE
                        }
                    }

                    observationButton.setOnClickListener {
                        val coroutineScope = CoroutineScope(Dispatchers.Main)

                        // Because update is a suspend function, we need a coroutine
                        val coroutine = coroutineScope.launch {
                            try {
                                withContext(Dispatchers.IO) {
                                    val device = deviceRepository.getDevice(deviceAddress ?: return@withContext) ?: return@withContext

                                    val observationDuration = ScheduleWorkersReceiver.OBSERVATION_DURATION

                                    device.nextObservationNotification = LocalDateTime.now().plusHours(observationDuration)
                                    device.currentObservationDuration = observationDuration

                                    deviceRepository.update(device)
                                    ScheduleWorkersReceiver.scheduleWorker(requireContext(), device.address)
                                }
                            } catch (e: Exception) {
                                // Handle any exceptions here
                                e.printStackTrace()
                            }

                            val text = R.string.observe_tracker_success
                            val toastDuration = Toast.LENGTH_SHORT
                            val toast = Toast.makeText(requireContext(), text, toastDuration)
                            withContext(Dispatchers.Main) {
                                toast.show()
                            }

                            findNavController().popBackStack()
                        }

                        // Ensure coroutine cancellation if needed
                        viewLifecycleOwner.lifecycle.addObserver(
                            LifecycleEventObserver { _, event ->
                                if (event == Lifecycle.Event.ON_DESTROY) {
                                    coroutine.cancel()
                                }
                            }
                        )
                    }
                }

            }
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