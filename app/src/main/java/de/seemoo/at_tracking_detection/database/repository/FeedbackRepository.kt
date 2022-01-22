package de.seemoo.at_tracking_detection.database.repository

import androidx.annotation.WorkerThread
import de.seemoo.at_tracking_detection.database.daos.FeedbackDao
import de.seemoo.at_tracking_detection.database.models.Feedback
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FeedbackRepository @Inject constructor(private val feedbackDao: FeedbackDao) {

    fun getFeedback(notificationId: Int): Feedback = feedbackDao.getFeedback(notificationId)

    @WorkerThread
    suspend fun insert(feedback: Feedback): Long {
        return feedbackDao.insert(feedback)
    }
}