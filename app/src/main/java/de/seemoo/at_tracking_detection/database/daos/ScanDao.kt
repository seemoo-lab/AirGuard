package de.seemoo.at_tracking_detection.database.daos

import androidx.room.*
import de.seemoo.at_tracking_detection.database.models.Beacon
import de.seemoo.at_tracking_detection.database.models.Scan
import java.time.LocalDateTime
import kotlinx.coroutines.flow.Flow
import kotlin.reflect.jvm.internal.impl.descriptors.Visibilities

@Dao
interface ScanDao {
    @Query("SELECT * FROM scan WHERE date >= :since ORDER by date DESC")
    fun getScansSince(since: LocalDateTime): List<Scan>

    @Query("SELECT * FROM scan WHERE date >= :since AND isManual = :manual ORDER by date DESC LIMIT :limit")
    fun getScansSince(since: LocalDateTime, manual: Boolean, limit: Int): List<Scan>

    @Query("SELECT * FROM scan WHERE date >= :since ORDER by date DESC")
    fun getFlowScansSince(since: LocalDateTime): Flow<List<Scan>>

    @Query("SELECT * FROM scan ORDER by date DESC")
    fun getAllScans(): List<Scan>

    @Query("SELECT * FROM scan ORDER by date DESC")
    fun getFlowAllScans(): Flow<List<Scan>>

    @Query("SELECT COUNT(*) FROM scan WHERE date >= :since ORDER by date DESC")
    fun getNumberOfScansSince(since: LocalDateTime): Int

    @Query("SELECT COUNT(*) FROM scan ORDER by date DESC")
    fun getNumberOfScans(): Int

    @Query("SELECT * FROM scan ORDER by date DESC LIMIT 1")
    fun lastScan(): Scan

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(scan: Scan): Long

    @Delete
    suspend fun deleteScans(vararg scan: Scan)

    @Query("DELETE FROM scan WHERE date <= :until")
    suspend fun deleteUntil(until: LocalDateTime): Int
}