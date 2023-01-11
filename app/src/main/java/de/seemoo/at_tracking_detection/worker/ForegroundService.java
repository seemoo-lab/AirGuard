package de.seemoo.at_tracking_detection.worker;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

public class ForegroundService extends Service {

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        String serviceChannelId = "de_seemoo_at_tracking_detection_s";
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel mChannel = new NotificationChannel(serviceChannelId, "Tracker Service", NotificationManager.IMPORTANCE_NONE);
            mChannel.setDescription("Tracker Detector Service");
            notificationManager.createNotificationChannel(mChannel);
        }

        Notification newNotification =
                new NotificationCompat.Builder(ForegroundService.this, serviceChannelId)
                        .setSmallIcon(android.R.drawable.ic_dialog_map)
                        .setContentTitle("Tracker Detector Service")
                        .setContentText("Enables full rate background GPS")
                        .build();

        startForeground(88811, newNotification);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }
}
