package de.seemoo.at_tracking_detection.ui.settings

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.google.android.material.switchmaterial.SwitchMaterial
import dagger.hilt.android.AndroidEntryPoint
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.util.SharedPrefs
import javax.inject.Inject

@AndroidEntryPoint
class OldDeviceCleanupFragment : Fragment() {

    @Inject
    lateinit var sharedPreferences: SharedPreferences

    private lateinit var switchDeleteOldDevices: SwitchMaterial
    private lateinit var switchDeleteUnsafeDevices: SwitchMaterial
    private lateinit var timeframeDropdown: AutoCompleteTextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_old_device_cleanup, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        switchDeleteOldDevices = view.findViewById(R.id.switch_delete_old_devices)
        switchDeleteUnsafeDevices = view.findViewById(R.id.switch_delete_unsafe_devices)
        timeframeDropdown = view.findViewById(R.id.timeframe_dropdown)

        setupDeactivationWarning()
        setupTimeframeDropdown()
        loadSettings()
        setupListeners()
    }

    private fun setupDeactivationWarning() {
        switchDeleteOldDevices.setOnCheckedChangeListener { _, isChecked ->
            if (!isChecked) {
                switchDeleteOldDevices.setOnCheckedChangeListener(null)

                AlertDialog.Builder(requireContext())
                    .setTitle(getString(R.string.old_device_deactivate_title))
                    .setMessage(getString(R.string.old_device_deactivate_text))
                    .setPositiveButton(getString(R.string.old_device_deactivate_yes)) { _, _ ->
                        SharedPrefs.deleteOldDevices = false
                        switchDeleteOldDevices.isChecked = false
                        updateUnsafeSwitchState()
                        setupDeactivationWarning()
                    }
                    .setNegativeButton(getString(R.string.old_device_deactivate_no)) { _, _ ->
                        SharedPrefs.deleteOldDevices = true
                        switchDeleteOldDevices.isChecked = true
                        updateUnsafeSwitchState()
                        setupDeactivationWarning()
                    }
                    .setOnCancelListener {
                        // If dialog is dismissed, revert to enabled
                        SharedPrefs.deleteOldDevices = true
                        switchDeleteOldDevices.isChecked = true
                        updateUnsafeSwitchState()
                        setupDeactivationWarning()
                    }
                    .show()
            } else {
                // Enabling automatic deletion: apply immediately
                SharedPrefs.deleteOldDevices = true
                updateUnsafeSwitchState()
            }
        }
    }

    private fun setupTimeframeDropdown() {
        val timeframes = resources.getStringArray(R.array.old_device_timeframes)
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, timeframes)
        timeframeDropdown.setAdapter(adapter)
    }

    private fun loadSettings() {
        // Load settings from SharedPrefs
        switchDeleteOldDevices.isChecked = SharedPrefs.deleteOldDevices
        switchDeleteUnsafeDevices.isChecked = SharedPrefs.deleteUnsafeOldDevices

        // Set the timeframe dropdown value
        val timeframeValue = SharedPrefs.oldDeviceTimeframeDays
        val timeframes = resources.getStringArray(R.array.old_device_timeframes)

        // Read integer array from resources and convert to long
        val timeframeValues = resources.getIntArray(R.array.old_device_timeframe_values).map { it.toLong() }

        val index = timeframeValues.indexOf(timeframeValue)
        if (index >= 0 && index < timeframes.size) {
            timeframeDropdown.setText(timeframes[index], false)
        }

        updateUnsafeSwitchState()
    }

    private fun setupListeners() {
        // Note: switchDeleteOldDevices listener is handled by setupDeactivationWarning() to show a confirmation dialog

        switchDeleteUnsafeDevices.setOnCheckedChangeListener { _, isChecked ->
            SharedPrefs.deleteUnsafeOldDevices = isChecked
        }

        timeframeDropdown.setOnItemClickListener { _, _, position, _ ->
            val timeframeValues = resources.getIntArray(R.array.old_device_timeframe_values).map { it.toLong() }

            if (position < timeframeValues.size) {
                SharedPrefs.oldDeviceTimeframeDays = timeframeValues[position]
            }
        }
    }

    private fun updateUnsafeSwitchState() {
        if (switchDeleteOldDevices.isChecked) {
            // Enable the unsafe switch
            switchDeleteUnsafeDevices.isEnabled = true
            timeframeDropdown.isEnabled = true
        } else {
            // Disable and uncheck the unsafe switch
            switchDeleteUnsafeDevices.isEnabled = false
            switchDeleteUnsafeDevices.isChecked = false
            timeframeDropdown.isEnabled = false
            SharedPrefs.deleteUnsafeOldDevices = false
        }
    }
}
