package de.seemoo.at_tracking_detection

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Data
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.TestWorkerBuilder
import de.seemoo.at_tracking_detection.database.tables.Beacon
import de.seemoo.at_tracking_detection.detection.TrackingDetectorWorker
import kotlinx.coroutines.runBlocking

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*
import org.junit.Before
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    private lateinit var context: Context
    private lateinit var executor: Executor

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        executor = Executors.newSingleThreadExecutor()
    }


    @Test
    fun useAppContext() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("de.seemoo.at_tracking_detection.debug", appContext.packageName)
    }

    @Test
    fun TrackingDetectionTimeDifferenceTest() {


        val validBeacons1 = listOf(
            Beacon(
                receivedAt = LocalDateTime.of(2021, 11, 20, 10, 0),
                rssi = -90,
                deviceAddress = "00:00:00:00:00",
                longitude = 8.24823948,
                latitude =  51.4839483,
                mfg = null
            ),
            Beacon(
                receivedAt = LocalDateTime.of(2021, 11, 20, 10, 30),
                rssi = -90,
                deviceAddress = "00:00:00:00:00",
                longitude = 8.24823948,
                latitude =  51.4839483,
                mfg = null
            )
        )

        val validBeacons2 = listOf(
            Beacon(
                receivedAt = LocalDateTime.of(2021, 11, 20, 10, 0),
                rssi = -90,
                deviceAddress = "00:00:00:00:00",
                longitude = 8.24823948,
                latitude =  51.4839483,
                mfg = null
            ),
            Beacon(
                receivedAt = LocalDateTime.of(2021, 11, 20, 10, 45),
                rssi = -90,
                deviceAddress = "00:00:00:00:00",
                longitude = 8.24823948,
                latitude =  51.4839483,
                mfg = null
            )
        )

        val validBeacons3 = listOf(
            Beacon(
                receivedAt = LocalDateTime.of(2021, 11, 20, 10, 0),
                rssi = -90,
                deviceAddress = "00:00:00:00:00",
                longitude = 8.24823948,
                latitude =  51.4839483,
                mfg = null
            ),
            Beacon(
                receivedAt = LocalDateTime.of(2021, 11, 22, 10, 45),
                rssi = -90,
                deviceAddress = "00:00:00:00:00",
                longitude = 8.24823948,
                latitude =  51.4839483,
                mfg = null
            )
        )

        val invalidBeacons1 = listOf(
            Beacon(
                receivedAt = LocalDateTime.of(2021, 11, 20, 10, 0),
                rssi = -90,
                deviceAddress = "00:00:00:00:00",
                longitude = 8.24823948,
                latitude =  51.4839483,
                mfg = null
            ),
            Beacon(
                receivedAt = LocalDateTime.of(2021, 11, 20, 10, 0),
                rssi = -90,
                deviceAddress = "00:00:00:00:00",
                longitude = 8.24823948,
                latitude =  51.4839483,
                mfg = null
            )
        )

        val invalidBeacons2 = listOf(
            Beacon(
                receivedAt = LocalDateTime.of(2021, 11, 20, 10, 0),
                rssi = -90,
                deviceAddress = "00:00:00:00:00",
                longitude = 8.24823948,
                latitude =  51.4839483,
                mfg = null
            ),
            Beacon(
                receivedAt = LocalDateTime.of(2021, 11, 20, 10, 29),
                rssi = -90,
                deviceAddress = "00:00:00:00:00",
                longitude = 8.24823948,
                latitude =  51.4839483,
                mfg = null
            )
        )

        val invalidBeacons3 = listOf(
            Beacon(
                receivedAt = LocalDateTime.of(2021, 11, 20, 10, 0),
                rssi = -90,
                deviceAddress = "00:00:00:00:00",
                longitude = 8.24823948,
                latitude =  51.4839483,
                mfg = null
            ),
            Beacon(
                receivedAt = LocalDateTime.of(2021, 11, 20, 10, 20),
                rssi = -90,
                deviceAddress = "00:00:00:00:00",
                longitude = 8.24823948,
                latitude =  51.4839483,
                mfg = null
            )
        )

        runBlocking {
            assertTrue(TrackingDetectorWorker.isTrackingForEnoughTime(validBeacons1))
            assertTrue(TrackingDetectorWorker.isTrackingForEnoughTime(validBeacons2))
            assertTrue(TrackingDetectorWorker.isTrackingForEnoughTime(validBeacons3))

            assertFalse(TrackingDetectorWorker.isTrackingForEnoughTime(invalidBeacons1))
            assertFalse(TrackingDetectorWorker.isTrackingForEnoughTime(invalidBeacons2))
            assertFalse(TrackingDetectorWorker.isTrackingForEnoughTime(invalidBeacons3))
        }

    }
}