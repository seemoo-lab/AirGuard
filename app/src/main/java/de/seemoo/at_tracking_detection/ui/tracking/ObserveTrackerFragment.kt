package de.seemoo.at_tracking_detection.ui.tracking

import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.navArgs
import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication
import de.seemoo.at_tracking_detection.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

class ObserveTrackerFragment: Fragment() {
    private val viewModel: ObserveTrackerViewModel by viewModels()
    private val safeArgs: ObserveTrackerFragmentArgs by navArgs()

    // TODO: check if an observation already takes place

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val observationButton = view.findViewById<Button>(R.id.start_observation_button)
        observationButton.setOnClickListener{
            // TODO: safety checks
            val deviceAddress = safeArgs.deviceAddress!!
            val deviceRepository = ATTrackingDetectionApplication.getCurrentApp()?.deviceRepository!!

            val coroutineScope = CoroutineScope(Dispatchers.Main)

            // Because update is a suspend function, we need a coroutine
            coroutineScope.launch {
                withContext(Dispatchers.IO) {
                    val device = deviceRepository.getDevice(deviceAddress)!!
                    device.nextObservationNotification = LocalDateTime.now()
                    device.currentObservationDuration = 60L // in minutes

                    deviceRepository.update(device)
                }
            }
        }
    }

}