package de.seemoo.at_tracking_detection.ui.dashboard

import android.content.Context
import androidx.fragment.app.FragmentActivity
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.play.core.review.ReviewInfo
import com.google.android.play.core.review.ReviewManager
import com.google.android.play.core.review.ReviewManagerFactory
import de.seemoo.at_tracking_detection.util.SharedPrefs
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReviewController @Inject constructor(
    private val context: Context
) {
    private var reviewManager: ReviewManager? = null
    private var reviewInfo: ReviewInfo? = null
    private var isReviewFlowReady = false
    private var pendingReviewRequest: (() -> Unit)? = null

    companion object {
        private const val REVIEW_THRESHOLD = 20
    }

    init {
        if (isGooglePlayServicesAvailable()) {
            reviewManager = ReviewManagerFactory.create(context)
            prepareReviewFlow()
        }
    }

    private fun isGooglePlayServicesAvailable(): Boolean {
        val googleApiAvailability = GoogleApiAvailability.getInstance()
        val connectionResult = googleApiAvailability.isGooglePlayServicesAvailable(context)
        return connectionResult == ConnectionResult.SUCCESS
    }

    fun incrementAppOpenCount() {
        val currentCount: Int = SharedPrefs.appOpenCount
        SharedPrefs.appOpenCount = currentCount + 1
        Timber.d("App opened ${currentCount + 1} times")
    }

    private fun prepareReviewFlow() {
        reviewManager?.let { manager ->
            val request = manager.requestReviewFlow()
            request.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    reviewInfo = task.result
                    isReviewFlowReady = true
                    Timber.d("Review flow prepared successfully")

                    // Execute pending review request if any
                    pendingReviewRequest?.invoke()
                    pendingReviewRequest = null
                } else {
                    Timber.w("Failed to prepare review flow: ${task.exception}")
                    isReviewFlowReady = false
                }
            }
        }
    }

    fun shouldShowReview(): Boolean {
        if (!isGooglePlayServicesAvailable()) {
            Timber.d("Google Play Services not available")
            return false
        }

        val appOpenCount: Int = SharedPrefs.appOpenCount
        val reviewShown: Boolean = SharedPrefs.reviewShown

        Timber.d("Review check - appOpenCount: $appOpenCount, reviewShown: $reviewShown, reviewFlowReady: $isReviewFlowReady, reviewInfo: ${reviewInfo != null}")

        return appOpenCount >= REVIEW_THRESHOLD && !reviewShown && isReviewFlowReady && reviewInfo != null
    }

    fun requestReviewDialog(activity: FragmentActivity, onComplete: () -> Unit = {}) {
        if (!isGooglePlayServicesAvailable()) {
            Timber.d("Google Play Services not available, skipping review")
            onComplete()
            return
        }

        val appOpenCount: Int = SharedPrefs.appOpenCount
        val reviewShown: Boolean = SharedPrefs.reviewShown

        if (appOpenCount < REVIEW_THRESHOLD || reviewShown) {
            Timber.d("Review conditions not met - count: $appOpenCount, shown: $reviewShown")
            onComplete()
            return
        }

        if (isReviewFlowReady && reviewInfo != null) {
            showReviewDialog(activity, onComplete)
        } else {
            Timber.d("Review flow not ready yet, queuing request")
            pendingReviewRequest = {
                if (shouldShowReview()) {
                    showReviewDialog(activity, onComplete)
                } else {
                    onComplete()
                }
            }
        }
    }

    private fun showReviewDialog(activity: FragmentActivity, onComplete: () -> Unit = {}) {
        reviewManager?.let { manager ->
            reviewInfo?.let { info ->
                Timber.d("Launching review flow")
                val flow = manager.launchReviewFlow(activity, info)
                flow.addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Timber.d("Review flow completed successfully")
                        markReviewAsShown()
                    } else {
                        Timber.w("Review flow failed: ${task.exception}")
                    }
                    onComplete()
                }
            } ?: run {
                Timber.w("ReviewInfo is null, cannot show review")
                onComplete()
            }
        } ?: run {
            Timber.w("ReviewManager is null, cannot show review")
            onComplete()
        }
    }

    private fun markReviewAsShown() {
        SharedPrefs.reviewShown = true
        Timber.d("Review marked as shown")
    }

    fun getAppOpenCount(): Int {
        return SharedPrefs.appOpenCount
    }

    fun resetReviewStatus() {
        SharedPrefs.reviewShown = false
        Timber.d("Review status reset")
    }

    fun debugReviewStatus() {
        Timber.d("=== Review Debug Info ===")
        Timber.d("Google Play Services available: ${isGooglePlayServicesAvailable()}")
        Timber.d("App open count: ${SharedPrefs.appOpenCount}")
        Timber.d("Review shown: ${SharedPrefs.reviewShown}")
        Timber.d("Review flow ready: $isReviewFlowReady")
        Timber.d("ReviewInfo available: ${reviewInfo != null}")
        Timber.d("Should show review: ${shouldShowReview()}")
        Timber.d("========================")
    }
}