package de.seemoo.at_tracking_detection.hilt

import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import android.location.LocationManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import androidx.preference.PreferenceManager
import androidx.work.WorkManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import de.seemoo.at_tracking_detection.database.repository.BeaconRepository
import de.seemoo.at_tracking_detection.database.repository.DeviceRepository
import de.seemoo.at_tracking_detection.util.risk.RiskLevelEvaluator
import de.seemoo.at_tracking_detection.util.worker.WorkManagerProvider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class AndroidModule {
    @Provides
    @Singleton
    fun workManager(workManagerProvider: WorkManagerProvider): WorkManager =
        workManagerProvider.workManager

    @Provides
    @Singleton
    fun notificationManagerCompat(@ApplicationContext context: Context): NotificationManagerCompat =
        NotificationManagerCompat.from(context)

    @Provides
    @Singleton
    fun notificationManager(@ApplicationContext context: Context): NotificationManager =
        context.getSystemService()!!

    @Provides
    @Singleton
    fun sharedPreferences(@ApplicationContext context: Context): SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(context)

    @Provides
    @Singleton
    fun locationManager(@ApplicationContext context: Context): LocationManager =
        context.getSystemService()!!

    @Provides
    @Singleton
    fun applicationContext(@ApplicationContext context: Context): Context =
        context

    @Provides
    @Singleton
    fun riskLevelEvaluator(
        deviceRepository: DeviceRepository,
        beaconRepository: BeaconRepository
    ): RiskLevelEvaluator = RiskLevelEvaluator(deviceRepository, beaconRepository)
}