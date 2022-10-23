package de.seemoo.at_tracking_detection.hilt

import android.content.Context
import android.database.sqlite.SQLiteException
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import de.seemoo.at_tracking_detection.database.AppDatabase
import de.seemoo.at_tracking_detection.database.daos.*
import de.seemoo.at_tracking_detection.database.repository.*
import timber.log.Timber
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    val MIGRATION_5_7 = object : Migration(5, 7) {
        override fun migrate(database: SupportSQLiteDatabase) {
            try {
                database.execSQL("ALTER TABLE `beacon` ADD COLUMN `serviceUUIDs` TEXT DEFAULT NULL")
            }catch (e: SQLiteException) {
                Timber.e("Could not create new column ${e}")
            }

        }
    }

    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(database: SupportSQLiteDatabase) {
        }
    }

    val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // TODO: implement
        }
    }


    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(context, AppDatabase::class.java, "attd_db")
            .addMigrations(MIGRATION_5_7, MIGRATION_6_7) // TODO: add MIGRATION_9_10
            .allowMainThreadQueries().build()
    }

    @Provides
    fun provideBeaconDao(database: AppDatabase): BeaconDao {
        return database.beaconDao()
    }

    @Provides
    fun provideDeviceDao(database: AppDatabase): DeviceDao {
        return database.deviceDao()
    }

    @Provides
    fun provideNotificationDao(database: AppDatabase): NotificationDao {
        return database.notificationDao()
    }

    @Provides
    fun provideFeedbackDao(database: AppDatabase): FeedbackDao {
        return database.feedbackDao()
    }

    @Provides
    fun provideScanDao(database: AppDatabase): ScanDao {
        return database.scanDao()
    }

    @Provides
    fun provideLocationDao(database: AppDatabase): LocationDao {
        return database.locationDao()
    }

    @Provides
    fun provideBeaconRepository(beaconDao: BeaconDao): BeaconRepository {
        return BeaconRepository(beaconDao)
    }

    @Provides
    fun provideDeviceRepository(deviceDao: DeviceDao): DeviceRepository {
        return DeviceRepository(deviceDao)
    }

    @Provides
    fun provideNotificationRepository(notificationDao: NotificationDao): NotificationRepository {
        return NotificationRepository(notificationDao)
    }

    @Provides
    fun providesFeedbackRepository(feedbackDao: FeedbackDao): FeedbackRepository {
        return FeedbackRepository(feedbackDao)
    }

    @Provides
    fun provideScanRepository(scanDao: ScanDao): ScanRepository {
        return ScanRepository(scanDao)
    }
}