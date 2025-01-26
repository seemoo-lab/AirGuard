package de.seemoo.at_tracking_detection.database.repository

import androidx.annotation.WorkerThread
import de.seemoo.at_tracking_detection.database.daos.ScanDao
import de.seemoo.at_tracking_detection.database.models.Scan
import de.seemoo.at_tracking_detection.util.risk.RiskLevelEvaluator
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ScanRepository @Inject constructor(
    private val scanDao: ScanDao
) {

    var lastScan = scanDao.lastScan()

    var lastCompletedScan = scanDao.lastCompletedScan()

    var relevantScans =
        scanDao.getScansSince(RiskLevelEvaluator.getRelevantTrackingDateForTrackingDetection())

    fun relevantScans(manual: Boolean, limit: Int): List<Scan> = scanDao.getScansSince(RiskLevelEvaluator.getRelevantTrackingDateForTrackingDetection(), manual, limit)

    val relevantDebugScans = scanDao.getDebugScansSince(RiskLevelEvaluator.getRelevantTrackingDateForTrackingDetection())

    val flowDebugScans = scanDao.getFlowDebugRelevantScans(RiskLevelEvaluator.getRelevantTrackingDateForTrackingDetection())

    var allScans: List<Scan> = scanDao.getAllScans()

    var flowAllScans: Flow<List<Scan>> = scanDao.getFlowAllScans()

    val totalCount: Int = scanDao.getNumberOfScans()

    var countInRelevantTime: Int = scanDao.getNumberOfScansSince(RiskLevelEvaluator.getRelevantTrackingDateForTrackingDetection())

    val relevantUnfinishedScans: List<Scan> = scanDao.unfinishedScans(RiskLevelEvaluator.getRelevantTrackingDateForTrackingDetection())

    @WorkerThread
    suspend fun insert(scan: Scan): Long = scanDao.insert(scan)

    @WorkerThread
    suspend fun  deleteIrrelevantScans() = scanDao.deleteUntil(RiskLevelEvaluator.relevantTrackingDateForRiskCalculation)

    @WorkerThread
    suspend fun update(scan: Scan) = scanDao.update(scan)

    fun scanWithId(scanId: Int) = scanDao.scanWithId(scanId)
}