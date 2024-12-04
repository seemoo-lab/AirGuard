package de.seemoo.at_tracking_detection.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.AndroidEntryPoint
import de.seemoo.at_tracking_detection.database.repository.DeviceRepository
import de.seemoo.at_tracking_detection.database.repository.NotificationRepository
import de.seemoo.at_tracking_detection.worker.BackgroundWorkScheduler
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {
    @Inject
    lateinit var backgroundWorkScheduler: BackgroundWorkScheduler

    @Inject
    lateinit var notificationManagerCompat: NotificationManagerCompat

    @Inject
    lateinit var notificationRepository: NotificationRepository

    @Inject
    lateinit var deviceRepository: DeviceRepository

    @OptIn(DelicateCoroutinesApi::class)
    override fun onReceive(context: Context, intent: Intent) {
        Timber.d("Intent received!")
        val notificationId = intent.getIntExtra("notificationId", -1)
        if (notificationId == -1) {
            Timber.e("Notification id missing!")
            return
        }
        when (intent.action) {
            NotificationConstants.FALSE_ALARM_ACTION -> {
                Timber.d("False Alarm intent for notification $notificationId received")
                GlobalScope.launch(Dispatchers.IO) {
                    notificationRepository.setFalseAlarm(notificationId, true)
                }
                notificationManagerCompat.cancel(notificationId)
            }
            NotificationConstants.IGNORE_DEVICE_ACTION -> {
                val deviceAddress = intent.getStringExtra("deviceAddress")
                if (deviceAddress == null) {
                    Timber.e("Device address missing!")
                    return
                }
                Timber.d("Ignore Device intent for device $deviceAddress received")
                GlobalScope.launch(Dispatchers.IO) {
                    deviceRepository.setIgnoreFlag(deviceAddress, true)
                }
                notificationManagerCompat.cancel(notificationId)
            }
            NotificationConstants.CLICKED_ACTION -> {
                GlobalScope.launch(Dispatchers.IO) {
                    notificationRepository.setClicked(notificationId, true)
                }
            }
            NotificationConstants.DISMISSED_ACTION -> {
                GlobalScope.launch(Dispatchers.IO) {
                    notificationRepository.setDismissed(notificationId, true)
                }
            }
        }
        notificationManagerCompat.cancel(notificationId)
    }
}

