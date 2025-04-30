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

    val lastScan:Scan?
        get() = scanDao.lastScan()

    val lastCompletedScan:Scan?
        get() = scanDao.lastCompletedScan()

    val relevantScans: List<Scan>
        get() = this.scanDao.getScansSince(RiskLevelEvaluator.getRelevantTrackingDateForTrackingDetection())

    fun relevantScans(manual: Boolean, limit: Int): List<Scan> = scanDao.getScansSince(RiskLevelEvaluator.getRelevantTrackingDateForTrackingDetection(), manual, limit)

    val relevantDebugScans: List<Scan>
        get() = scanDao.getDebugScansSince(RiskLevelEvaluator.getRelevantTrackingDateForTrackingDetection())

    val flowDebugScans
        get() = scanDao.getFlowDebugRelevantScans(RiskLevelEvaluator.getRelevantTrackingDateForTrackingDetection())

    val allScans: List<Scan>
        get() = scanDao.getAllScans()

    val flowAllScans: Flow<List<Scan>>
        get() = scanDao.getFlowAllScans()

    val totalCount: Int
        get() = scanDao.getNumberOfScans()

    val countInRelevantTime: Int
        get() = scanDao.getNumberOfScansSince(RiskLevelEvaluator.getRelevantTrackingDateForTrackingDetection())

    val relevantUnfinishedScans: List<Scan>
        get() = scanDao.unfinishedScans(RiskLevelEvaluator.getRelevantTrackingDateForTrackingDetection())

    @WorkerThread
    suspend fun insert(scan: Scan): Long = scanDao.insert(scan)

    @WorkerThread
    suspend fun  deleteIrrelevantScans() = scanDao.deleteUntil(RiskLevelEvaluator.relevantTrackingDateForRiskCalculation)

    @WorkerThread
    suspend fun update(scan: Scan) = scanDao.update(scan)

    fun scanWithId(scanId: Int) = scanDao.scanWithId(scanId)
}