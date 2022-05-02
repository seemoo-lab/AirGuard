package de.seemoo.at_tracking_detection.notifications

import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import de.seemoo.at_tracking_detection.database.models.device.BaseDevice
import de.seemoo.at_tracking_detection.database.viewmodel.NotificationViewModel
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

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

    fun setup() {
        Timber.d("Setting up NotificationManager")
        // Register the channel with the system
        val channel = NotificationChannelCompat.Builder(
            NotificationConstants.CHANNEL_ID,
            NotificationManagerCompat.IMPORTANCE_DEFAULT
        )
            .setName(NotificationConstants.NOTIFICATION_CHANNEL_NAME)
            .build()

        notificationManagerCompat.createNotificationChannel(channel)
    }

    companion object {
        const val TRACKING_NOTIFICATION_TAG =
            "de.seemoo.at_tracking_detection.tracking_notification"
        const val BLE_SCAN_ERROR_TAG =
            "de.seemoo.at_tracking_detection.ble_scan_error_notification"
    }
}