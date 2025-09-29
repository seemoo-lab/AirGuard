package de.seemoo.at_tracking_detection.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import dagger.hilt.android.AndroidEntryPoint
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.database.repository.NotificationRepository
import de.seemoo.at_tracking_detection.ui.tracking.TrackingFragment
import de.seemoo.at_tracking_detection.ui.tracking.TrackingFragmentArgs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class TrackingNotificationActivity : AppCompatActivity() {

    @Inject
    lateinit var notificationRepository: NotificationRepository

    private lateinit var navController: NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tracking)

        // For notification entry: disable edge-to-edge so content area automatically respects system bars
        MainActivity.configureSystemBars(this, edgeToEdge = true, applyRootPadding = true)

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.tracking_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        // Only navigate on fresh creation or when a new intent with different device arrives
        if (savedInstanceState == null) {
            navigateToTrackingFragment(firstTime = true)
        } else {
            updateActionBarTitle()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBackNavigation()
            }
        })
    }

    override fun onResume() {
        super.onResume()
        MainActivity.configureSystemBars(this, edgeToEdge = false, applyRootPadding = false)
        val fragment = supportFragmentManager.findFragmentById(R.id.tracking_host_fragment)
        if (fragment is NavHostFragment) {
            val trackingFragment = fragment.childFragmentManager.primaryNavigationFragment
            if (trackingFragment is TrackingFragment) {
                trackingFragment.mapView.onResume()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        val fragment = supportFragmentManager.findFragmentById(R.id.tracking_host_fragment)
        if (fragment is NavHostFragment) {
            val trackingFragment = fragment.childFragmentManager.primaryNavigationFragment
            if (trackingFragment is TrackingFragment) {
                trackingFragment.mapView.onPause()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        navigateToTrackingFragment(replaceGraph = true, firstTime = true)
    }

    private fun navigateToTrackingFragment(replaceGraph: Boolean = false, firstTime: Boolean = false) {
        val deviceAddress = intent.getStringExtra("deviceAddress")
        val deviceTypeAsString = intent.getStringExtra("deviceTypeAsString") ?: "UNKNOWN"
        val notificationId = intent.getIntExtra("notificationId", -1)
        Timber.d("Tracking Activity with device $deviceAddress and notification $notificationId started!")

        if (notificationId != -1) {
            lifecycleScope.launch(Dispatchers.IO) {
                notificationRepository.setClicked(notificationId, true)
            }
        }

        if (deviceAddress == null) {
            Timber.e("Device was not provided! Finishing TrackingNotificationActivity.")
            finish()
            return
        }

        updateActionBarTitle(deviceAddress)
        val args = TrackingFragmentArgs(deviceAddress, deviceTypeAsString, notificationId).toBundle()

        if (replaceGraph || (firstTime && navController.currentDestination == null)) {
            navController.setGraph(R.navigation.main_navigation)
            // When accessing through Notification: trackingFragment is the root
            val startDest = navController.graph.startDestinationId
            val navOptions = NavOptions.Builder()
                .setPopUpTo(startDest, inclusive = true)
                .setLaunchSingleTop(true)
                .build()
            navController.navigate(R.id.trackingFragment, args, navOptions)
            return
        }

        if (navController.currentDestination?.id != R.id.trackingFragment) {
            navController.navigate(
                R.id.trackingFragment,
                args,
                NavOptions.Builder().setLaunchSingleTop(true).build()
            )
        }
    }

    private fun updateActionBarTitle(deviceAddress: String? = intent.getStringExtra("deviceAddress")) {
        deviceAddress ?: return
        var getTitle = getString(R.string.title_devices_tracking)
        getTitle = getTitle.replace("{deviceAddress}", deviceAddress)
        supportActionBar?.title = getTitle
    }

    private fun handleBackNavigation() {
        if (navController.currentDestination?.id != R.id.trackingFragment && navController.popBackStack()) {
            return
        }
        // When at root --> finish
        finish()
    }

    override fun onSupportNavigateUp(): Boolean {
        handleBackNavigation()
        return true
    }
}