package de.seemoo.at_tracking_detection.ui.tracking

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.navArgs
import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.databinding.FragmentObserveTrackerBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

class ObserveTrackerFragment: Fragment() {
    private val viewModel: ObserveTrackerViewModel by viewModels()
    private val safeArgs: ObserveTrackerFragmentArgs by navArgs()

    private var deviceAddress: String? = null

    private lateinit var binding: FragmentObserveTrackerBinding

    // TODO: check if an observation already takes place

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val observationButton = view.findViewById<Button>(R.id.start_observation_button)
        observationButton.setOnClickListener{
            // TODO: safety checks
            val deviceRepository = ATTrackingDetectionApplication.getCurrentApp()?.deviceRepository!!

            val coroutineScope = CoroutineScope(Dispatchers.Main)

            // Because update is a suspend function, we need a coroutine
            coroutineScope.launch {
                withContext(Dispatchers.IO) {
                    val device = deviceRepository.getDevice(deviceAddress!!)!!

                    val duration = 60L // in minutes

                    device.nextObservationNotification = LocalDateTime.now().plusMinutes(duration)
                    device.currentObservationDuration = duration

                    deviceRepository.update(device)
                }
            }

            val text = "Observation started" // TODO: fixed string
            val duration = Toast.LENGTH_SHORT
            val toast = Toast.makeText(requireContext(), text, duration) // in Activity
            toast.show()

            // TODO: Crashes when we re enter the View after this pop
            requireActivity().supportFragmentManager.popBackStack()
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