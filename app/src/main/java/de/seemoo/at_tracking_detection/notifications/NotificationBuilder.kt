package de.seemoo.at_tracking_detection.notifications

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.ui.TrackingActivity
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationBuilder @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private fun pendingNotificationIntent(bundle: Bundle): PendingIntent {
        val intent = Intent(context, TrackingActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            action = NotificationConstants.CLICKED_ACTION
            putExtras(bundle)
        }
        return PendingIntent.getActivity(
            context,
            NotificationConstants.CLICKED_CODE,
            intent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )
    }


    private fun packBundle(deviceAddress: String, notificationId: Int): Bundle = Bundle().apply {
        putString("deviceAddress", deviceAddress)
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
        return PendingIntent.getBroadcast(
            context,
            code,
            intent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )
    }

    fun buildTrackingNotification(
        deviceAddress: String,
        notificationId: Int
    ): Notification {
        Timber.d("Notification with id $notificationId for device $deviceAddress has been build!")
        val bundle: Bundle = packBundle(deviceAddress, notificationId)
        val notifyText = context.getString(R.string.notification_text)
        return NotificationCompat.Builder(context, NotificationConstants.CHANNEL_ID)
            .setContentTitle(context.getString(R.string.notification_title))
            .setContentText(notifyText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingNotificationIntent(bundle))
            .setCategory(Notification.CATEGORY_ALARM)
            .setSmallIcon(R.drawable.ic_warning)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notifyText))
            .addAction(
                R.drawable.ic_warning,
                context.getString(R.string.notification_false_alarm),
                buildPendingIntent(
                    bundle,
                    NotificationConstants.FALSE_ALARM_ACTION,
                    NotificationConstants.FALSE_ALARM_CODE
                )
            ).addAction(
                R.drawable.ic_warning,
                context.getString(R.string.notification_ignore_device),
                buildPendingIntent(
                    bundle,
                    NotificationConstants.IGNORE_DEVICE_ACTION,
                    NotificationConstants.IGNORE_DEVICE_CODE
                )
            ).setDeleteIntent(
                buildPendingIntent(
                    bundle,
                    NotificationConstants.DISMISSED_ACTION,
                    NotificationConstants.DISMISSED_CODE
                )
            ).setAutoCancel(true).build()

    }

    fun buildBluetoothErrorNotification(): Notification {
        val notificationId = -100
        val bundle: Bundle = Bundle().apply { putInt("notificationId", notificationId) }
        return NotificationCompat.Builder(context, NotificationConstants.CHANNEL_ID)
            .setContentTitle(context.getString(R.string.notification_title_ble_error))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingNotificationIntent(bundle))
            .setCategory(Notification.CATEGORY_ERROR)
            .setSmallIcon(R.drawable.ic_scan_icon)
            .setAutoCancel(true)
            .build()
    }
}