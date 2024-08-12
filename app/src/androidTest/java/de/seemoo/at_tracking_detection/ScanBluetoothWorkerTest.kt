package de.seemoo.at_tracking_detection

import android.location.Location
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import androidx.core.content.getSystemService
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.Data
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.impl.utils.taskexecutor.WorkManagerTaskExecutor
import androidx.work.testing.TestForegroundUpdater
import androidx.work.testing.TestProgressUpdater
import de.seemoo.at_tracking_detection.database.AppDatabase
import de.seemoo.at_tracking_detection.detection.LocationProvider
import de.seemoo.at_tracking_detection.detection.LocationRequester
import de.seemoo.at_tracking_detection.hilt.DatabaseModule
import de.seemoo.at_tracking_detection.util.BuildVersionProvider
import de.seemoo.at_tracking_detection.util.DefaultBuildVersionProvider
import de.seemoo.at_tracking_detection.util.SharedPrefs
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Collections
import java.util.UUID
import java.util.concurrent.Executor
import java.util.concurrent.Executors

@RunWith(AndroidJUnit4::class)
class ScanBluetoothWorkerTest {

    private val TEST_DB = "scan-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java.canonicalName,
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
        ).addMigrations(DatabaseModule.MIGRATION_5_7, DatabaseModule.MIGRATION_6_7)
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
        val context = ATTrackingDetectionApplication.getAppContext()
        val beaconRepository = DatabaseModule.provideBeaconRepository(DatabaseModule.provideBeaconDao(db))
        val deviceRepository = DatabaseModule.provideDeviceRepository(DatabaseModule.provideDeviceDao(db))
        val scanRepository = DatabaseModule.provideScanRepository(DatabaseModule.provideScanDao(db))
        val locationRepository = DatabaseModule.provideLocationRepository(DatabaseModule.provideLocationDao(db))
        val locationProvider = LocationProvider(context.getSystemService<LocationManager>()!!)
        val notificationService = ATTrackingDetectionApplication.getCurrentApp()!!.notificationService
        val backgroundWorkScheduler = ATTrackingDetectionApplication.getCurrentApp()!!.backgroundWorkScheduler
//        val backgroundBluetoothScanner = BackgroundBluetoothScanner(backgroundWorkScheduler, notificationService, locationProvider, scanRepository)

        val params = WorkerParameters(
            UUID.randomUUID(),
            Data.EMPTY,
            Collections.emptyList(),
            WorkerParameters.RuntimeExtras(),
            1,
            1,
            // This is unused for ListenableWorker
            executor,
            WorkManagerTaskExecutor(executor),
            WorkerFactory.getDefaultWorkerFactory(),
            TestProgressUpdater(),
            TestForegroundUpdater()
        )
    }

    @Test
    fun testDelayedLocationBluetoothScan() {
        val context = ATTrackingDetectionApplication.getAppContext()
        val beaconRepository = DatabaseModule.provideBeaconRepository(DatabaseModule.provideBeaconDao(db))
        val deviceRepository = DatabaseModule.provideDeviceRepository(DatabaseModule.provideDeviceDao(db))
        val scanRepository = DatabaseModule.provideScanRepository(DatabaseModule.provideScanDao(db))
        val locationRepository = DatabaseModule.provideLocationRepository(DatabaseModule.provideLocationDao(db))
        val locationProvider = TestLocationProvider(true, 9000, context.getSystemService<LocationManager>()!!, DefaultBuildVersionProvider())

        val notificationService = ATTrackingDetectionApplication.getCurrentApp()!!.notificationService
        val backgroundWorkScheduler = ATTrackingDetectionApplication.getCurrentApp()!!.backgroundWorkScheduler
//        val backgroundScanner = BackgroundBluetoothScanner(backgroundWorkScheduler, notificationService, locationProvider, scanRepository)

        val params = WorkerParameters(
            UUID.randomUUID(),
            Data.EMPTY,
            Collections.emptyList(),
            WorkerParameters.RuntimeExtras(),
            1,
            1,
            // This is unused for ListenableWorker
            executor,
            WorkManagerTaskExecutor(executor),
            WorkerFactory.getDefaultWorkerFactory(),
            TestProgressUpdater(),
            TestForegroundUpdater()
        )
    }


    @Test
    fun testLocationTimedOutScanBluetooth() {
        val context = ATTrackingDetectionApplication.getAppContext()
        val beaconRepository = DatabaseModule.provideBeaconRepository(DatabaseModule.provideBeaconDao(db))
        val deviceRepository = DatabaseModule.provideDeviceRepository(DatabaseModule.provideDeviceDao(db))
        val scanRepository = DatabaseModule.provideScanRepository(DatabaseModule.provideScanDao(db))
        val locationRepository = DatabaseModule.provideLocationRepository(DatabaseModule.provideLocationDao(db))
        val locationProvider = TestLocationProvider(true, 20000, context.getSystemService<LocationManager>()!!, DefaultBuildVersionProvider())

        val notificationService = ATTrackingDetectionApplication.getCurrentApp()!!.notificationService
        val backgroundWorkScheduler = ATTrackingDetectionApplication.getCurrentApp()!!.backgroundWorkScheduler
//        val backgroundScanner = BackgroundBluetoothScanner(backgroundWorkScheduler, notificationService, locationProvider, scanRepository)

        val params = WorkerParameters(
            UUID.randomUUID(),
            Data.EMPTY,
            Collections.emptyList(),
            WorkerParameters.RuntimeExtras(),
            1,
            // This is unused for ListenableWorker
            1,
            executor,
            WorkManagerTaskExecutor(executor),
            WorkerFactory.getDefaultWorkerFactory(),
            TestProgressUpdater(),
            TestForegroundUpdater()
        )
    }

    @Test
    fun testLocationNotAllowedInBackgroundScan() {
        SharedPrefs.useLocationInTrackingDetection = false
        val context = ATTrackingDetectionApplication.getAppContext()
        val beaconRepository = DatabaseModule.provideBeaconRepository(DatabaseModule.provideBeaconDao(db))
        val deviceRepository = DatabaseModule.provideDeviceRepository(DatabaseModule.provideDeviceDao(db))
        val scanRepository = DatabaseModule.provideScanRepository(DatabaseModule.provideScanDao(db))
        val locationRepository = DatabaseModule.provideLocationRepository(DatabaseModule.provideLocationDao(db))
        val locationProvider = TestLocationProvider(true, 0, context.getSystemService<LocationManager>()!!, DefaultBuildVersionProvider())

        val notificationService = ATTrackingDetectionApplication.getCurrentApp()!!.notificationService
        val backgroundWorkScheduler = ATTrackingDetectionApplication.getCurrentApp()!!.backgroundWorkScheduler


        val params = WorkerParameters(
            UUID.randomUUID(),
            Data.EMPTY,
            Collections.emptyList(),
            WorkerParameters.RuntimeExtras(),
            1,
            // This is unused for ListenableWorker
            1,
            executor,
            WorkManagerTaskExecutor(executor),
            WorkerFactory.getDefaultWorkerFactory(),
            TestProgressUpdater(),
            TestForegroundUpdater()
        )
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
}

class TestLocationProvider(private val lastLocationIsNull: Boolean, private val locationDelayMillis: Long, locationManager: LocationManager, versionProvider: BuildVersionProvider) : LocationProvider(locationManager) {
//    override fun getLastLocation(checkRequirements: Boolean): Location? {
//        if (lastLocationIsNull) {
//            return null
//        }
//        return super.getLastLocation(checkRequirements)
//    }

    override fun lastKnownOrRequestLocationUpdates(
        locationRequester: LocationRequester,
        timeoutMillis: Long?
    ): Location? {
        if (locationDelayMillis > 0) {
            val handler = Handler(Looper.getMainLooper())
            val runnable = Runnable {
                super.lastKnownOrRequestLocationUpdates(locationRequester, timeoutMillis)
            }
            handler.postDelayed(runnable, locationDelayMillis)
        }

        return null
    }
}