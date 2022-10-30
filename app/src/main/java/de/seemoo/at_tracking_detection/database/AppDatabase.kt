package de.seemoo.at_tracking_detection.database

import androidx.room.*
import androidx.room.migration.AutoMigrationSpec
import de.seemoo.at_tracking_detection.database.daos.*
import de.seemoo.at_tracking_detection.database.models.*
import de.seemoo.at_tracking_detection.database.models.device.BaseDevice
import de.seemoo.at_tracking_detection.util.converter.DateTimeConverter


// Database Definition
// TODO: Migration from Version 9 to 10, where Location is independent table
@Database(
    version = 10,
    entities = [
        BaseDevice::class,
        Notification::class,
        Beacon::class,
        Feedback::class,
        Scan::class,
        Location::class,
    ],
    autoMigrations = [
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5) ,
        AutoMigration(from=5, to=6),
        AutoMigration(from=7, to=8),
        AutoMigration(from=8, to=9, spec = AppDatabase.RenameScanMigrationSpec::class),
        // AutoMigration(from=9, to=10, spec = AppDatabase.MigrateToLocationDB::class)
    ],
    exportSchema = true
)
@TypeConverters(Converters::class, DateTimeConverter::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun deviceDao(): DeviceDao

    abstract fun beaconDao(): BeaconDao

    abstract fun notificationDao(): NotificationDao

    abstract fun feedbackDao(): FeedbackDao

    abstract fun scanDao(): ScanDao

    abstract fun locationDao(): LocationDao

    @RenameColumn(
        tableName = "scan",
        fromColumnName = "date",
        toColumnName = "endDate"
    )
    class RenameScanMigrationSpec: AutoMigrationSpec {

    }

    /*
    @DeleteColumn(
        tableName = "beacon",
        columnName = "latitude",
    )
    @DeleteColumn(
        tableName = "beacon",
        columnName = "longitude",
    )
    class MigrateToLocationDB: AutoMigrationSpec {

    }
     */
}