package de.seemoo.at_tracking_detection

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import de.seemoo.at_tracking_detection.notifications.NotificationService
import de.seemoo.at_tracking_detection.ui.OnboardingActivity
import de.seemoo.at_tracking_detection.util.ATTDLifecycleCallbacks
import de.seemoo.at_tracking_detection.worker.BackgroundWorkScheduler
import timber.log.Timber
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltAndroidApp
class ATTrackingDetectionApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var backgroundWorkScheduler: BackgroundWorkScheduler

    @Inject
    lateinit var notificationService: NotificationService

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var sharedPreferences: SharedPreferences

    private val activityLifecycleCallbacks = ATTDLifecycleCallbacks()

    override fun getWorkManagerConfiguration() =
        Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.DEBUG)
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        instance = this
        super.onCreate()

        Timber.plant(Timber.DebugTree())
        Timber.d("Tree planted")

        registerActivityLifecycleCallbacks(activityLifecycleCallbacks)

        if (showOnboarding() or !hasPermissions()) {
            startOnboarding()
        }

        if (sharedPreferences.getBoolean("share_data", false)) {
            backgroundWorkScheduler.scheduleShareData()
        }

        if (sharedPreferences.getString("lastDataDonation", null) == null) {
            sharedPreferences.edit()
                .putString(
                    "lastDataDonation",
                    LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                ).apply()
        }

        notificationService.setup()
        backgroundWorkScheduler.launch()
    }

    private fun showOnboarding(): Boolean = !sharedPreferences.getBoolean(
        "onboarding_completed",
        false
    ) or sharedPreferences.getBoolean("show_onboarding", false)

    private fun hasPermissions(): Boolean = ContextCompat.checkSelfPermission(
        applicationContext,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    private fun startOnboarding() =
        startActivity(Intent(applicationContext, OnboardingActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })

    companion object {
        private lateinit var instance: ATTrackingDetectionApplication
        fun getAppContext(): Context = instance.applicationContext
        fun getCurrentActivity(): Activity {
            return instance.activityLifecycleCallbacks.currentActivity
        }
    }
}