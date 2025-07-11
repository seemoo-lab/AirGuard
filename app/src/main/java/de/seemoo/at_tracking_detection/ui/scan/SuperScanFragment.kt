package de.seemoo.at_tracking_detection.ui.scan

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.RadioGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.database.models.device.BaseDevice
import kotlinx.coroutines.launch

class SuperScanFragment : Fragment() {

    private val viewModel: SuperScanViewModel by viewModels()
    private lateinit var detector: SuperScanDetector
    private lateinit var resultAdapter: SuperScanAdapter
    private var suspectedDevices: MutableList<BaseDevice> = mutableListOf()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_super_scan, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        detector = SuperScanDetector(
            beaconRepository = ATTrackingDetectionApplication.getCurrentApp().beaconRepository,
            deviceRepository = ATTrackingDetectionApplication.getCurrentApp().deviceRepository,
            locationRepository = ATTrackingDetectionApplication.getCurrentApp().locationRepository
        )

        val explanationTextView = view.findViewById<TextView>(R.id.super_scan_explanation)
        val modeSelector = view.findViewById<RadioGroup>(R.id.scan_mode_selector)
        val scanButton = view.findViewById<Button>(R.id.start_scan_button)
        val resultsRecyclerView = view.findViewById<RecyclerView>(R.id.scan_results_recycler_view)
        val mode3ResultTextView = view.findViewById<TextView>(R.id.mode3_result_text)

        resultsRecyclerView.layoutManager = LinearLayoutManager(context)
        resultAdapter = SuperScanAdapter(suspectedDevices) { device ->
            val direction = SuperScanFragmentDirections.actionSuperScanToTrackingFragment(
                device.address,
                device.deviceType.toString()
            )
            findNavController().navigate(direction)
        }
        resultsRecyclerView.adapter = resultAdapter

        modeSelector.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.mode1_button -> explanationTextView.text = getString(R.string.super_scan_explanation_mode1)
                R.id.mode2_button -> explanationTextView.text = getString(R.string.super_scan_explanation_mode2)
                R.id.mode3_button -> explanationTextView.text = getString(R.string.super_scan_explanation_mode3)
            }
            // Clear previous results
            suspectedDevices.clear()
            resultAdapter.notifyDataSetChanged()
            mode3ResultTextView.visibility = View.GONE
            resultsRecyclerView.visibility = View.VISIBLE
        }
        // Set initial explanation text (Mode 1)
        explanationTextView.text = getString(R.string.super_scan_explanation_mode1)

        scanButton.setOnClickListener {
            lifecycleScope.launch {
                val daysToScan = 7L
                val durationHours = 1L
                val minLocations = 3
                val intervalMinutes = 20L

                when (modeSelector.checkedRadioButtonId) {
                    R.id.mode1_button -> {
                        val results = detector.checkTrackersWithIdentitySwitching(daysToScan, durationHours, minLocations, intervalMinutes)
                        suspectedDevices.clear()
                        suspectedDevices.addAll(results)
                        resultAdapter.notifyDataSetChanged()
                    }
                    R.id.mode2_button -> {
                        val results = detector.checkTrackersWithMotionSensor(daysToScan, durationHours, minLocations)
                        suspectedDevices.clear()
                        suspectedDevices.addAll(results)
                        resultAdapter.notifyDataSetChanged()
                    }
                    R.id.mode3_button -> {
                        val result = detector.checkNetworkSwitchingTrackers(daysToScan, durationHours, minLocations, intervalMinutes)
                        resultsRecyclerView.visibility = View.GONE
                        mode3ResultTextView.visibility = View.VISIBLE
                        mode3ResultTextView.text = if (result) getString(R.string.super_scan_mode3_result_positive) else getString(R.string.super_scan_mode3_result_negative)
                    }
                }
            }
        }
    }
}
