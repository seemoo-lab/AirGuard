package de.seemoo.at_tracking_detection.ui.settings

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.navigation.findNavController
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import dagger.hilt.android.AndroidEntryPoint
import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication
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

        if (SharedPrefs.token == null && !SharedPrefs.shareData) {
            findPreference<Preference>("delete_study_data")?.isVisible = false
        }

        if (SharedPrefs.advancedMode) {
            findPreference<SwitchPreferenceCompat>("use_location")?.isVisible = true
            findPreference<SwitchPreferenceCompat>("notification_priority_high")?.isVisible = true
            findPreference<SwitchPreferenceCompat>("show_onboarding")?.isVisible = true
        } else {
            findPreference<SwitchPreferenceCompat>("use_location")?.isVisible = false
            findPreference<SwitchPreferenceCompat>("notification_priority_high")?.isVisible = false
            findPreference<SwitchPreferenceCompat>("show_onboarding")?.isVisible = false
        }

        findPreference<Preference>("information_contact")?.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                view?.findNavController()?.navigate(R.id.action_settings_to_information)
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
                    Uri.parse("https://tpe.seemoo.tu-darmstadt.de/privacy-policy.html")
                )
                startActivity(intent)
                return@OnPreferenceClickListener true
            }

        findPreference<Preference>("survey")?.setOnPreferenceClickListener {
            val intent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse(ATTrackingDetectionApplication.SURVEY_URL)
            )
            startActivity(intent)
            return@setOnPreferenceClickListener true
        }
    }

    private val sharedPreferenceListener =
        SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, preferenceKey ->
            when (preferenceKey) {
                "advanced_mode" -> {
                    if (SharedPrefs.advancedMode) {
                        Timber.d("Enabled advanced mode!")
                        findPreference<SwitchPreferenceCompat>("use_location")?.isVisible = true
                        findPreference<SwitchPreferenceCompat>("notification_priority_high")?.isVisible = true
                        findPreference<SwitchPreferenceCompat>("show_onboarding")?.isVisible = true
                    } else {
                        Timber.d("Disabled advanced mode!")
                        findPreference<SwitchPreferenceCompat>("use_location")?.isVisible = false
                        findPreference<SwitchPreferenceCompat>("notification_priority_high")?.isVisible = false
                        findPreference<SwitchPreferenceCompat>("show_onboarding")?.isVisible = false
                    }
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