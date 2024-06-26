package de.seemoo.at_tracking_detection

import androidx.room.Room
import androidx.room.migration.Migration
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.seemoo.at_tracking_detection.database.AppDatabase
import de.seemoo.at_tracking_detection.database.daos.BeaconDao_Impl
import de.seemoo.at_tracking_detection.database.repository.BeaconRepository
import de.seemoo.at_tracking_detection.hilt.DatabaseModule
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException
import java.time.LocalDateTime

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val TEST_DB = "migration-test"

    private val AUTO_MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE `device` ADD COLUMN `name` TEXT DEFAULT NULL")
        }
    }

    private val AUTO_MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE `notification` ADD COLUMN `dismissed` INTEGER DEFAULT NULL")
            database.execSQL("ALTER TABLE `notification` ADD COLUMN `clicked` INTEGER DEFAULT NULL")
            database.execSQL("CREATE TABLE IF NOT EXISTS `_new_device` (`deviceId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uniqueId` TEXT, `address` TEXT NOT NULL, `name` TEXT, `ignore` INTEGER NOT NULL, `connectable` INTEGER, `payloadData` INTEGER, `firstDiscovery` TEXT NOT NULL, `lastSeen` TEXT NOT NULL, `notificationSent` INTEGER NOT NULL, `lastNotificationSent` TEXT, `deviceType` TEXT)")
            database.execSQL("INSERT INTO `_new_device` (`lastNotificationSent`,`address`,`connectable`,`firstDiscovery`,`lastSeen`,`name`,`ignore`,`payloadData`,`deviceId`,`notificationSent`) SELECT `lastNotificationSent`,`address`,`connectable`,`firstDiscovery`,`lastSeen`,`name`,`ignore`,`payloadData`,`deviceId`,`notificationSent` FROM `device`")
            database.execSQL("DROP TABLE `device`")
            database.execSQL("ALTER TABLE `_new_device` RENAME TO `device`")
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_device_address` ON `device` (`address`)")
        }
    }

    private val AUTO_MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE `beacon` ADD COLUMN `serviceUUIDs` TEXT DEFAULT NULL")
            database.execSQL("CREATE TABLE IF NOT EXISTS `_new_device` (`deviceId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uniqueId` TEXT, `address` TEXT NOT NULL, `name` TEXT, `ignore` INTEGER NOT NULL, `connectable` INTEGER DEFAULT 0, `payloadData` INTEGER, `firstDiscovery` TEXT NOT NULL, `lastSeen` TEXT NOT NULL, `notificationSent` INTEGER NOT NULL, `lastNotificationSent` TEXT, `deviceType` TEXT DEFAULT null)")
            database.execSQL("INSERT INTO `_new_device` (`deviceType`,`lastNotificationSent`,`address`,`connectable`,`firstDiscovery`,`lastSeen`,`name`,`ignore`,`payloadData`,`deviceId`,`uniqueId`,`notificationSent`) SELECT `deviceType`,`lastNotificationSent`,`address`,`connectable`,`firstDiscovery`,`lastSeen`,`name`,`ignore`,`payloadData`,`deviceId`,`uniqueId`,`notificationSent` FROM `device`")
            database.execSQL("DROP TABLE `device`")
            database.execSQL("ALTER TABLE `_new_device` RENAME TO `device`")
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_device_address` ON `device` (`address`)")
        }
    }

    private val AUTO_MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `_new_device` (`deviceId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uniqueId` TEXT, `address` TEXT NOT NULL, `name` TEXT, `ignore` INTEGER NOT NULL, `connectable` INTEGER DEFAULT 0, `payloadData` INTEGER, `firstDiscovery` TEXT NOT NULL, `lastSeen` TEXT NOT NULL, `notificationSent` INTEGER NOT NULL, `lastNotificationSent` TEXT, `deviceType` TEXT)")
            database.execSQL("INSERT INTO `_new_device` (`deviceType`,`lastNotificationSent`,`address`,`connectable`,`firstDiscovery`,`lastSeen`,`name`,`ignore`,`payloadData`,`deviceId`,`uniqueId`,`notificationSent`) SELECT `deviceType`,`lastNotificationSent`,`address`,`connectable`,`firstDiscovery`,`lastSeen`,`name`,`ignore`,`payloadData`,`deviceId`,`uniqueId`,`notificationSent` FROM `device`")
            database.execSQL("DROP TABLE `device`")
            database.execSQL("ALTER TABLE `_new_device` RENAME TO `device`")
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_device_address` ON `device` (`address`)")
        }
    }


    private val ALL_MIGRATIONS = arrayOf(
        DatabaseModule.MIGRATION_5_7, DatabaseModule.MIGRATION_6_7, DatabaseModule.MIGRATION_9_10, DatabaseModule.MIGRATION_16_17
    )

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory()
    )

    fun insertDummyData(db: SupportSQLiteDatabase) {
        db.execSQL("INSERT INTO `beacon` (`receivedAt`, `rssi`, `deviceAddress`, `longitude`, `latitude`) VALUES ('2022-03-25T10:00:00', -78, 'aa:bb:cc:dd:ee', 50.04231, 8.34423)")
        db.execSQL("INSERT INTO `device` (`address` , `ignore`, `connectable`, `firstDiscovery`, `lastSeen`, `notificationSent`) VALUES ('aa:bb:cc:dd:ee', false, true, '2022-03-25', '2022-03-25T10:00:00', false)")
    }

    fun insertNewBeacon(db: SupportSQLiteDatabase) {
        db.execSQL("INSERT INTO `beacon` (`receivedAt`, `rssi`, `deviceAddress`, `longitude`, `latitude`, `serviceUUIDs`) VALUES ('2022-03-25T11:00:00', -78, 'aa:bb:cc:dd:ee', 50.04231, 8.34423, 'AA:BB;DD:EE;FF:EE')")
    }

    fun insertNewBeacon2(db: SupportSQLiteDatabase) {
        db.execSQL("INSERT INTO `beacon` (`receivedAt`, `rssi`, `deviceAddress`, `longitude`, `latitude`, `serviceUUIDs`) VALUES ('2022-03-25T15:00:00', -50, 'aa:bb:cc:dd:ee', 50.04232, 8.34424, 'AA:BB;DD:EE;FF:EE')")
    }

    fun insertNewBeacon3(db: SupportSQLiteDatabase) {
        db.execSQL("INSERT INTO `beacon` (`receivedAt`, `rssi`, `deviceAddress`, `longitude`, `latitude`, `serviceUUIDs`) VALUES ('2022-03-25T19:00:00', -60, 'aa:bb:cc:dd:ee', 40.04232, 4.34424, 'AA:BB;DD:EE;FF:EE')")
    }

    fun insertNewBeaconWithLocationId(db: SupportSQLiteDatabase) {
        db.execSQL("INSERT INTO `beacon` (`receivedAt`, `rssi`, `deviceAddress`, `locationId`, `serviceUUIDs`) VALUES ('2022-04-25T11:00:00', -79, 'aa:bb:cc:dd:ee', 1, 'AA:BB;DD:EE;FF:EE')")
    }

    @After
    fun removeDB() {
        helper.createDatabase(TEST_DB, 2).apply {
            val path =  this.path
            this.close()
            val dbFile = File(path)
            assert(dbFile.delete() == true)
        }
    }

    fun assertDBMigrations(roomDB: AppDatabase) {
        val time = LocalDateTime.of(2022,3,1,0,0,0,0)
        val beaconDao = BeaconDao_Impl(roomDB)
        val beaconRepository = BeaconRepository(beaconDao)
        val beacons = beaconRepository.getLatestBeacons(time)
        val serviceBeacon = beacons.filter { it.serviceUUIDs != null }.first()
        assert(serviceBeacon.serviceUUIDs != null)
        assert(serviceBeacon.serviceUUIDs == arrayListOf<String>("AA:BB","DD:EE","FF:EE"))

        val otherBeacon  = beacons.first { it.serviceUUIDs == null }
        assert(otherBeacon.serviceUUIDs == null)
    }

    @Test
    @Throws(IOException::class)
    fun migrateAll() {

        helper.createDatabase(TEST_DB, 2).apply {
            insertDummyData(this)
            close()
        }

        val roomDB = Room.databaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java,
            TEST_DB
        ).addMigrations(*ALL_MIGRATIONS).build().apply {
            insertNewBeaconWithLocationId(openHelper.writableDatabase)
            openHelper.writableDatabase.close()
        }

        assertDBMigrations(roomDB)
    }

    @Test
    @Throws(IOException::class)
    fun roomMigrate3_current() {
        helper.createDatabase(TEST_DB, 3).apply {
            insertDummyData(this)
            close()
        }

        val roomDB = Room.databaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java,
            TEST_DB
        ).addMigrations(*ALL_MIGRATIONS).build().apply {
            insertNewBeaconWithLocationId(openHelper.writableDatabase)
            openHelper.writableDatabase.close()
        }

        assertDBMigrations(roomDB)
    }

    @Test
    @Throws(IOException::class)
    fun roomMigrate4_current() {
        helper.createDatabase(TEST_DB, 4).apply {
            insertDummyData(this)
            close()
        }

        val roomDB = Room.databaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java,
            TEST_DB
        ).addMigrations(*ALL_MIGRATIONS).build().apply {
            insertNewBeaconWithLocationId(openHelper.writableDatabase)
            openHelper.writableDatabase.close()
        }

        assertDBMigrations(roomDB)
    }

    @Test
    @Throws(IOException::class)
    fun roomMigrate5_current() {
        helper.createDatabase(TEST_DB, 5).apply {
            insertDummyData(this)
            close()
        }

        val roomDB = Room.databaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java,
            TEST_DB
        ).addMigrations(*ALL_MIGRATIONS).build().apply {
            insertNewBeaconWithLocationId(openHelper.writableDatabase)
            openHelper.writableDatabase.close()
        }

        assertDBMigrations(roomDB)
    }

    @Test
    @Throws(IOException::class)
    fun roomMigrate6_current() {
        helper.createDatabase(TEST_DB, 6).apply {
            insertDummyData(this)
            close()
        }

        val roomDB = Room.databaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java,
            TEST_DB
        ).addMigrations(*ALL_MIGRATIONS).build().apply {
            insertNewBeaconWithLocationId(openHelper.writableDatabase)
            openHelper.writableDatabase.close()
        }

        assertDBMigrations(roomDB)
    }

    @Test
    @Throws(IOException::class)
    fun roomMigrate7_current() {
        helper.createDatabase(TEST_DB, 7).apply {
            insertDummyData(this)
            close()
        }

        val roomDB = Room.databaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java,
            TEST_DB
        ).addMigrations(*ALL_MIGRATIONS).build().apply {
            insertNewBeaconWithLocationId(openHelper.writableDatabase)
            openHelper.writableDatabase.close()
        }

        assertDBMigrations(roomDB)
    }

    @Test
    @Throws(IOException::class)
    fun migrate4_5() {
        helper.createDatabase(TEST_DB, 4).apply {
            insertDummyData(this)
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 5, true, AUTO_MIGRATION_4_5)
        insertNewBeacon(db)
    }

    @Test
    @Throws(IOException::class)
    fun migrate5_6() {
        helper.createDatabase(TEST_DB, 5).apply {
            insertDummyData(this)
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 6, true, AUTO_MIGRATION_5_6)
        insertNewBeacon(db)
    }

    @Test
    @Throws(IOException::class)
    fun migrate5_7() {
        helper.createDatabase(TEST_DB, 5).apply {
            insertDummyData(this)
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 7, true, DatabaseModule.MIGRATION_5_7)
        insertNewBeacon(db)
    }

    @Test
    @Throws(IOException::class)
    fun migrate9_10() {
        helper.createDatabase(TEST_DB, 9).apply {
            insertNewBeacon(this)
            insertNewBeacon2(this) // different location but in distance for same locationId
            insertNewBeacon3(this) // totally different location
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 10, true, DatabaseModule.MIGRATION_9_10)
        insertNewBeaconWithLocationId(db)
    }

}