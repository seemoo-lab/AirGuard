package de.seemoo.at_tracking_detection.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import de.seemoo.at_tracking_detection.database.viewmodel.NotificationViewModel
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationService @Inject constructor(
    @ApplicationContext private val context: Context,
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

    fun setup() {
        Timber.d("Setting up NotificationManager")
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel(
            NotificationConstants.CHANNEL_ID,
            NotificationConstants.NOTIFICATION_CHANNEL_NAME,
            importance
        )
        // Register the channel with the system
        val notificationManager: NotificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    companion object {
        const val TRACKING_NOTIFICATION_TAG =
            "de.seemoo.at_tracking_detection.tracking_notification"
    }
}