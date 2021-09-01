package de.seemoo.at_tracking_detection.ui

import android.Manifest
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.github.appintro.AppIntro
import com.github.appintro.AppIntroFragment
import dagger.hilt.android.AndroidEntryPoint
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.ui.onboarding.BackgroundLocationFragment
import de.seemoo.at_tracking_detection.ui.onboarding.IgnoreBatteryOptimizationFragment
import de.seemoo.at_tracking_detection.ui.onboarding.LocationFragment
import de.seemoo.at_tracking_detection.ui.onboarding.ShareDataFragment
import de.seemoo.at_tracking_detection.worker.BackgroundWorkScheduler
import javax.inject.Inject

@AndroidEntryPoint
class OnboardingActivity : AppIntro() {

    @Inject
    lateinit var sharedPreferences: SharedPreferences

    @Inject
    lateinit var backgroundWorkScheduler: BackgroundWorkScheduler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildSlides()
    }

    override fun onDonePressed(currentFragment: Fragment?) {
        sharedPreferences.edit().putBoolean("onboarding_completed", true).apply()
        backgroundWorkScheduler.launch()
        finish()
        startActivity(Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
    }

    override fun onUserDeniedPermission(permissionName: String) {
        if (permissionName == Manifest.permission.ACCESS_BACKGROUND_LOCATION) {
            sharedPreferences.edit().putBoolean("use_location", false).apply()
        } else {
            handleRequiredPermission()
        }
    }

    private fun buildSlides() {
        isIndicatorEnabled = true
        isWizardMode = true
        isSystemBackButtonLocked = true

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

        addSlide(LocationFragment.newInstance())

        addSlide(IgnoreBatteryOptimizationFragment.newInstance())

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            addSlide(
                BackgroundLocationFragment.newInstance()
            )
        }
        addSlide(ShareDataFragment.newInstance())
        addSlide(
            AppIntroFragment.newInstance(
                title = getString(R.string.onboarding_5_title),
                imageDrawable = R.drawable.ic_baseline_check_256_green
            )
        )
    }

    private fun handleRequiredPermission() =
        AlertDialog.Builder(applicationContext).setTitle(R.string.permission_required)
            .setIcon(R.drawable.ic_baseline_error_outline_24)
            .setMessage(R.string.permission_required_message)
            .setPositiveButton(R.string.ok_button) { _: DialogInterface, _: Int ->
                finish()
            }.create()
}