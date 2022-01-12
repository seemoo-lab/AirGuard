package de.seemoo.at_tracking_detection.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import de.seemoo.at_tracking_detection.database.daos.BeaconDao
import de.seemoo.at_tracking_detection.database.daos.DeviceDao
import de.seemoo.at_tracking_detection.database.daos.FeedbackDao
import de.seemoo.at_tracking_detection.database.daos.NotificationDao
import de.seemoo.at_tracking_detection.database.tables.Beacon
import de.seemoo.at_tracking_detection.database.tables.device.Device
import de.seemoo.at_tracking_detection.database.tables.Feedback
import de.seemoo.at_tracking_detection.database.tables.Notification
import de.seemoo.at_tracking_detection.util.converter.DateTimeConverter

@Database(
    version = 3,
    entities = [Device::class, Notification::class, Beacon::class, Feedback::class],
    autoMigrations = [AutoMigration(from = 2, to = 3)]
)
@TypeConverters(DateTimeConverter::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun deviceDao(): DeviceDao

    abstract fun beaconDao(): BeaconDao

    abstract fun notificationDao(): NotificationDao

    abstract fun feedbackDao(): FeedbackDao
}