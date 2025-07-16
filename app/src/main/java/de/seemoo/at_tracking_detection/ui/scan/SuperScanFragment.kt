package de.seemoo.at_tracking_detection.ui.scan

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.RadioGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.database.models.device.BaseDevice
import kotlinx.coroutines.launch

class SuperScanFragment : Fragment() {

    private lateinit var detector: SuperScanDetector
    private lateinit var resultAdapter: SuperScanAdapter
    private var suspectedDevices: List<BaseDevice> = emptyList()

    private lateinit var resultsRecyclerView: RecyclerView
    private lateinit var loadingSpinner: ProgressBar
    private lateinit var noResultsTextView: TextView
    private lateinit var mode3ResultTextView: TextView
    private lateinit var scanButton: Button
    private lateinit var modeSelector: RadioGroup

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_super_scan, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        detector = SuperScanDetector(
            ATTrackingDetectionApplication.getCurrentApp().beaconRepository,
            ATTrackingDetectionApplication.getCurrentApp().deviceRepository,
            ATTrackingDetectionApplication.getCurrentApp().locationRepository
        )

        val explanationTextView = view.findViewById<TextView>(R.id.super_scan_explanation)
        modeSelector = view.findViewById(R.id.scan_mode_selector)
        scanButton = view.findViewById(R.id.start_scan_button)
        resultsRecyclerView = view.findViewById(R.id.scan_results_recycler_view)
        mode3ResultTextView = view.findViewById(R.id.mode3_result_text)
        loadingSpinner = view.findViewById(R.id.loading_spinner)
        noResultsTextView = view.findViewById(R.id.no_results_text)

        setupRecyclerView()
        setupListeners(explanationTextView)

        // Set initial explanation text
        explanationTextView.text = getString(R.string.super_scan_explanation_mode1)
    }

    private fun setupRecyclerView() {
        resultsRecyclerView.layoutManager = LinearLayoutManager(context)
        resultAdapter = SuperScanAdapter(suspectedDevices) { device ->
            val direction = SuperScanFragmentDirections.actionSuperScanToTrackingFragment(
                device.address,
                device.deviceType.toString()
            )
            findNavController().navigate(direction)
        }
        resultsRecyclerView.adapter = resultAdapter
    }

    private fun setupListeners(explanationTextView: TextView) {
        modeSelector.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.mode1_button -> explanationTextView.text = getString(R.string.super_scan_explanation_mode1)
                R.id.mode2_button -> explanationTextView.text = getString(R.string.super_scan_explanation_mode2)
                R.id.mode3_button -> explanationTextView.text = getString(R.string.super_scan_explanation_mode3)
            }
            updateUiForNewScan()
        }

        scanButton.setOnClickListener {
            performScan()
        }
    }

    private fun performScan() {
        updateUiForScanStart()

        lifecycleScope.launch {
            // Parameters for the scan
            // TODO: make adjustable by user
            val daysToScan = 7L
            val durationMinutes = 60L
            val minLocations = 3
            val intervalMinutes = 20L

            when (modeSelector.checkedRadioButtonId) {
                R.id.mode1_button -> {
                    val results = detector.checkTrackersWithIdentitySwitching(daysToScan, durationMinutes, minLocations, intervalMinutes)
                    updateUiWithResults(results)
                }
                R.id.mode2_button -> {
                    val results = detector.checkTrackersWithMotionSensor(daysToScan, durationMinutes, minLocations)
                    updateUiWithResults(results)
                }
                R.id.mode3_button -> {
                    val result = detector.checkNetworkSwitchingTrackers(daysToScan, durationMinutes, minLocations, intervalMinutes)
                    updateUiForMode3(result)
                }
            }
        }
    }

    private fun updateUiForNewScan() {
        suspectedDevices = emptyList()
        resultAdapter.updateData(suspectedDevices)
        resultsRecyclerView.visibility = View.GONE
        mode3ResultTextView.visibility = View.GONE
        noResultsTextView.visibility = View.GONE
        loadingSpinner.visibility = View.GONE
    }

    private fun updateUiForScanStart() {
        loadingSpinner.visibility = View.VISIBLE
        resultsRecyclerView.visibility = View.GONE
        mode3ResultTextView.visibility = View.GONE
        noResultsTextView.visibility = View.GONE
    }

    private fun updateUiWithResults(results: List<BaseDevice>) {
        loadingSpinner.visibility = View.GONE
        suspectedDevices = results
        if (suspectedDevices.isEmpty()) {
            noResultsTextView.visibility = View.VISIBLE
            resultsRecyclerView.visibility = View.GONE
        } else {
            noResultsTextView.visibility = View.GONE
            resultsRecyclerView.visibility = View.VISIBLE
            resultAdapter.updateData(suspectedDevices)
        }
    }

    private fun updateUiForMode3(result: Boolean) {
        loadingSpinner.visibility = View.GONE
        resultsRecyclerView.visibility = View.GONE
        noResultsTextView.visibility = View.GONE
        mode3ResultTextView.visibility = View.VISIBLE
        mode3ResultTextView.text = if (result) getString(R.string.super_scan_mode3_result_positive) else getString(R.string.super_scan_mode3_result_negative)
    }
}
