@file:OptIn(ExperimentalCoroutinesApi::class)

package de.seemoo.at_tracking_detection

import android.bluetooth.le.ScanSettings
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.seemoo.at_tracking_detection.database.AppDatabase
import de.seemoo.at_tracking_detection.database.daos.ScanDao
import de.seemoo.at_tracking_detection.database.models.Scan
import de.seemoo.at_tracking_detection.database.repository.ScanRepository
import de.seemoo.at_tracking_detection.util.risk.RiskLevelEvaluator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runBlockingTest
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

@RunWith(AndroidJUnit4::class)
class ScanDBTests() {

    private val TEST_DB = "scan-test"


    val roomDB = Room.databaseBuilder(
        InstrumentationRegistry.getInstrumentation().targetContext,
        AppDatabase::class.java,
        TEST_DB
    ).addMigrations().build().apply {
    }

    val scanRepository = ScanRepository(scanDao = roomDB.scanDao())

    @Test
    @Throws(IOException::class)
    fun insertScanWithoutEndDate() =
        runTest {
            val allScansOld = scanRepository.allScans
            val scan = Scan(
                startDate = LocalDateTime.now(),
                isManual = true,
                scanMode = ScanSettings.SCAN_MODE_LOW_LATENCY
            )
            val scanId = scanRepository.insert(scan)

            allScansOld.forEach {
                assert(it.scanId != scanId.toInt())
            }

            //Get all scans with an end_date
            val scansWithEndDate = scanRepository.relevantScans
            scansWithEndDate.forEach {
                assert(it.endDate != null)
            }

            val insertedScan = scanRepository.scanWithId(scanId.toInt())
            assert(insertedScan != null)
            assert(scan.endDate == insertedScan?.endDate)
            assert(scan.startDate == insertedScan?.startDate)
        }

    @Test
    @Throws(IOException::class)
    fun insertAndRetrieve() =
        runTest {
            val scanInserted =  Scan(
                endDate = LocalDateTime.now(),
                0,
                0,
                true,
                ScanSettings.SCAN_MODE_LOW_LATENCY,
                LocalDateTime.now()
            )
            val scanId = scanRepository.insert(
               scanInserted
            )

            val scan = scanRepository.scanWithId(scanId.toInt())

            assert(scan != null)
            assert(scan?.scanId == scanId.toInt())
            assert(scanInserted.endDate == scan?.endDate)
            assert(scanInserted.startDate == scan?.startDate)
        }

    @Test
    fun getScanWithUnknownId() = runTest {
        val scan = scanRepository.scanWithId(1099919)

        assert(scan == null)
    }

    @Test
    fun getUnfinishedScans() = runTest {
        val unfinished = scanRepository.relevantUnfinishedScans
        unfinished.forEach{
            assert(it.endDate == null)
            assert(it.startDate == null)
            assert(it.startDate?.compareTo(RiskLevelEvaluator.relevantTrackingDate) ?: -1 >= 0)
        }
    }
}