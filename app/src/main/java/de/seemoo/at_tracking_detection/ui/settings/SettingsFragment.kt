package de.seemoo.at_tracking_detection.ui.settings

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.navigation.findNavController
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import dagger.hilt.android.AndroidEntryPoint
import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication
import de.seemoo.at_tracking_detection.BuildConfig
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.util.SharedPrefs
import de.seemoo.at_tracking_detection.util.Utility
import de.seemoo.at_tracking_detection.worker.BackgroundWorkScheduler
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class SettingsFragment : PreferenceFragmentCompat() {

    @Inject
    lateinit var sharedPreferences: SharedPreferences

    @Inject
    lateinit var backgroundWorkScheduler: BackgroundWorkScheduler

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.fragment_settings, rootKey)
        updatePermissionSettings()
        sharedPreferences.registerOnSharedPreferenceChangeListener(sharedPreferenceListener)

        val riskSensitivityPref = findPreference<ListPreference>("risk_sensitivity")
        val devicesFilterPref = findPreference<MultiSelectListPreference>("devices_filter_unselected")

        val updateDevicesFilter = { sensitivity: String ->
            val entries = resources.getStringArray(R.array.devicesFilter).toMutableList()
            val entryValues = resources.getStringArray(R.array.devicesFilterValue).toMutableList()

            if (sensitivity == "high") {
                // Add samsung_find_my_mobile if not already present
                if (!entryValues.contains("samsung_find_my_mobile")) {
                    entries.add(getString(R.string.samsung_find_my_mobile_name))
                    entryValues.add("samsung_find_my_mobile")
                }
            } else {
                // Remove samsung_find_my_mobile if present
                val index = entryValues.indexOf("samsung_find_my_mobile")
                if (index != -1) {
                    entries.removeAt(index)
                    entryValues.removeAt(index)
                }
            }

            devicesFilterPref?.entries = entries.toTypedArray()
            devicesFilterPref?.entryValues = entryValues.toTypedArray()
        }

        riskSensitivityPref?.value?.let { updateDevicesFilter(it) }

        riskSensitivityPref?.setOnPreferenceChangeListener { _, newValue ->
            updateDevicesFilter(newValue as String)
            true
        }

        if (SharedPrefs.token == null && !SharedPrefs.shareData) {
            findPreference<Preference>("delete_study_data")?.isVisible = false
        }

        findPreference<Preference>("send_ble_error_messages")?.isVisible = BuildConfig.DEBUG


        setAdvancedModeButtonVisibility()

        // TODO
//        if (ATTrackingDetectionApplication.getCurrentApp().beaconRepository.totalCount > 0) {
//            findPreference<SwitchPreferenceCompat>("export_found_trackers")?.isVisible = true
//        } else {
//            findPreference<SwitchPreferenceCompat>("export_found_trackers")?.isVisible = false
//        }

        val deactivateBackgroundScanningPref = findPreference<SwitchPreferenceCompat>("deactivate_background_scanning")
        deactivateBackgroundScanningPref?.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
            if (newValue as Boolean) {
                // Show confirmation dialog
                AlertDialog.Builder(requireContext())
                    .setTitle(ATTrackingDetectionApplication.getAppContext().resources.getString(R.string.confirm_deactivating_background_scan_title))
                    .setMessage(ATTrackingDetectionApplication.getAppContext().resources.getString(R.string.confirm_deactivating_background_scan_text))
                    .setPositiveButton(ATTrackingDetectionApplication.getAppContext().resources.getString(R.string.confirm_deactivating_background_scan_yes)) { _, _ ->
                        // User confirmed, allow the change
                        if (deactivateBackgroundScanningPref != null) {
                            deactivateBackgroundScanningPref.isChecked = true
                        }
                    }
                    .setNegativeButton(ATTrackingDetectionApplication.getAppContext().resources.getString(R.string.confirm_deactivating_background_scan_no)) { _, _ ->
                        // User canceled, revert the change
                        if (deactivateBackgroundScanningPref != null) {
                            deactivateBackgroundScanningPref.isChecked = false
                        }
                    }
                    .show()
                // Return false to prevent the change until user confirms
                false
            } else {
                // Allow the change immediately
                deactivateBackgroundScanningPref.isVisible = SharedPrefs.advancedMode
                true
            }
        }

        findPreference<Preference>("super_scan")?.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                view?.findNavController()?.navigate(R.id.action_settings_to_super_scan)
                true
            }

        findPreference<Preference>("information_contact")?.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                view?.findNavController()?.navigate(R.id.action_settings_to_information)
                true
            }

        findPreference<Preference>("export_found_trackers")?.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                view?.findNavController()?.navigate(R.id.action_settings_to_export_found_trackers)
                true
            }

        findPreference<Preference>("delete_study_data")?.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                view?.findNavController()?.navigate(R.id.action_settings_to_data_deletion)
                true
            }

        findPreference<Preference>("privacy_policy")?.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                val intent = Intent(
                    Intent.ACTION_VIEW,
                    "https://tpe.seemoo.tu-darmstadt.de/privacy-policy.html".toUri()
                )
                startActivity(intent)
                return@OnPreferenceClickListener true
            }

        findPreference<Preference>("survey")?.setOnPreferenceClickListener {
            val intent = Intent(
                Intent.ACTION_VIEW,
                ATTrackingDetectionApplication.SURVEY_URL.toUri()
            )
            startActivity(intent)
            return@setOnPreferenceClickListener true
        }
    }

    private val sharedPreferenceListener =
        SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, preferenceKey ->
            when (preferenceKey) {
                "advanced_mode" -> {
                    setAdvancedModeButtonVisibility()
                }
                "share_data" -> {
                    if (SharedPrefs.shareData) {
                        Timber.d("Enabled background statistics sharing!")
                        backgroundWorkScheduler.scheduleShareData()
                        findPreference<Preference>("delete_study_data")?.isVisible = true
                    } else {
                        backgroundWorkScheduler.removeShareData()
                        if (SharedPrefs.token == null) {
                            findPreference<Preference>("delete_study_data")?.isVisible = false
                        }
                    }
                }
                "use_location" -> {
                    if (SharedPrefs.useLocationInTrackingDetection) {
                        Timber.d("Use location enabled!")
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            Utility.checkAndRequestPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                        }
                    }
                }
                "app_theme" -> {
                    Utility.setSelectedTheme(sharedPreferences)
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                        ATTrackingDetectionApplication.getCurrentActivity()?.recreate()
                    }
                }
            }
        }

    private fun setAdvancedModeButtonVisibility() {
        if (SharedPrefs.advancedMode) {
            Timber.d("Enabled advanced mode!")
            findPreference<Preference>("super_scan")?.isVisible = true
            findPreference<SwitchPreferenceCompat>("use_location")?.isVisible = true
            findPreference<SwitchPreferenceCompat>("use_low_power_ble")?.isVisible = true
            findPreference<SwitchPreferenceCompat>("notification_priority_high")?.isVisible = true
            findPreference<SwitchPreferenceCompat>("show_onboarding")?.isVisible = true
            findPreference<SwitchPreferenceCompat>("deactivate_background_scanning")?.isVisible = true
        } else {
            Timber.d("Disabled advanced mode!")
            findPreference<Preference>("super_scan")?.isVisible = false
            findPreference<SwitchPreferenceCompat>("use_location")?.isVisible = false
            findPreference<SwitchPreferenceCompat>("use_low_power_ble")?.isVisible = false
            findPreference<SwitchPreferenceCompat>("notification_priority_high")?.isVisible = false
            findPreference<SwitchPreferenceCompat>("show_onboarding")?.isVisible = false
            findPreference<SwitchPreferenceCompat>("deactivate_background_scanning")?.isVisible = SharedPrefs.deactivateBackgroundScanning
        }
    }

    private fun updatePermissionSettings() {
        val locationPermissionState =
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

        val backgroundPermissionState =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

        SharedPrefs.useLocationInTrackingDetection = locationPermissionState && backgroundPermissionState
    }
}