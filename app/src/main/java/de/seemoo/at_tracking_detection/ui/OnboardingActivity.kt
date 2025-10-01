package de.seemoo.at_tracking_detection.ui

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.github.appintro.AppIntro
import com.github.appintro.AppIntroFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.ui.onboarding.IgnoreBatteryOptimizationFragment
import de.seemoo.at_tracking_detection.ui.onboarding.ShareDataFragment
import de.seemoo.at_tracking_detection.util.SharedPrefs
import de.seemoo.at_tracking_detection.worker.BackgroundWorkScheduler
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class OnboardingActivity : AppIntro() {

    @Inject
    lateinit var sharedPreferences: SharedPreferences

    @Inject
    lateinit var backgroundWorkScheduler: BackgroundWorkScheduler

    var permission: String? = null

    private var dialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        MainActivity.configureSystemBars(this, edgeToEdge = true, applyRootPadding = true)

        try {
            WindowCompat.getInsetsController(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
        } catch (e: Exception) {
            Timber.w(e, "Failed to disable immersive mode or show system bars")
        }

        permission = intent.getStringExtra("permission")
        Timber.d("Onboarding started with: $permission")
        if (permission != null) {
            when (permission) {
                Manifest.permission.ACCESS_FINE_LOCATION -> locationSlide(1)
                Manifest.permission.BLUETOOTH_SCAN -> scanSlide(1)
                Manifest.permission.ACCESS_BACKGROUND_LOCATION -> backgroundLocationSlide(1)
                Manifest.permission.BLUETOOTH_CONNECT -> connectSlide(1)
                Manifest.permission.POST_NOTIFICATIONS -> notificationSlide(1)
            }
        } else {
            buildSlides()
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            WindowCompat.getInsetsController(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
        } catch (e: Exception) {
            Timber.w(e, "Failed to disable immersive mode or show system bars in onResume")
        }
    }

    override fun onDonePressed(currentFragment: Fragment?) {
        //Checks which permissions have given to store the default value for location access
        val locationPermissionState =
            ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

        val backgroundPermissionState =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

        SharedPrefs.useLocationInTrackingDetection = locationPermissionState && backgroundPermissionState

        if (permission == null) {
            SharedPrefs.onBoardingCompleted = true
            SharedPrefs.showOnboarding = false
            backgroundWorkScheduler.launch()
            finish()
            startActivity(Intent(applicationContext, MainActivity::class.java).apply {
                action = "de.seemoo.at_tracking_detection.OPEN_MAIN_ACTIVITY"
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
        } else {
            finish()
        }
    }

    override fun onUserDeniedPermission(permissionName: String) {
        handleRequiredPermission(permissionName)
    }

    override fun onUserDisabledPermission(permissionName: String) {
        handleRequiredPermission(permissionName)
    }

    private fun notificationSlide(slideNumber: Int): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            addSlide(
                AppIntroFragment.newInstance(
                    title = getString(R.string.onboarding_notification_title),
                    description = getString(R.string.onboarding_notification_description),
                    imageDrawable = R.drawable.ic_onboarding_notification
                )
            )
            askForPermissions(
                permissions = arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                slideNumber = slideNumber,
                required = false
            )
            return true
        }
        return false
    }

    private fun scanSlide(slideNumber: Int): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            addSlide(
                AppIntroFragment.newInstance(
                    title = getString(R.string.onboarding_scan_title),
                    description = getString(R.string.onboarding_scan_description),
                    imageDrawable = R.drawable.ic_signal_searching
                )
            )
            askForPermissions(
                permissions = arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT),
                slideNumber = slideNumber,
                required = true
            )
            return true
        }
        return false
    }

    private fun connectSlide(slideNumber: Int): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            addSlide(
                AppIntroFragment.newInstance(
                    title = getString(R.string.onboarding_connect_title),
                    description = getString(R.string.onboarding_connect_description),
                    imageDrawable = R.drawable.ic_play_sound
                )
            )
            askForPermissions(
                permissions = arrayOf(Manifest.permission.BLUETOOTH_SCAN),
                slideNumber = slideNumber,
                required = true
            )
            return true
        }
        return false
    }

    private fun locationSlide(slideNumber: Int): Boolean {
        addSlide(
            AppIntroFragment.newInstance(
                title = getString(R.string.location_permission_title),
                description = getString(R.string.location_permission_message),
                imageDrawable = R.drawable.ic_location
            )
        )

        askForPermissions(
            permissions = arrayOf(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION
            ),
            required = true,
            slideNumber = slideNumber
        )
        return true
    }

    private fun backgroundLocationSlide(slideNumber: Int): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            addSlide(
                AppIntroFragment.newInstance(
                    title = getString(R.string.onboarding_4_title),
                    description = getString(R.string.onboarding_4_description),
                    imageDrawable = R.drawable.img_tracking_map
                )
            )

            askForPermissions(
                permissions = arrayOf(
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ),
                required = false,
                slideNumber = slideNumber
            )
            return true
        }
        return false
    }

    private fun buildSlides() {
        isIndicatorEnabled = true
        isWizardMode = true
        isSystemBackButtonLocked = true

        var slideNumber = 1

        setIndicatorColor(
            selectedIndicatorColor = Color.LTGRAY,
            unselectedIndicatorColor = Color.DKGRAY
        )

        addSlide(
            AppIntroFragment.newInstance(
                title = getString(R.string.app_name),
                description = getString(R.string.onboarding_1_description),
                imageDrawable = R.mipmap.ic_launcher
            )
        )

        if (scanSlide(slideNumber + 1)) {
            slideNumber = 2
        }

        locationSlide(slideNumber + 1)

        backgroundLocationSlide(slideNumber + 2)

        notificationSlide(slideNumber + 3)

        addSlide(IgnoreBatteryOptimizationFragment.newInstance())

        addSlide(ShareDataFragment.newInstance())

        addSlide(
            AppIntroFragment.newInstance(
                title = getString(R.string.onboarding_5_title),
                imageDrawable = R.drawable.ic_security_on
            )
        )
    }

    private fun handleRequiredPermission(permissionName: String) {
        if (permissionName == Manifest.permission.ACCESS_BACKGROUND_LOCATION) {
            SharedPrefs.useLocationInTrackingDetection = false
            goToNextSlide()
        } else if (permissionName == Manifest.permission.POST_NOTIFICATIONS) {
            goToNextSlide()
        } else if (dialog?.isShowing != true) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.permission_required)
                .setIcon(R.drawable.ic_baseline_error_outline_24)
                .setMessage(R.string.permission_required_message)
                .setPositiveButton(R.string.ok_button) { dialog, _ ->
                    dialog.dismiss()
                }
                .create()
                .also { dialog = it }
                .show()
        }
    }
}
