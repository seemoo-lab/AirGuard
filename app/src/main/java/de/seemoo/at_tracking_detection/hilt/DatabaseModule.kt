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
import dagger.hilt.processor.internal.definecomponent.codegen._dagger_hilt_android_components_ActivityComponent
import de.seemoo.at_tracking_detection.database.AppDatabase
import de.seemoo.at_tracking_detection.database.daos.*
import de.seemoo.at_tracking_detection.database.repository.*
import de.seemoo.at_tracking_detection.detection.ScanBluetoothWorker.Companion.MAX_DISTANCE_UNTIL_NEW_LOCATION
import de.seemoo.at_tracking_detection.detection.TrackingDetectorWorker.Companion.getLocation
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
            // TODO: This migration makes the App Crash when migration, why?
            // add location table and locationID to beacon
            try {
                database.execSQL("CREATE TABLE `location` (`locationId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT, `firstDiscovery` TEXT NOT NULL, `lastSeen` TEXT NOT NULL, `longitude` REAL NOT NULL, `latitude` REAL NOT NULL, `accuracy` REAL)")
                database.execSQL("ALTER TABLE `beacon` ADD COLUMN `locationId` INTEGER")
            }catch (e: SQLiteException) {
                Timber.e("Could not create location ${e}")
            }

            /*
            var sql: String = ""
            while (true) {
                val beacon = database.query("SELECT * FROM `beacon` WHERE `locationId` IS NULL AND `latitude` IS NOT NULL AND `longitude` IS NOT NULL LIMIT 1")

                if (beacon.count == 0) {
                    // If there are no more locations left to do, then break
                    break
                }

                var longitude: Double = beacon.getDouble(4)
                var latitude: Double = beacon.getDouble(5)

                sql = "SELECT `latitude`, `longitude` FROM `location` ORDER BY ABS(`latitude` - $latitude) + ABS(`longitude` - $longitude) ASC LIMIT 1\""
                val closestLocation = database.query(sql)

                var insertNewLocation = false

                if (closestLocation.count > 0){
                    val closestLatitude = closestLocation.getDouble(0)
                    val closestLongitude = closestLocation.getDouble(1)

                    val locationA = getLocation(latitude, longitude)
                    val locationB = getLocation(closestLatitude, closestLongitude)
                    val distanceBetweenLocations = locationA.distanceTo(locationB)
                    if (distanceBetweenLocations > MAX_DISTANCE_UNTIL_NEW_LOCATION){
                        insertNewLocation = true
                    } else {
                        latitude = closestLatitude
                        longitude = closestLongitude
                    }
                } else if (closestLocation.count == 0) {
                    insertNewLocation = true
                }

                if (insertNewLocation) {
                    val deviceAddress = beacon.getString(3)

                    sql = "SELECT `firstDiscovery`, `lastSeen` FROM `device` WHERE `address` = $deviceAddress"
                    val device = database.query(sql)
                    val firstDiscovery =  device.getString(0)
                    val lastSeen = device.getString(1)

                    sql = "INSERT INTO `location` (`firstDiscovery`, `lastSeen`, `latitude`, `longitude`) VALUES ($firstDiscovery, $lastSeen, $latitude, $longitude)"
                    database.execSQL(sql)
                }

                sql = "SELECT `locationId` FROM `location` WHERE `latitude` = $latitude AND `longitude` = $longitude"
                val location = database.query(sql)

                val locationId = location.getInt(0)
                val beaconId = beacon.getInt(0)
                sql = "UPDATE `beacon` SET `locationId` = $locationId WHERE `locationId` IS NULL AND `beaconID` = $beaconId"
                database.execSQL(sql)
            }

              */

            try {
                database.execSQL("CREATE TABLE `beacon_backup` (`beaconId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `receivedAt` TEXT NOT NULL, `rssi` INTEGER NOT NULL, `deviceAddress` TEXT NOT NULL, `locationId` INTEGER, `mfg` BLOB, `serviceUUIDs` TEXT)")
                database.execSQL("INSERT INTO `beacon_backup` SELECT `beaconId`, `receivedAt`,  `rssi`, `deviceAddress`, `locationId`, `mfg`, `serviceUUIDs` FROM `beacon`")
                database.execSQL("DROP TABLE `beacon`")
            } catch (e: SQLiteException) {
                Timber.e("Could not create beacon_backup ${e}")
            }

            try {
                database.execSQL("CREATE TABLE `beacon` (`beaconId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `receivedAt` TEXT NOT NULL, `rssi` INTEGER NOT NULL, `deviceAddress` TEXT NOT NULL, `locationId` INTEGER, `mfg` BLOB, `serviceUUIDs` TEXT)")
                database.execSQL("INSERT INTO `beacon` SELECT `beaconId`, `receivedAt`,  `rssi`, `deviceAddress`, `locationId`, `mfg`, `serviceUUIDs` FROM `beacon_backup`")
                database.execSQL("DROP TABLE `beacon_backup`")
            } catch (e: SQLiteException) {
                Timber.e("Could not create beacon ${e}")
            }
        }
    }


    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(context, AppDatabase::class.java, "attd_db")
            .addMigrations(MIGRATION_5_7, MIGRATION_6_7, MIGRATION_9_10)
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