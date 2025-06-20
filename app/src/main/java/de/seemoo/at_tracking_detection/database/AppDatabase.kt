package de.seemoo.at_tracking_detection.database

import androidx.room.*
import androidx.room.migration.AutoMigrationSpec
import de.seemoo.at_tracking_detection.database.daos.*
import de.seemoo.at_tracking_detection.database.models.*
import de.seemoo.at_tracking_detection.database.models.device.BaseDevice
import de.seemoo.at_tracking_detection.util.converter.DateTimeConverter

@Database(
    version = 23,
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
        AutoMigration(from=10, to=11),
        AutoMigration(from=11, to=12),
        AutoMigration(from=12, to=13),
        AutoMigration(from=13, to=14),
        AutoMigration(from=14, to=15),
        AutoMigration(from=15, to=16),
        AutoMigration(from=17, to=18),
        AutoMigration(from=18, to=19),
        AutoMigration(from=19, to=20),
        AutoMigration(from=20, to=21),
        AutoMigration(from=21, to=22),
        AutoMigration(from=22, to=23)
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
    class RenameScanMigrationSpec: AutoMigrationSpec
}