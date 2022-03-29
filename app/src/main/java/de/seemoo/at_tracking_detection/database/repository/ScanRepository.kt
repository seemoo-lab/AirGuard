package de.seemoo.at_tracking_detection.database.repository

import androidx.annotation.WorkerThread
import de.seemoo.at_tracking_detection.database.daos.ScanDao
import de.seemoo.at_tracking_detection.database.models.Scan
import de.seemoo.at_tracking_detection.util.risk.RiskLevelEvaluator
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime
import javax.inject.Inject

class ScanRepository @Inject constructor(
    private val scanDao: ScanDao
) {

    var lastScan = scanDao.lastScan()

    var relevantScans =
        scanDao.getScansSince(RiskLevelEvaluator.relevantTrackingDate)

    fun relevantScans(manual: Boolean, limit: Int): List<Scan> = scanDao.getScansSince(RiskLevelEvaluator.relevantTrackingDate, manual, limit)

    var flowRelevantScans =
        scanDao.getFlowScansSince(RiskLevelEvaluator.relevantTrackingDate)

    var allScans: List<Scan> = scanDao.getAllScans()

    var flowAllScans: Flow<List<Scan>> = scanDao.getFlowAllScans()

    val totalCount: Int = scanDao.getNumberOfScans()

    var countInRelevantTime: Int = scanDao.getNumberOfScansSince(RiskLevelEvaluator.relevantTrackingDate)

    @WorkerThread
    suspend fun insert(scan: Scan): Long = scanDao.insert(scan)

    @WorkerThread
    suspend fun  deleteIrrelevantScans() = scanDao.deleteUntil(RiskLevelEvaluator.relevantTrackingDate)
}