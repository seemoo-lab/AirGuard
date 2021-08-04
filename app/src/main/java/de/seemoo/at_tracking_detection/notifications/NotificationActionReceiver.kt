package de.seemoo.at_tracking_detection.notifications

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import de.seemoo.at_tracking_detection.worker.BackgroundWorkScheduler
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {
    @Inject
    lateinit var backgroundWorkScheduler: BackgroundWorkScheduler

    @Inject
    lateinit var notificationManager: NotificationManager

    override fun onReceive(context: Context, intent: Intent) {
        Timber.d("Intent received!")
        when (intent.action) {
            NotificationConstants.FALSE_ALARM_ACTION -> {
                val notificationId = intent.getIntExtra("notificationId", -1)
                if (notificationId == -1) {
                    Timber.e("Notification id missing!")
                    return
                }
                Timber.d("False Alarm intent for notification $notificationId received")
                backgroundWorkScheduler.scheduleFalseAlarm(notificationId)
                notificationManager.cancel(notificationId)
            }
            NotificationConstants.IGNORE_DEVICE_ACTION -> {
                val notificationId = intent.getIntExtra("notificationId", -1)
                val deviceAddress = intent.getStringExtra("deviceAddress")
                if (deviceAddress == null || notificationId == -1) {
                    Timber.e("Notification ID or device address missing!")
                    return
                }
                Timber.d("Ignore Device intent for device $deviceAddress received")
                backgroundWorkScheduler.scheduleIgnoreDevice(deviceAddress, notificationId)
                notificationManager.cancel(notificationId)
            }
        }
    }
}

