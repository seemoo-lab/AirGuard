package de.seemoo.at_tracking_detection.util

import android.app.Activity
import android.app.Application
import android.os.Bundle
import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication
import de.seemoo.at_tracking_detection.ui.OnboardingActivity

class ATTDLifecycleCallbacks : Application.ActivityLifecycleCallbacks {
    lateinit var currentActivity: Activity

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        currentActivity = activity
    }

    override fun onActivityStarted(activity: Activity) {
        currentActivity = activity
    }

    override fun onActivityResumed(activity: Activity) {
        currentActivity = activity

        // Necessary for API 37+
        if (activity !is OnboardingActivity) {
            val app = ATTrackingDetectionApplication.getCurrentApp() ?: return
            if (app.showOnboarding()) {
                app.startOnboarding()
            }
        }
    }

    override fun onActivityPaused(activity: Activity) {

    }

    override fun onActivityStopped(activity: Activity) {

    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {

    }

    override fun onActivityDestroyed(activity: Activity) {

    }
}