package de.seemoo.at_tracking_detection.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import de.seemoo.at_tracking_detection.database.daos.BeaconDao
import de.seemoo.at_tracking_detection.database.daos.DeviceDao
import de.seemoo.at_tracking_detection.database.daos.FeedbackDao
import de.seemoo.at_tracking_detection.database.daos.NotificationDao
import de.seemoo.at_tracking_detection.database.tables.Beacon
import de.seemoo.at_tracking_detection.database.tables.Device
import de.seemoo.at_tracking_detection.database.tables.Feedback
import de.seemoo.at_tracking_detection.database.tables.Notification
import de.seemoo.at_tracking_detection.util.converter.DateTimeConverter

@Database(
    entities = [Device::class, Notification::class, Beacon::class, Feedback::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(DateTimeConverter::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun deviceDao(): DeviceDao

    abstract fun beaconDao(): BeaconDao

    abstract fun notificationDao(): NotificationDao

    abstract fun feedbackDao(): FeedbackDao
}