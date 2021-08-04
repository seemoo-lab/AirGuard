package de.seemoo.at_tracking_detection.ui.feedback

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.seemoo.at_tracking_detection.database.repository.FeedbackRepository
import de.seemoo.at_tracking_detection.database.tables.Feedback
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class FeedbackViewModel @Inject constructor(
    private val feedbackRepository: FeedbackRepository
) : ViewModel() {

    val location = MutableLiveData<String>()

    private val feedbackId = MutableLiveData<Int>()

    fun submitFeedback(notificationId: Int) {
        val feedback = if (feedbackId.value == null) {
            Feedback(notificationId, location.value)
        } else {
            Feedback(
                feedbackRepository.getFeedback(notificationId).feedbackId,
                notificationId,
                location.value
            )
        }
        viewModelScope.launch {
            feedbackRepository.insert(feedback)
            Timber.d("Feedback submitted!")
        }
    }

    fun loadFeedback(notificationId: Int) {
        Timber.d("Loading feedback...")
        try {
            val feedback = feedbackRepository.getFeedback(notificationId)
            feedbackId.postValue(feedback.feedbackId)
            location.value = (feedback.location)
        } catch (e: NullPointerException) {
            Timber.d("No feedback found!")
        }
    }
}