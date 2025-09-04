package de.seemoo.at_tracking_detection.notifications

import android.app.Notification
import android.app.PendingIntent
import android.app.TaskStackBuilder
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.database.models.device.BaseDevice
import de.seemoo.at_tracking_detection.database.models.device.ConnectionState
import de.seemoo.at_tracking_detection.database.models.device.DeviceManager
import de.seemoo.at_tracking_detection.database.models.device.DeviceType
import de.seemoo.at_tracking_detection.database.models.device.types.GoogleFindMyNetworkType
import de.seemoo.at_tracking_detection.database.models.device.types.SamsungTrackerType
import de.seemoo.at_tracking_detection.ui.TrackingNotificationActivity
import de.seemoo.at_tracking_detection.util.SharedPrefs
import de.seemoo.at_tracking_detection.util.risk.RiskLevelEvaluator
import timber.log.Timber
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationBuilder @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private fun pendingNotificationIntent(bundle: Bundle, notificationId: Int): PendingIntent {
        val intent = Intent(context, TrackingNotificationActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            action = NotificationConstants.CLICKED_ACTION
            putExtras(bundle)
        }
        val context = ATTrackingDetectionApplication.getCurrentActivity() ?: ATTrackingDetectionApplication.getAppContext()
        val resultPendingIntent: PendingIntent = TaskStackBuilder.create(context).run {
            addNextIntentWithParentStack(intent)
            var flags = PendingIntent.FLAG_UPDATE_CURRENT
            // For S+ the FLAG_IMMUTABLE or FLAG_MUTABLE must be set
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // This prevents security issues that some apps can execute code on behalf of our app
                flags = flags or PendingIntent.FLAG_IMMUTABLE
            }
            getPendingIntent(notificationId, flags)
        }
        return resultPendingIntent

    }

//    private fun pendingIntentMainActivity(): PendingIntent {
//        val intent = Intent(context, MainActivity::class.java).apply {
//            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
//            action = NotificationConstants.CLICKED_ACTION
//        }
//
//        val context = ATTrackingDetectionApplication.getCurrentActivity() ?: ATTrackingDetectionApplication.getAppContext()
//        val resultPendingIntent: PendingIntent = TaskStackBuilder.create(context).run {
//            addNextIntentWithParentStack(intent)
//            var flags = PendingIntent.FLAG_UPDATE_CURRENT
//            // For S+ the FLAG_IMMUTABLE or FLAG_MUTABLE must be set
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
//                flags = flags or PendingIntent.FLAG_IMMUTABLE
//            }
//            getPendingIntent(1654, flags)
//        }
//        return resultPendingIntent
//
//    }


    private fun packBundle(deviceAddress: String, deviceTypeString: String, notificationId: Int): Bundle = Bundle().apply {
        putString("deviceAddress", deviceAddress)
        putString("deviceTypeAsString", deviceTypeString)
        putInt("notificationId", notificationId)
    }

    private fun buildPendingIntent(
        bundle: Bundle,
        notificationAction: String,
        code: Int
    ): PendingIntent {
        val intent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = notificationAction
            putExtras(bundle)
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        return PendingIntent.getBroadcast(
            context,
            code,
            intent,
            flags
        )
    }

    fun buildTrackingNotification(
        baseDevice: BaseDevice,
        notificationId: Int
    ): Notification {
        Timber.d("Notification with id $notificationId for device ${baseDevice.address} has been build!")

        val deviceAddress = baseDevice.address
        val deviceTypeString: String = baseDevice.deviceType?.let {
            DeviceManager.deviceTypeToString(
                it
            )
        }
            ?: "UNKNOWN"

        val bundle: Bundle = packBundle(deviceAddress, deviceTypeString, notificationId)
        val device = baseDevice.device
        val notificationText: String
        val notificationTitle: String
        when (baseDevice.deviceType ?: DeviceType.UNKNOWN) {
            DeviceType.AIRTAG, DeviceType.APPLE, DeviceType.AIRPODS, DeviceType.UNKNOWN -> {
                notificationTitle = context.getString(R.string.notification_title_vocal, device.deviceContext.defaultDeviceName )
                notificationText = if (baseDevice.deviceType == DeviceType.AIRPODS) {
                    context.getString(R.string.notification_text_multiple, device.deviceContext.defaultDeviceName, RiskLevelEvaluator.getMinutesAtLeastTrackedBeforeAlarm())
                }else {
                    context.getString(R.string.notification_text_single, device.deviceContext.defaultDeviceName, RiskLevelEvaluator.getMinutesAtLeastTrackedBeforeAlarm())
                }
            }
            DeviceType.SAMSUNG_TRACKER -> {
                val subType = SamsungTrackerType.stringToSubType(baseDevice.subDeviceType)
                val deviceName = SamsungTrackerType.visibleStringFromSubtype(subType)
                notificationTitle = context.getString(R.string.notification_title_consonant, deviceName )
                notificationText = context.getString(R.string.notification_text_single, deviceName, RiskLevelEvaluator.getMinutesAtLeastTrackedBeforeAlarm())
            }
            DeviceType.GOOGLE_FIND_MY_NETWORK -> {
                val subType = GoogleFindMyNetworkType.stringToSubType(baseDevice.subDeviceType)
                val deviceName = GoogleFindMyNetworkType.visibleStringFromSubtype(subType)
                notificationTitle = context.getString(R.string.notification_title_consonant, deviceName )
                notificationText = context.getString(R.string.notification_text_single, deviceName, RiskLevelEvaluator.getMinutesAtLeastTrackedBeforeAlarm())
            }
            else -> {
                notificationTitle = context.getString(R.string.notification_title_consonant, device.deviceContext.defaultDeviceName )
                notificationText =  context.getString(R.string.notification_text_single, device.deviceContext.defaultDeviceName, RiskLevelEvaluator.getMinutesAtLeastTrackedBeforeAlarm())
            }
        }

        val pendingIntent = pendingNotificationIntent(bundle, notificationId)

        var notification = NotificationCompat.Builder(context, NotificationConstants.CHANNEL_ID)
            .setContentTitle(notificationTitle)
            .setContentText(notificationText)
            .setPriority(getNotificationPriority())
            .setContentIntent(pendingIntent)
            .setCategory(getNotificationCategory())
            .setSmallIcon(R.drawable.ic_warning)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notificationText))
            .addAction(
                R.drawable.ic_warning,
                context.getString(R.string.notification_false_alarm),
                buildPendingIntent(
                    bundle,
                    NotificationConstants.FALSE_ALARM_ACTION,
                    NotificationConstants.FALSE_ALARM_CODE
                )
            ).setAutoCancel(true)

        if (baseDevice.deviceType != null && baseDevice.deviceType.canBeIgnored(ConnectionState.OVERMATURE_OFFLINE)) {
            notification = notification.addAction(
                R.drawable.ic_warning,
                context.getString(R.string.notification_ignore_device),
                buildPendingIntent(
                    bundle,
                    NotificationConstants.IGNORE_DEVICE_ACTION,
                    NotificationConstants.IGNORE_DEVICE_CODE
                )
            ).setAutoCancel(true)
        }

        notification = notification.setDeleteIntent(
            buildPendingIntent(
                bundle,
                NotificationConstants.DISMISSED_ACTION,
                NotificationConstants.DISMISSED_CODE
            )
        ).setAutoCancel(true)

        return notification.build()
    }

    fun buildObserveTrackerNotification(
        deviceAddress: String,
        deviceTypeString: String,
        notificationId: Int,
        observationDuration: Long,
        observationPositive: Boolean
    ): Notification {
        Timber.d("Notification with id $notificationId for device $deviceAddress has been build!")
        val bundle: Bundle = packBundle(deviceAddress, deviceTypeString, notificationId)

        val notifyText = if (observationPositive) {
            context.resources.getQuantityString(
                R.plurals.notification_observe_tracker_positive,
                observationDuration.toInt(),
                observationDuration
            )
        } else {
            context.getString(
                R.string.notification_observe_tracker_negative,
            )
        }

        var notification = NotificationCompat.Builder(context, NotificationConstants.CHANNEL_ID)
            .setContentTitle(context.getString(R.string.notification_observe_tracker_title_base))
            .setContentText(notifyText)
            .setPriority(getNotificationPriority())
            .setContentIntent(pendingNotificationIntent(bundle, notificationId))
            .setCategory(getNotificationCategory())
            .setSmallIcon(R.drawable.ic_warning)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notifyText))

        notification = notification.setDeleteIntent(
            buildPendingIntent(
                bundle,
                NotificationConstants.DISMISSED_ACTION,
                NotificationConstants.DISMISSED_CODE
            )
        ).setAutoCancel(true)

        return notification.build()

    }

    fun buildObserveTrackerFailedNotification(notificationId: Int): Notification {
        val bundle: Bundle = Bundle().apply { putInt("notificationId", notificationId) }

        return NotificationCompat.Builder(context, NotificationConstants.CHANNEL_ID)
            .setContentTitle(context.getString(R.string.notification_observe_tracker_title_base))
            .setContentText(context.getString(R.string.notification_observe_tracker_error))
            .setPriority(getNotificationPriority())
            .setContentIntent(pendingNotificationIntent(bundle, notificationId))
            .setCategory(Notification.CATEGORY_ERROR)
            .setSmallIcon(R.drawable.ic_scan_icon)
            .setAutoCancel(true)
            .build()
    }

    fun buildBluetoothErrorNotification(): Notification {
        val notificationId = -100
        val bundle: Bundle = Bundle().apply { putInt("notificationId", notificationId) }

        return NotificationCompat.Builder(context, NotificationConstants.CHANNEL_ID)
            .setContentTitle(context.getString(R.string.notification_title_ble_error))
            .setPriority(getNotificationPriority())
            .setContentIntent(pendingNotificationIntent(bundle, notificationId))
            .setCategory(Notification.CATEGORY_ERROR)
            .setSmallIcon(R.drawable.ic_scan_icon)
            .setAutoCancel(true)
            .build()
    }

    fun buildDebugFoundDeviceNotification(scanResult: android.bluetooth.le.ScanResult): Notification {

        val deviceType = DeviceManager.getDeviceType(scanResult)

        val millisecondsSinceEvent = (SystemClock.elapsedRealtimeNanos() - scanResult.timestampNanos) / 1000000L
        val timeOfEvent = System.currentTimeMillis() - millisecondsSinceEvent
        val eventDate = Instant.ofEpochMilli(timeOfEvent).atZone(ZoneId.systemDefault()).toLocalDateTime()


        return NotificationCompat.Builder(context, NotificationConstants.INFO_CHANNEL_ID)
            .setContentTitle("Discovered ${deviceType.name} | ${scanResult.device.address}")
            .setContentText("Received at $eventDate")
            .setPriority(getNotificationPriority())
            .setCategory(Notification.CATEGORY_STATUS)
            .setSmallIcon(R.drawable.ic_scan_icon)
            .setAutoCancel(true)
            .build()
    }

    /*
    fun buildSurveyInfoNotification(): Notification {
        val context = ATTrackingDetectionApplication.getAppContext()
        val text = context.getString(R.string.survey_info_1) + " " + context.getString(R.string.survey_info_2) + " " + context.getString(R.string.survey_info_3)

        val pendingIntent = pendingIntentMainActivity()

        return NotificationCompat.Builder(context, NotificationConstants.INFO_CHANNEL_ID)
            .setContentTitle("AirGuard - " + context.getString(R.string.survey_card_title))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(Notification.CATEGORY_STATUS)
            .setSmallIcon(R.drawable.ic_edit_48px)
            .setContentIntent(pendingIntent)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .build()
    }
     */

    private fun getNotificationPriority(): Int {
        return if (SharedPrefs.notificationPriorityHigh){
            NotificationCompat.PRIORITY_MAX
        } else {
            NotificationCompat.PRIORITY_HIGH
        }
    }

    private fun getNotificationCategory(): String {
        return if (SharedPrefs.notificationPriorityHigh){
            Notification.CATEGORY_ALARM
        } else {
            Notification.CATEGORY_STATUS
        }
    }
}