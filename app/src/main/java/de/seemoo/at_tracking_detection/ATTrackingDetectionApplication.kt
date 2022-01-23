package de.seemoo.at_tracking_detection

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.google.android.material.color.DynamicColors
import dagger.hilt.android.HiltAndroidApp
import de.seemoo.at_tracking_detection.notifications.NotificationService
import de.seemoo.at_tracking_detection.ui.OnboardingActivity
import de.seemoo.at_tracking_detection.util.ATTDLifecycleCallbacks
import de.seemoo.at_tracking_detection.util.Util
import de.seemoo.at_tracking_detection.worker.BackgroundWorkScheduler
import fr.bipi.tressence.file.FileLoggerTree
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import timber.log.Timber
import java.io.File


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
            .setMinimumLoggingLevel(Log.DEBUG)
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        instance = this
        super.onCreate()

        Timber.plant(Timber.DebugTree())
        Timber.d("Tree planted")

        val logFilePath = filesDir.path + "/logs.log"
        val logFile = File(logFilePath).createNewFile()

        if (BuildConfig.DEBUG) {
            // We use this to access our logs from a file for on device debugging
            val t: Timber.Tree = FileLoggerTree.Builder()
                .withSizeLimit(2_000_000)
                .withDir(filesDir)
                .withFileName("logs.log")
                .withMinPriority(Log.VERBOSE)
                .appendToFile(true)
                .build()

            Timber.plant(t)
            Timber.v("File tree planted")
        }

        DynamicColors.applyToActivitiesIfAvailable(this)

        registerActivityLifecycleCallbacks(activityLifecycleCallbacks)

        Util.setSelectedTheme(sharedPreferences)

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

    private fun hasPermissions(): Boolean {
        val requiredPermissions = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requiredPermissions.add(Manifest.permission.BLUETOOTH_SCAN)
        }

        for (permission in requiredPermissions) {
            val granted = ContextCompat.checkSelfPermission(
                applicationContext,
                permission
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                return false
            }
        }
        return true
    }

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