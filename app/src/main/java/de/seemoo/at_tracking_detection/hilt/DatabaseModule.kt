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
import de.seemoo.at_tracking_detection.detection.BackgroundBluetoothScanner
import de.seemoo.at_tracking_detection.detection.TrackingDetectorWorker.Companion.getLocation
import timber.log.Timber
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    val MIGRATION_5_7 = object : Migration(5, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            try {
                db.execSQL("ALTER TABLE `beacon` ADD COLUMN `serviceUUIDs` TEXT DEFAULT NULL")
            }catch (e: SQLiteException) {
                Timber.e("Could not create new column ${e}")
            }

        }
    }

    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
        }
    }

    val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // add location table and locationID to beacon
            try {
                db.execSQL("CREATE TABLE `location` (`locationId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT, `firstDiscovery` TEXT NOT NULL, `lastSeen` TEXT NOT NULL, `longitude` REAL NOT NULL, `latitude` REAL NOT NULL, `accuracy` REAL)")
                db.execSQL("CREATE UNIQUE INDEX `index_location_latitude_longitude` ON `location` (`latitude`, `longitude`)")
                db.execSQL("ALTER TABLE `beacon` ADD COLUMN `locationId` INTEGER")
            }catch (e: SQLiteException) {
                Timber.e("Could not create location ${e}")
            }

            var sql: String
            while (true) {
                sql = "SELECT * FROM `beacon` WHERE `locationId` IS NULL AND `latitude` IS NOT NULL AND `longitude` IS NOT NULL LIMIT 1"
                val beacon = db.query(sql)

                if (beacon.count == 0) {
                    // If there are no more locations left to do, then break
                    break
                }

                beacon.moveToFirst()

                // println("Coordinates")
                var latitude: Double = beacon.getDouble(4)
                // println("Latitude: $latitude")
                var longitude: Double = beacon.getDouble(5)
                // println("Longitude: $longitude")

                sql = "SELECT `longitude`, `latitude` FROM `location` ORDER BY ABS(`latitude` - $latitude) + ABS(`longitude` - $longitude) ASC LIMIT 1"
                val closestLocation = db.query(sql)

                var insertNewLocation = false

                if (closestLocation.count > 0){
                    closestLocation.moveToFirst()
                    val closestLongitude = closestLocation.getDouble(0)
                    val closestLatitude = closestLocation.getDouble(1)

                    val locationA = getLocation(latitude, longitude)
                    val locationB = getLocation(closestLatitude, closestLongitude)
                    val distanceBetweenLocations = locationA.distanceTo(locationB)
                    if (distanceBetweenLocations > BackgroundBluetoothScanner.MAX_DISTANCE_UNTIL_NEW_LOCATION){
                        // println("Insert New, because far enough away")
                        insertNewLocation = true
                    } else {
                        // println("Get existing location")
                        latitude = closestLatitude
                        longitude = closestLongitude
                        // println("Latitude: $latitude")
                        // println("Longitude: $longitude")
                    }
                } else if (closestLocation.count == 0) {
                    // println("Insert New, because no locations in database")
                    insertNewLocation = true
                }

                if (insertNewLocation) {
                    val deviceAddress = beacon.getString(3)

                    //Fallback if device db is inconsistent
                    var firstDiscovery = beacon.getString(1) // receivedAt
                    var lastSeen = firstDiscovery // receivedAt

                    sql = "SELECT `firstDiscovery`, `lastSeen` FROM `device` WHERE `address` = '$deviceAddress'"
                    val device = db.query(sql)

                    if (device.count > 0) {
                        // println("Successfully got timestamps from device table")
                        device.moveToFirst()
                        firstDiscovery =  device.getString(0)
                        lastSeen = device.getString(1)
                    }

                    sql = "INSERT INTO `location` (`firstDiscovery`, `lastSeen`, `longitude`, `latitude`) VALUES ('$firstDiscovery', '$lastSeen', $longitude, $latitude)"
                    db.execSQL(sql)
                }

                sql = "SELECT `locationId` FROM `location` WHERE `latitude` = $latitude AND `longitude` = $longitude"
                val location = db.query(sql)
                println(location.count)
                if (location.count > 0) { // else: locationId stays null
                    location.moveToFirst()
                    val locationId = location.getInt(0)
                    println("locationId: $locationId")
                    val beaconId = beacon.getInt(0)
                    println("beaconId: $beaconId")
                    sql = "UPDATE `beacon` SET `locationId` = $locationId WHERE `locationId` IS NULL AND `beaconId` = $beaconId"
                    db.execSQL(sql)

                    sql = "SELECT * FROM `beacon` WHERE `locationId` IS NOT NULL"
                    println(db.query(sql).count)
                }
            }

            try {
                db.execSQL("CREATE TABLE `beacon_backup` (`beaconId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `receivedAt` TEXT NOT NULL, `rssi` INTEGER NOT NULL, `deviceAddress` TEXT NOT NULL, `locationId` INTEGER, `mfg` BLOB, `serviceUUIDs` TEXT)")
                db.execSQL("INSERT INTO `beacon_backup` SELECT `beaconId`, `receivedAt`,  `rssi`, `deviceAddress`, `locationId`, `mfg`, `serviceUUIDs` FROM `beacon`")
                db.execSQL("DROP TABLE `beacon`")
            } catch (e: SQLiteException) {
                Timber.e("Could not create beacon_backup ${e}")
            }

            try {
                db.execSQL("CREATE TABLE `beacon` (`beaconId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `receivedAt` TEXT NOT NULL, `rssi` INTEGER NOT NULL, `deviceAddress` TEXT NOT NULL, `locationId` INTEGER, `mfg` BLOB, `serviceUUIDs` TEXT)")
                db.execSQL("INSERT INTO `beacon` SELECT `beaconId`, `receivedAt`,  `rssi`, `deviceAddress`, `locationId`, `mfg`, `serviceUUIDs` FROM `beacon_backup`")
                db.execSQL("DROP TABLE `beacon_backup`")
            } catch (e: SQLiteException) {
                Timber.e("Could not create beacon ${e}")
            }
        }
    }

    val MIGRATION_16_17 = object : Migration(16, 17) {
        override fun migrate(db: SupportSQLiteDatabase) {
            try {
                Timber.d("Adding new column 'subDeviceType'")
                db.execSQL("ALTER TABLE device ADD COLUMN subDeviceType TEXT NOT NULL DEFAULT 'UNKNOWN'")

                Timber.d("Updating deviceType from 'SMART_TAG' and 'SMART_TAG_PLUS' to 'SAMSUNG_DEVICE'")
                db.execSQL("UPDATE device SET deviceType = 'SAMSUNG_DEVICE' WHERE deviceType = 'SMART_TAG' OR deviceType = 'SMART_TAG_PLUS'")
            } catch (e: SQLiteException) {
                Timber.e("Migration error: ${e.message}")
            } catch (e: Exception) {
                Timber.e("Unexpected migration error: ${e.message}")
            }
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(context, AppDatabase::class.java, "attd_db")
            .addMigrations(MIGRATION_5_7, MIGRATION_6_7, MIGRATION_9_10, MIGRATION_16_17)
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

    @Provides
    fun provideLocationRepository(locationDao: LocationDao): LocationRepository {
        return LocationRepository(locationDao)
    }
}