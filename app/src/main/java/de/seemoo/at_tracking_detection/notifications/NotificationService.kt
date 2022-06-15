package de.seemoo.at_tracking_detection.notifications

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication
import de.seemoo.at_tracking_detection.BuildConfig
import de.seemoo.at_tracking_detection.database.models.device.BaseDevice
import de.seemoo.at_tracking_detection.database.models.device.DeviceType
import de.seemoo.at_tracking_detection.database.viewmodel.NotificationViewModel
import de.seemoo.at_tracking_detection.util.SharedPrefs
import timber.log.Timber
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

@Singleton
class NotificationService @Inject constructor(
    private val notificationManagerCompat: NotificationManagerCompat,
    private val notificationBuilder: NotificationBuilder,
    private val notificationViewModel: NotificationViewModel
) {

    suspend fun sendTrackingNotification(deviceAddress: String) {
        val notificationId = notificationViewModel.insert(deviceAddress)
        with(notificationManagerCompat) {
            notify(
                TRACKING_NOTIFICATION_TAG,
                notificationId,
                notificationBuilder.buildTrackingNotification(deviceAddress, notificationId)
            )
        }
    }

    suspend fun sendTrackingNotification(baseDevice: BaseDevice) {
        val notificationId = notificationViewModel.insert(deviceAddress = baseDevice.address)
        with(notificationManagerCompat) {
            notify(
                TRACKING_NOTIFICATION_TAG,
                notificationId,
                notificationBuilder.buildTrackingNotification(baseDevice, notificationId)
            )
        }
    }

    fun sendBLEErrorNotification() {
        with(notificationManagerCompat) {
            notify(
                BLE_SCAN_ERROR_TAG,
                -100,
                notificationBuilder.buildBluetoothErrorNotification()
            )
        }
    }

    fun sendSurveyInfoNotification() {
        with(notificationManagerCompat) {

            notify(
                SURVEY_INFO_TAG,
                -101,
                notificationBuilder.buildSurveyInfoNotification()
            )
        }
        SharedPrefs.surveyNotficationSent = true
    }

    fun sendDebugNotificationFoundDevice(scanResult: ScanResult) {
        with(notificationManagerCompat) {
            notify(
                BLE_SCAN_ERROR_TAG,
                Random.nextInt(),
                notificationBuilder.buildDebugFoundDeviceNotification(scanResult)
                )
        }
    }


    @SuppressLint("UnspecifiedImmutableFlag")
    fun scheduleSurveyNotification(replace: Boolean) {
        // Do not send multiple notifications
        if (!ATTrackingDetectionApplication.SURVEY_IS_RUNNING) {return}

        if (SharedPrefs.surveyNotficationSent && !replace) {return}
        //Check if already scheduled
        val notificationDate = SharedPrefs.surveyNotificationDate
        if ( replace || notificationDate == null || notificationDate < LocalDateTime.now()) {
            //Notification should be at least one day in the future and up to 5 days
            var timeInMillisUntilNotification: Long = Random(6544).nextLong(24 * 60 * 60 * 1000, 5 * 24 * 60 * 60 * 1000)
            if (BuildConfig.DEBUG) {
                // 10s to 60s
                timeInMillisUntilNotification = Random(6544).nextLong(10 * 1000, 60 * 1000)
            }
            val alarmTime = System.currentTimeMillis() + timeInMillisUntilNotification
            val dateForNotification = LocalDateTime.now().plus(timeInMillisUntilNotification, ChronoUnit.MILLIS)
            SharedPrefs.surveyNotificationDate = dateForNotification

            val intent = Intent(ATTrackingDetectionApplication.getAppContext(), ScheduledNotificationReceiver::class.java)

            val pendingIntent = if (Build.VERSION.SDK_INT >= 31) {
                PendingIntent.getBroadcast(ATTrackingDetectionApplication.getAppContext(), -102,intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
            }else {
                PendingIntent.getBroadcast(ATTrackingDetectionApplication.getAppContext(), -102, intent, PendingIntent.FLAG_UPDATE_CURRENT)
            }

            val alarmManager = ATTrackingDetectionApplication.getAppContext().getSystemService(Context.ALARM_SERVICE) as AlarmManager

            alarmManager.set(AlarmManager.RTC_WAKEUP, alarmTime, pendingIntent)
            Timber.d("Scheduled a survey reminder notificaiton at $dateForNotification")
        }
    }

    fun setup() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Timber.d("Setting up NotificationManager")
            // Register the channel with the system
            val channel = NotificationChannelCompat.Builder(
                NotificationConstants.CHANNEL_ID,
                NotificationManagerCompat.IMPORTANCE_HIGH
            )
                .setName(NotificationConstants.NOTIFICATION_CHANNEL_NAME)
                .build()

            notificationManagerCompat.createNotificationChannel(channel)

            //Register the info channel
            val infoChannel = NotificationChannelCompat.Builder(
                NotificationConstants.INFO_CHANNEL_ID,
                NotificationManagerCompat.IMPORTANCE_LOW
            )
                .setName(NotificationConstants.NOTIFICATION_CHANNEL_INFO)
                .build()

            notificationManagerCompat.createNotificationChannel(infoChannel)
        }
    }

    companion object {
        const val TRACKING_NOTIFICATION_TAG =
            "de.seemoo.at_tracking_detection.tracking_notification"
        const val BLE_SCAN_ERROR_TAG =
            "de.seemoo.at_tracking_detection.ble_scan_error_notification"
        const val SURVEY_INFO_TAG = "de.seemoo.at_tracking_detection.survey_info"
    }
}