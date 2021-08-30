package de.seemoo.at_tracking_detection.ui.settings

import android.Manifest
import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.navigation.findNavController
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import dagger.hilt.android.AndroidEntryPoint
import de.seemoo.at_tracking_detection.R
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
        sharedPreferences.registerOnSharedPreferenceChangeListener { sharedPreferences: SharedPreferences, s: String ->
            when (s) {
                "share_data" -> {
                    if (sharedPreferences.getBoolean("share_data", false)) {
                        Timber.d("Enabled background statistics sharing!")
                        backgroundWorkScheduler.scheduleShareData()
                    } else {
                        backgroundWorkScheduler.removeShareData()
                    }
                }
                "use_location" -> {
                    if (sharedPreferences.getBoolean("use_location", false)) {
                        Timber.d("Use location enabled!")
                        sharedPreferences.edit().putBoolean("use_location", true).apply()
                        handlePermissions()
                    }
                }
            }
        }
        findPreference<Preference>("third_party_libraries")?.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                view?.findNavController()?.navigate(R.id.action_settings_to_about_libs)
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
    }

    private fun updatePermissionSettings() {
        val permissionState =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        sharedPreferences.edit().putBoolean("use_location", permissionState).apply()
    }

    private fun handlePermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return
        }
        val builder: AlertDialog.Builder = context.let { AlertDialog.Builder(it) }

        builder.setMessage(R.string.onboarding_4_description)
        builder.setTitle(R.string.onboarding_4_title)
        builder.setPositiveButton(R.string.ok_button) { _: DialogInterface, _: Int ->
            Timber.d("Request ACCESS BACKGROUND LOCATION PERMISSION permission!")
            requestPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                )
            )
        }

        val dialog = builder.create()
        dialog?.show()
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            permissions.map {
                if (!it.value) {
                    sharedPreferences.edit().putBoolean("use_location", false).apply()
                }
            }
        }
}