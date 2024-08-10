package de.seemoo.at_tracking_detection

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.google.android.material.color.DynamicColors
import dagger.hilt.android.HiltAndroidApp
import de.seemoo.at_tracking_detection.database.repository.BeaconRepository
import de.seemoo.at_tracking_detection.database.repository.DeviceRepository
import de.seemoo.at_tracking_detection.database.repository.LocationRepository
import de.seemoo.at_tracking_detection.database.repository.NotificationRepository
import de.seemoo.at_tracking_detection.database.repository.ScanRepository
import de.seemoo.at_tracking_detection.detection.LocationProvider
import de.seemoo.at_tracking_detection.notifications.NotificationService
import de.seemoo.at_tracking_detection.ui.OnboardingActivity
import de.seemoo.at_tracking_detection.util.ATTDLifecycleCallbacks
import de.seemoo.at_tracking_detection.util.SharedPrefs
import de.seemoo.at_tracking_detection.util.Utility
import de.seemoo.at_tracking_detection.worker.BackgroundWorkScheduler
import de.seemoo.at_tracking_detection.worker.SetExactAlarmPermissionChangedReceiver
import fr.bipi.tressence.file.FileLoggerTree
import timber.log.Timber
import java.io.File
import java.time.LocalDateTime
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

    @Inject
    lateinit var deviceRepository: DeviceRepository

    @Inject
    lateinit var locationRepository: LocationRepository

    @Inject
    lateinit var beaconRepository: BeaconRepository

    @Inject
    lateinit var notificationRepository: NotificationRepository

    @Inject
    lateinit var locationProvider: LocationProvider

    @Inject
    lateinit var scanRepository: ScanRepository

    private val activityLifecycleCallbacks = ATTDLifecycleCallbacks()

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(Log.DEBUG)
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        instance = this
        super.onCreate()

        Timber.plant(Timber.DebugTree())
        Timber.d("Tree planted")

        if (BuildConfig.DEBUG) {
            // We use this to access our logs from a file for on device debugging
            File(filesDir.path + "/logs.log").createNewFile()
            val t: Timber.Tree = FileLoggerTree.Builder()
                .withSizeLimit(500_000)
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

        Utility.setSelectedTheme(sharedPreferences)

        if (showOnboarding() or !hasPermissions()) {
            startOnboarding()
        }else {
            backgroundWorkScheduler.launch()
        }

        if (SharedPrefs.shareData) {
            backgroundWorkScheduler.scheduleShareData()
        }

        if (SharedPrefs.lastDataDonation == null) {
            SharedPrefs.lastDataDonation = LocalDateTime.now()
        }

        notificationService.setup()
        notificationService.scheduleSurveyNotification(false)
        BackgroundWorkScheduler.scheduleAlarmWakeupIfScansFail()

        registerBroadcastReceiver()

        if (BuildConfig.DEBUG) {
//            // Get a location for testing
//            Timber.d("Request location")
//            val startTime = Date()
//            val locationRequester: LocationRequester = object  : LocationRequester() {
//                override fun receivedAccurateLocationUpdate(location: Location) {
//                    val endTime = Date()
//                    val duration = (endTime.time - startTime.time) / 1000
//                    Timber.d("Got location $location after $duration s")
//                }
//            }
//            val location =  locationProvider.lastKnownOrRequestLocationUpdates(locationRequester, 20_000L)
//            if (location != null) {
//                Timber.d("Using last known location")
//            }
//
//            // Printing time zone and user agent
//            Timber.d("Timezone: ${Api.TIME_ZONE} useragent ${Api.USER_AGENT}")
        }
    }


    private fun showOnboarding(): Boolean = !SharedPrefs.onBoardingCompleted or SharedPrefs.showOnboarding

    private fun hasPermissions(): Boolean {
        val requiredPermissions = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requiredPermissions.add(Manifest.permission.BLUETOOTH_SCAN)
            requiredPermissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requiredPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
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

    private fun registerBroadcastReceiver() {
        if (Build.VERSION.SDK_INT >= 31) {
            val br = SetExactAlarmPermissionChangedReceiver()
            val filter =
                IntentFilter(AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED)
            val flags = ContextCompat.RECEIVER_NOT_EXPORTED
            ContextCompat.registerReceiver(this, br, filter, flags)
        }
    }

    companion object {
        private lateinit var instance: ATTrackingDetectionApplication
        fun getAppContext(): Context = instance.applicationContext
        fun getCurrentActivity(): Activity? {
            return try {
                instance.activityLifecycleCallbacks.currentActivity
            }catch (e: UninitializedPropertyAccessException) {
                Timber.e("Failed accessing current activity $e")
                null
            }
        }
        fun getCurrentApp(): ATTrackingDetectionApplication {
            return instance
        }
        //TODO: Add real survey URL
        const val SURVEY_URL = "https://survey.seemoo.tu-darmstadt.de/index.php/117478?G06Q39=AirGuardAppAndroid&newtest=Y&lang=en"
        const val SURVEY_IS_RUNNING = false
    }
}