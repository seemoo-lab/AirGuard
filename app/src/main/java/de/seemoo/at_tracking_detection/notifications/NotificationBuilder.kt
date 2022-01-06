package de.seemoo.at_tracking_detection.notifications

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
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
            putExtras(bundle)
        }
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
    }

    private fun pendingFalseAlarmIntent(bundle: Bundle): PendingIntent {
        val intent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationConstants.FALSE_ALARM_ACTION
            putExtras(bundle)
        }
        return PendingIntent.getBroadcast(
            context,
            NotificationConstants.FALSE_ALARM_CODE,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun pendingIgnoreDeviceIntent(bundle: Bundle): PendingIntent {
        val intent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationConstants.IGNORE_DEVICE_ACTION
            putExtras(bundle)
        }
        return PendingIntent.getBroadcast(
            context,
            NotificationConstants.IGNORE_DEVICE_CODE,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )
    }


    private fun packBundle(deviceAddress: String, notificationId: Int): Bundle = Bundle().apply {
        putString("deviceAddress", deviceAddress)
        putInt("notificationId", notificationId)
    }

    fun buildTrackingNotification(
        deviceAddress: String,
        notificationId: Int
    ): Notification {
        Timber.d("Notification with id $notificationId for device $deviceAddress has been build!")
        val bundle: Bundle = packBundle(deviceAddress, notificationId)
        val notifText = context.getString(R.string.notification_text)
        return NotificationCompat.Builder(context, NotificationConstants.CHANNEL_ID)
            .setContentTitle(context.getString(R.string.notification_title))
            .setContentText(notifText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingNotificationIntent(bundle))
            .setCategory(Notification.CATEGORY_ALARM)
            .setSmallIcon(R.drawable.ic_warning)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notifText))
            .addAction(
                R.drawable.ic_warning,
                "FALSE ALARM",
                pendingFalseAlarmIntent(bundle)
            ).addAction(
                R.drawable.ic_warning,
                "IGNORE DEVICE",
                pendingIgnoreDeviceIntent(bundle)
            ).setAutoCancel(true).build()

    }
}