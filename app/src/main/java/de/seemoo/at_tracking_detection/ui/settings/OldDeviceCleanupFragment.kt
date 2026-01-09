package de.seemoo.at_tracking_detection.ui.settings

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import dagger.hilt.android.AndroidEntryPoint
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication
import de.seemoo.at_tracking_detection.util.SharedPrefs
import javax.inject.Inject

@AndroidEntryPoint
class OldDeviceCleanupFragment : Fragment() {

    @Inject
    lateinit var sharedPreferences: SharedPreferences

    private lateinit var switchDeleteOldDevices: MaterialSwitch
    private lateinit var switchDeleteUnsafeDevices: MaterialSwitch
    private lateinit var timeframeDropdown: AutoCompleteTextView
    private lateinit var saveButton: Button

    private var pendingDeleteOldDevices: Boolean = false
    private var pendingDeleteUnsafeOldDevices: Boolean = false
    private var pendingOldDeviceTimeframeDays: Long = SharedPrefs.oldDeviceTimeframeDays

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
        saveButton = view.findViewById(R.id.save_button)

        setupDeactivationWarning()
        setupTimeframeDropdown()
        loadSettings()
        setupListeners()

        saveButton.setOnClickListener {
            applyPendingSettings()
            scheduleCleanupNow()
            updateSaveButtonState()
        }

        updateSaveButtonState()
    }

    private fun setupDeactivationWarning() {
        switchDeleteOldDevices.setOnCheckedChangeListener { _, isChecked ->
            if (!isChecked) {
                switchDeleteOldDevices.setOnCheckedChangeListener(null)

                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(getString(R.string.old_device_deactivate_title))
                    .setMessage(getString(R.string.old_device_deactivate_text))
                    .setPositiveButton(getString(R.string.old_device_deactivate_yes)) { _, _ ->
                        pendingDeleteOldDevices = false
                        switchDeleteOldDevices.isChecked = false
                        updateUnsafeSwitchState()
                        setupDeactivationWarning()
                        updateSaveButtonState()
                    }
                    .setNegativeButton(getString(R.string.old_device_deactivate_no)) { _, _ ->
                        pendingDeleteOldDevices = true
                        switchDeleteOldDevices.isChecked = true
                        updateUnsafeSwitchState()
                        setupDeactivationWarning()
                        updateSaveButtonState()
                    }
                    .setOnCancelListener {
                        pendingDeleteOldDevices = true
                        switchDeleteOldDevices.isChecked = true
                        updateUnsafeSwitchState()
                        setupDeactivationWarning()
                        updateSaveButtonState()
                    }
                    .show()
            } else {
                pendingDeleteOldDevices = true
                updateUnsafeSwitchState()
                updateSaveButtonState()
            }
        }
    }

    private fun setupTimeframeDropdown() {
        val timeframes = resources.getStringArray(R.array.old_device_timeframes)
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, timeframes)
        timeframeDropdown.setAdapter(adapter)
    }

    private fun loadSettings() {
        pendingDeleteOldDevices = SharedPrefs.deleteOldDevices
        pendingDeleteUnsafeOldDevices = SharedPrefs.deleteUnsafeOldDevices
        pendingOldDeviceTimeframeDays = SharedPrefs.oldDeviceTimeframeDays

        switchDeleteOldDevices.isChecked = pendingDeleteOldDevices
        switchDeleteUnsafeDevices.isChecked = pendingDeleteUnsafeOldDevices

        val timeframes = resources.getStringArray(R.array.old_device_timeframes)
        val timeframeValues = resources.getIntArray(R.array.old_device_timeframe_values).map { it.toLong() }
        val index = timeframeValues.indexOf(pendingOldDeviceTimeframeDays)
        if (index >= 0 && index < timeframes.size) {
            timeframeDropdown.setText(timeframes[index], false)
        }

        updateUnsafeSwitchState()
        updateSaveButtonState()
    }

    private fun setupListeners() {
        // Note: switchDeleteOldDevices listener is handled by setupDeactivationWarning() to show a confirmation dialog

        switchDeleteUnsafeDevices.setOnCheckedChangeListener { _, isChecked ->
            pendingDeleteUnsafeOldDevices = isChecked
            updateSaveButtonState()
        }

        timeframeDropdown.setOnItemClickListener { _, _, position, _ ->
            val timeframeValues = resources.getIntArray(R.array.old_device_timeframe_values).map { it.toLong() }
            if (position < timeframeValues.size) {
                pendingOldDeviceTimeframeDays = timeframeValues[position]
                updateSaveButtonState()
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
            pendingDeleteUnsafeOldDevices = false
            timeframeDropdown.isEnabled = false
        }
        updateSaveButtonState()
    }

    private fun applyPendingSettings() {
        SharedPrefs.deleteOldDevices = pendingDeleteOldDevices
        SharedPrefs.deleteUnsafeOldDevices = pendingDeleteUnsafeOldDevices
        SharedPrefs.oldDeviceTimeframeDays = pendingOldDeviceTimeframeDays
    }

    private fun scheduleCleanupNow() {
        val app = ATTrackingDetectionApplication.getCurrentApp()
        app.backgroundWorkScheduler.scheduleDeviceCleanupPeriodic()
        app.backgroundWorkScheduler.scheduleDeviceCleanupNow()
    }

    private fun updateSaveButtonState() {
        val hasChanges = pendingDeleteOldDevices != SharedPrefs.deleteOldDevices ||
                pendingDeleteUnsafeOldDevices != SharedPrefs.deleteUnsafeOldDevices ||
                pendingOldDeviceTimeframeDays != SharedPrefs.oldDeviceTimeframeDays
        saveButton.isEnabled = hasChanges
    }
}