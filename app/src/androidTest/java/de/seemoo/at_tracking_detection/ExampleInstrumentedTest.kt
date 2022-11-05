package de.seemoo.at_tracking_detection

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.seemoo.at_tracking_detection.database.models.Beacon
import de.seemoo.at_tracking_detection.database.models.device.BaseDevice
import de.seemoo.at_tracking_detection.database.models.device.DeviceType
import de.seemoo.at_tracking_detection.detection.TrackingDetectorWorker
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime
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
    fun trackingDetectionTimeDifferenceTest() {

        val testDevice = BaseDevice(
            address = "00:00:00:00:00",
            ignore = false,
            connectable = true,
            payloadData = null,
            firstDiscovery = LocalDateTime.now(),
            lastSeen = LocalDateTime.now(),
            deviceType = DeviceType.AIRTAG
        )

        val validBeacons1 = listOf(
            Beacon(
                receivedAt = LocalDateTime.of(2021, 11, 20, 10, 0),
                rssi = -90,
                deviceAddress = "00:00:00:00:00",
                // longitude = 8.24823948,
                // latitude = 51.4839483,
                locationId = 1,
                mfg = null,
                serviceUUIDs = null
            ),
            Beacon(
                receivedAt = LocalDateTime.of(2021, 11, 20, 10, 30),
                rssi = -90,
                deviceAddress = "00:00:00:00:00",
                // longitude = 8.24823948,
                // latitude = 51.4839483,
                locationId = 1,
                mfg = null,
                serviceUUIDs = null
            )
        )

        val validBeacons2 = listOf(
            Beacon(
                receivedAt = LocalDateTime.of(2021, 11, 20, 10, 0),
                rssi = -90,
                deviceAddress = "00:00:00:00:00",
                // longitude = 8.24823948,
                // latitude = 51.4839483,
                locationId = 1,
                mfg = null,
                serviceUUIDs = null
            ),
            Beacon(
                receivedAt = LocalDateTime.of(2021, 11, 20, 10, 45),
                rssi = -90,
                deviceAddress = "00:00:00:00:00",
                // longitude = 8.24823948,
                // latitude = 51.4839483,
                locationId = 1,
                mfg = null,
                serviceUUIDs = null
            )
        )

        val validBeacons3 = listOf(
            Beacon(
                receivedAt = LocalDateTime.of(2021, 11, 20, 10, 0),
                rssi = -90,
                deviceAddress = "00:00:00:00:00",
                // longitude = 8.24823948,
                // latitude = 51.4839483,
                locationId = 1,
                mfg = null,
                serviceUUIDs = null
            ),
            Beacon(
                receivedAt = LocalDateTime.of(2021, 11, 22, 10, 45),
                rssi = -90,
                deviceAddress = "00:00:00:00:00",
                // longitude = 8.24823948,
                // latitude = 51.4839483,
                locationId = 1,
                mfg = null,
                serviceUUIDs = null
            )
        )

        val invalidBeacons1 = listOf(
            Beacon(
                receivedAt = LocalDateTime.of(2021, 11, 20, 10, 0),
                rssi = -90,
                deviceAddress = "00:00:00:00:00",
                // longitude = 8.24823948,
                // latitude = 51.4839483,
                locationId = null,
                mfg = null,
                serviceUUIDs = null
            ),
            Beacon(
                receivedAt = LocalDateTime.of(2021, 11, 20, 10, 0),
                rssi = -90,
                deviceAddress = "00:00:00:00:00",
                // longitude = 8.24823948,
                // latitude = 51.4839483,
                locationId = null,
                mfg = null,
                serviceUUIDs = null
            )
        )

        val invalidBeacons2 = listOf(
            Beacon(
                receivedAt = LocalDateTime.of(2021, 11, 20, 10, 0),
                rssi = -90,
                deviceAddress = "00:00:00:00:00",
                // longitude = 8.24823948,
                // latitude = 51.4839483,
                locationId = null,
                mfg = null,
                serviceUUIDs = null
            ),
            Beacon(
                receivedAt = LocalDateTime.of(2021, 11, 20, 10, 29),
                rssi = -90,
                deviceAddress = "00:00:00:00:00",
                // longitude = 8.24823948,
                // latitude = 51.4839483,
                locationId = null,
                mfg = null,
                serviceUUIDs = null
            )
        )

        val invalidBeacons3 = listOf(
            Beacon(
                receivedAt = LocalDateTime.of(2021, 11, 20, 10, 0),
                rssi = -90,
                deviceAddress = "00:00:00:00:00",
                // longitude = 8.24823948,
                // latitude = 51.4839483,
                locationId = null,
                mfg = null,
                serviceUUIDs = null
            ),
            Beacon(
                receivedAt = LocalDateTime.of(2021, 11, 20, 10, 20),
                rssi = -90,
                deviceAddress = "00:00:00:00:00",
                // longitude = 8.24823948,
                // latitude = 51.4839483,
                locationId = null,
                mfg = null,
                serviceUUIDs = null
            )
        )

        runBlocking {
            assertTrue(TrackingDetectorWorker.isTrackingForEnoughTime(testDevice, validBeacons1))
            assertTrue(TrackingDetectorWorker.isTrackingForEnoughTime(testDevice, validBeacons2))
            assertTrue(TrackingDetectorWorker.isTrackingForEnoughTime(testDevice, validBeacons3))

            assertFalse(TrackingDetectorWorker.isTrackingForEnoughTime(testDevice, invalidBeacons1))
            assertFalse(TrackingDetectorWorker.isTrackingForEnoughTime(testDevice, invalidBeacons2))
            assertFalse(TrackingDetectorWorker.isTrackingForEnoughTime(testDevice, invalidBeacons3))
        }

    }
}