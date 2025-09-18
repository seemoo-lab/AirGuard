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
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.tracking_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        navigateToTrackingFragment()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                onSupportNavigateUp()
            }
        })
    }

    override fun onSupportNavigateUp(): Boolean {
        return if (navController.currentDestination?.id == R.id.trackingFragment) {
            finish()
            true
        } else {
            navController.navigateUp()
        }
    }

    override fun onResume() {
        super.onResume()
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

        navigateToTrackingFragment()
    }

    private fun navigateToTrackingFragment() {
        val deviceAddress = intent.getStringExtra("deviceAddress")
        val deviceTypeAsString = intent.getStringExtra("deviceTypeAsString") ?: "UNKNOWN"
        val notificationId = intent.getIntExtra("notificationId", -1)
        Timber.d("Tracking Activity with device $deviceAddress and notification $notificationId started!")

        // Mark as clicked if we have a valid notification id
        if (notificationId != -1) {
            lifecycleScope.launch(Dispatchers.IO) {
                notificationRepository.setClicked(notificationId, true)
            }
        }

        if (deviceAddress == null) {
            Timber.e("Device address is needed! Going home...")
            this.onSupportNavigateUp()
        } else {
            // Workaround: Somehow not possible to use getString with deviceAddress as an Argument
            var getTitle = getString(R.string.title_devices_tracking)
            getTitle = getTitle.replace("{deviceAddress}", deviceAddress.toString())
            supportActionBar?.title = getTitle

            val args = TrackingFragmentArgs(
                deviceAddress = deviceAddress,
                deviceTypeAsString = deviceTypeAsString,
                notificationId = notificationId
            ).toBundle()
            navController.setGraph(R.navigation.main_navigation)
            val navOptions = NavOptions.Builder()
                // .setPopUpTo(R.id.navigation_dashboard, true)
                .build()
            navController.navigate(R.id.trackingFragment, args, navOptions)
        }
    }
}