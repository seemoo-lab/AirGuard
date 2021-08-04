package de.seemoo.at_tracking_detection.database.daos

import androidx.room.*
import de.seemoo.at_tracking_detection.database.tables.Feedback

@Dao
interface FeedbackDao {
    @Query("SELECT * FROM feedback WHERE notificationId = :notificationId")
    fun getFeedback(notificationId: Int): Feedback

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(feedback: Feedback): Long

    @Update
    suspend fun update(feedback: Feedback)
}