package de.seemoo.at_tracking_detection.database.daos

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import de.seemoo.at_tracking_detection.database.models.Scan
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

@Dao
interface ScanDao {
    @Query("SELECT * FROM scan WHERE endDate >= :since ORDER by endDate DESC")
    fun getScansSince(since: LocalDateTime): List<Scan>

    @Query("SELECT * FROM scan WHERE startDate >= :since ORDER by startDate DESC")
    fun getDebugScansSince(since: LocalDateTime): List<Scan>

    @Query("SELECT * FROM scan WHERE endDate >= :since AND isManual = :manual ORDER by endDate DESC LIMIT :limit")
    fun getScansSince(since: LocalDateTime, manual: Boolean, limit: Int): List<Scan>

    @Query("SELECT * FROM scan WHERE endDate >= :since ORDER by endDate DESC")
    fun getFlowScansSince(since: LocalDateTime): Flow<List<Scan>>

    @Query("SELECT * FROM scan ORDER by endDate DESC")
    fun getAllScans(): List<Scan>

    @Query("SELECT * FROM scan ORDER by endDate DESC")
    fun getFlowAllScans(): Flow<List<Scan>>

    @Query("SELECT * FROM scan WHERE startDate >= :since ORDER by startDate DESC")
    fun getFlowDebugRelevantScans(since: LocalDateTime): Flow<List<Scan>>

    @Query("SELECT COUNT(*) FROM scan WHERE endDate >= :since ORDER by endDate DESC")
    fun getNumberOfScansSince(since: LocalDateTime): Int

    @Query("SELECT COUNT(*) FROM scan ORDER by endDate DESC")
    fun getNumberOfScans(): Int

    @Query("SELECT * FROM scan ORDER by endDate DESC LIMIT 1")
    fun lastScan(): Scan

    @Query("SELECT * FROM scan WHERE scanId == :scanId")
    fun scanWithId(scanId: Int): Scan?

    @Query("SELECT * FROM scan WHERE startDate == NULL AND startDate >= :since")
    fun unfinishedScans(since: LocalDateTime): List<Scan>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(scan: Scan): Long

    @Delete
    suspend fun deleteScans(vararg scan: Scan)

    @Query("DELETE FROM scan WHERE endDate <= :until")
    suspend fun deleteUntil(until: LocalDateTime): Int

    @Update
    suspend fun update(scan:Scan)
}