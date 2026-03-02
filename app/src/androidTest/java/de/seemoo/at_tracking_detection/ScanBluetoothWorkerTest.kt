package de.seemoo.at_tracking_detection

import android.location.Location
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.seemoo.at_tracking_detection.database.AppDatabase
import de.seemoo.at_tracking_detection.detection.BackgroundBluetoothScanner
import de.seemoo.at_tracking_detection.detection.LocationProvider
import de.seemoo.at_tracking_detection.detection.LocationRequester
import de.seemoo.at_tracking_detection.hilt.DatabaseModule
import de.seemoo.at_tracking_detection.util.SharedPrefs
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.Executor
import java.util.concurrent.Executors

@RunWith(AndroidJUnit4::class)
class ScanBluetoothWorkerTest {

    private val TEST_DB = "scan-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    lateinit var db: AppDatabase
    lateinit var executor: Executor

    @Before
    fun createDB() {
        helper.createDatabase(TEST_DB, 2).apply {
            close()
        }

        val roomDB = Room.databaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java,
            TEST_DB
        ).addMigrations(DatabaseModule.MIGRATION_5_7, DatabaseModule.MIGRATION_6_7, DatabaseModule.MIGRATION_9_10, DatabaseModule.MIGRATION_16_17)
            .allowMainThreadQueries()
            .build().apply {
                openHelper.writableDatabase.close()
            }

        this.db = roomDB
        executor = Executors.newSingleThreadExecutor()
        SharedPrefs.useLocationInTrackingDetection = true
    }

    @Test
    fun testBluetoothScan() {
        runBlocking {
            val result = BackgroundBluetoothScanner.scanInBackground(startedFrom = "UnitTest")
            Assert.assertNotNull(result)
        }
    }

    @Test
    fun testDelayedLocationBluetoothScan() {
        runBlocking {
            val result = BackgroundBluetoothScanner.scanInBackground(startedFrom = "UnitTest")
            Assert.assertNotNull(result)
        }
    }

    @Test
    fun testLocationTimedOutScanBluetooth() {
        runBlocking {
            val result = BackgroundBluetoothScanner.scanInBackground(startedFrom = "UnitTest")
            Assert.assertNotNull(result)
            Assert.assertNull(BackgroundBluetoothScanner.location)
        }
    }

    @Test
    fun testLocationNotAllowedInBackgroundScan() {
        SharedPrefs.useLocationInTrackingDetection = false

        runBlocking {
            val result = BackgroundBluetoothScanner.scanInBackground(startedFrom = "UnitTest")
            Assert.assertNotNull(result)
            Assert.assertNull(BackgroundBluetoothScanner.location)
        }
    }

    @After
    fun removeDB() {
        helper.createDatabase(TEST_DB, 2).apply {
            val path = this.path ?: return@apply
            this.close()
            val dbFile = File(path)
            assert(dbFile.delete())
        }
    }
}

class TestLocationProvider(
    private val lastLocationIsNull: Boolean,
    private val locationDelayMillis: Long,
    locationManager: LocationManager
) : LocationProvider(locationManager) {

    override fun lastKnownOrRequestLocationUpdates(
        locationRequester: LocationRequester,
        timeoutMillis: Long?
    ): Location? {
        if (locationDelayMillis > 0) {
            val handler = Handler(Looper.getMainLooper())
            handler.postDelayed({
                super.lastKnownOrRequestLocationUpdates(locationRequester, timeoutMillis)
            }, locationDelayMillis)
        }

        return null
    }
}