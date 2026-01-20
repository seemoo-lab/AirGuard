package de.seemoo.at_tracking_detection.ui

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import com.github.appintro.AppIntro
import com.github.appintro.AppIntroFragment
import com.github.appintro.model.SliderPage
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.ui.onboarding.BackgroundLocationFragment
import de.seemoo.at_tracking_detection.ui.onboarding.IgnoreBatteryOptimizationFragment
import de.seemoo.at_tracking_detection.ui.onboarding.LocationFragment
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

        enableEdgeToEdge()

        if (SharedPrefs.preventScreenshots) {
            window.setFlags(
                android.view.WindowManager.LayoutParams.FLAG_SECURE,
                android.view.WindowManager.LayoutParams.FLAG_SECURE
            )
        }

        // Apply System Insets to Content View to prevent navigation bar overlap
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(
                left = systemBars.left,
                right = systemBars.right,
                bottom = systemBars.bottom,
                top = systemBars.top
            )
            insets
        }

        // Setup AppIntro UI Colors (Fixes White on White issue)
        val primaryColor = ContextCompat.getColor(this, R.color.md_theme_primary)
        val onSurfaceColor = ContextCompat.getColor(this, R.color.md_theme_onSurface)

        setNextArrowColor(onSurfaceColor)
        setBackArrowColor(onSurfaceColor)
        setColorDoneText(primaryColor)
        setColorSkipButton(onSurfaceColor)
        setIndicatorColor(
            selectedIndicatorColor = primaryColor,
            unselectedIndicatorColor = ContextCompat.getColor(this, R.color.md_theme_outline)
        )
        setSeparatorColor(Color.TRANSPARENT)

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
            WindowCompat.getInsetsController(window, window.decorView).apply {
                show(WindowInsetsCompat.Type.systemBars())
                isAppearanceLightStatusBars = true
                isAppearanceLightNavigationBars = true
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to configure system bars in onResume")
        }
    }

    override fun onDonePressed(currentFragment: Fragment?) {
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

    private fun createM3Slide(
        title: String,
        description: String,
        imageDrawable: Int
    ): AppIntroFragment {
        val sliderPage = SliderPage()
        sliderPage.title = title
        sliderPage.description = description
        sliderPage.imageDrawable = imageDrawable
        sliderPage.backgroundColorRes = R.color.md_theme_background
        sliderPage.titleColorRes = R.color.md_theme_onSurface
        sliderPage.descriptionColorRes = R.color.md_theme_onSurface
        return AppIntroFragment.createInstance(sliderPage)
    }

    private fun notificationSlide(slideNumber: Int): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            addSlide(
                createM3Slide(
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
                createM3Slide(
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
                createM3Slide(
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
        addSlide(LocationFragment.newInstance())
        return true
    }

    private fun backgroundLocationSlide(slideNumber: Int): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            addSlide(BackgroundLocationFragment.newInstance())
            return true
        }
        return false
    }

    private fun buildSlides() {
        isIndicatorEnabled = true
        isWizardMode = true
        isSystemBackButtonLocked = true

        var slideNumber = 1

        addSlide(
            createM3Slide(
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
            createM3Slide(
                title = getString(R.string.onboarding_5_title),
                description = "", // Empty description if not needed
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