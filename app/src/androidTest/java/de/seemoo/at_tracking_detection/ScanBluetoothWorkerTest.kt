package de.seemoo.at_tracking_detection

import android.location.Location
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import androidx.arch.core.executor.TaskExecutor
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.espresso.matcher.ViewMatchers.assertThat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.*
import androidx.work.impl.utils.taskexecutor.WorkManagerTaskExecutor
import androidx.work.testing.TestForegroundUpdater
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.TestProgressUpdater
import de.seemoo.at_tracking_detection.database.AppDatabase
import de.seemoo.at_tracking_detection.database.models.Scan
import de.seemoo.at_tracking_detection.database.repository.NotificationRepository
import de.seemoo.at_tracking_detection.database.viewmodel.NotificationViewModel
import de.seemoo.at_tracking_detection.detection.LocationProvider
import de.seemoo.at_tracking_detection.detection.ScanBluetoothWorker
import de.seemoo.at_tracking_detection.hilt.DatabaseModule
import de.seemoo.at_tracking_detection.notifications.NotificationBuilder
import de.seemoo.at_tracking_detection.notifications.NotificationService
import de.seemoo.at_tracking_detection.util.BuildVersionProvider
import de.seemoo.at_tracking_detection.util.DefaultBuildVersionProvider
import de.seemoo.at_tracking_detection.util.SharedPrefs
import de.seemoo.at_tracking_detection.worker.BackgroundWorkBuilder
import de.seemoo.at_tracking_detection.worker.BackgroundWorkScheduler
import kotlinx.coroutines.runBlocking
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.CoreMatchers.instanceOf
import org.junit.*
import org.junit.runner.RunWith
import java.io.File
import java.util.*
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
        val locationProvider = LocationProvider(context.getSystemService<LocationManager>()!!, DefaultBuildVersionProvider())

        val notificationService = ATTrackingDetectionApplication.getCurrentApp()!!.notificationService
        val backgroundWorkScheduler = ATTrackingDetectionApplication.getCurrentApp()!!.backgroundWorkScheduler

        val params = WorkerParameters(
            UUID.randomUUID(),
            Data.EMPTY,
            Collections.emptyList(),
            WorkerParameters.RuntimeExtras(),
            1,
            // This is unused for ListenableWorker
            executor,
            WorkManagerTaskExecutor(executor),
            WorkerFactory.getDefaultWorkerFactory(),
            TestProgressUpdater(),
            TestForegroundUpdater()
        )

        val worker = ScanBluetoothWorker(
            context,
            params,
            beaconRepository, deviceRepository, scanRepository, locationRepository, locationProvider, notificationService, backgroundWorkScheduler)

        runBlocking {
            val result = worker.doWork()
            assertThat(result, instanceOf(ListenableWorker.Result.Success::class.java))
        }
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

        val params = WorkerParameters(
            UUID.randomUUID(),
            Data.EMPTY,
            Collections.emptyList(),
            WorkerParameters.RuntimeExtras(),
            1,
            // This is unused for ListenableWorker
            executor,
            WorkManagerTaskExecutor(executor),
            WorkerFactory.getDefaultWorkerFactory(),
            TestProgressUpdater(),
            TestForegroundUpdater()
        )

        val worker = ScanBluetoothWorker(
            context,
            params,
            beaconRepository, deviceRepository, scanRepository, locationRepository, locationProvider, notificationService, backgroundWorkScheduler)

        runBlocking {
            val result = worker.doWork()
            assertThat(result, instanceOf(ListenableWorker.Result.Success::class.java))
            Assert.assertNotNull(worker.location)
        }
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

        val params = WorkerParameters(
            UUID.randomUUID(),
            Data.EMPTY,
            Collections.emptyList(),
            WorkerParameters.RuntimeExtras(),
            1,
            // This is unused for ListenableWorker
            executor,
            WorkManagerTaskExecutor(executor),
            WorkerFactory.getDefaultWorkerFactory(),
            TestProgressUpdater(),
            TestForegroundUpdater()
        )

        val worker = ScanBluetoothWorker(
            context,
            params,
            beaconRepository, deviceRepository, scanRepository, locationRepository, locationProvider, notificationService, backgroundWorkScheduler)

        runBlocking {
            val result = worker.doWork()
            assertThat(result, instanceOf(ListenableWorker.Result.Success::class.java))
            Assert.assertNull(worker.location)
        }
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
            executor,
            WorkManagerTaskExecutor(executor),
            WorkerFactory.getDefaultWorkerFactory(),
            TestProgressUpdater(),
            TestForegroundUpdater()
        )

        val worker = ScanBluetoothWorker(
            context,
            params,
            beaconRepository, deviceRepository, scanRepository, locationRepository, locationProvider, notificationService, backgroundWorkScheduler)

        runBlocking {
            val result = worker.doWork()
            assertThat(result, instanceOf(ListenableWorker.Result.Success::class.java))
            Assert.assertNull(worker.location)
        }
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

class TestLocationProvider(val lastLocationIsNull: Boolean, val locationDelayMillis: Long, locationManager: LocationManager, versionProvider: BuildVersionProvider) : LocationProvider(locationManager, versionProvider) {
    override fun getLastLocation(): Location? {
        if (lastLocationIsNull) {
            return null
        }
        return super.getLastLocation()
    }

    override fun getCurrentLocation(callback: (Location?) -> Unit) {
        if (locationDelayMillis > 0) {
            val handler = Handler(Looper.getMainLooper())
            val runnable = Runnable {
                super.getCurrentLocation(callback)
            }
            handler.postDelayed(runnable, locationDelayMillis)
        }
    }
}