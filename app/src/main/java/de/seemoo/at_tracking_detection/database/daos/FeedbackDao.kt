package de.seemoo.at_tracking_detection.database.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import de.seemoo.at_tracking_detection.database.models.Feedback

@Dao
interface FeedbackDao {
    @Query("SELECT * FROM feedback WHERE notificationId = :notificationId LIMIT 1")
    fun getFeedback(notificationId: Int): Feedback?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(feedback: Feedback): Long

    @Update
    suspend fun update(feedback: Feedback)
}