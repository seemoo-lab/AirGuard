package de.seemoo.at_tracking_detection.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import dagger.hilt.android.AndroidEntryPoint
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.database.repository.NotificationRepository
import de.seemoo.at_tracking_detection.ui.tracking.TrackingFragment
import de.seemoo.at_tracking_detection.ui.tracking.TrackingFragmentArgs
import de.seemoo.at_tracking_detection.util.SharedPrefs
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

        enableEdgeToEdge()

        // Prevent Screenshots, if set in settings
        if (SharedPrefs.preventScreenshots) {
            window.setFlags(
                android.view.WindowManager.LayoutParams.FLAG_SECURE,
                android.view.WindowManager.LayoutParams.FLAG_SECURE
            )
        }

        setContentView(R.layout.activity_tracking)

        // Automagically handle Padding for Content inside Fragments to prevent cut-offs
        setupFragmentContentPadding()

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

    /**
     * Similar to MainActivity but without BottomNavigation
     */
    private fun setupFragmentContentPadding() {
        supportFragmentManager.registerFragmentLifecycleCallbacks(object : FragmentManager.FragmentLifecycleCallbacks() {
            override fun onFragmentViewCreated(fm: FragmentManager, f: Fragment, v: View, savedInstanceState: Bundle?) {

                // Handle Scrolling Content (RecyclerView, etc.)
                val scrollingView = findScrollingView(v) ?: v

                if (scrollingView is ViewGroup) {
                    scrollingView.clipToPadding = false
                }

                ViewCompat.setOnApplyWindowInsetsListener(scrollingView) { view, insets ->
                    val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

                    // Apply padding for Status Bar (top) and Navigation Bar (bottom)
                    view.updatePadding(
                        top = bars.top,
                        bottom = bars.bottom
                    )
                    insets
                }

                // Handle Floating Action Buttons
                val fabs = findFloatingActionButtons(v)
                fabs.forEach { fab ->
                    val originalFabMargin = (fab.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin

                    ViewCompat.setOnApplyWindowInsetsListener(fab) { view, insets ->
                        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

                        view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                            // Ensure FAB floats above the system navigation bar
                            bottomMargin = originalFabMargin + bars.bottom
                        }
                        insets
                    }
                }
            }
        }, true)
    }

    private fun findScrollingView(view: View): View? {
        if (view is RecyclerView || view is NestedScrollView || view is ScrollView) {
            return view
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val child = view.getChildAt(i)
                val result = findScrollingView(child)
                if (result != null) return result
            }
        }
        return null
    }

    private fun findFloatingActionButtons(view: View): List<FloatingActionButton> {
        val fabs = mutableListOf<FloatingActionButton>()
        if (view is FloatingActionButton) {
            fabs.add(view)
        } else if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                fabs.addAll(findFloatingActionButtons(view.getChildAt(i)))
            }
        }
        return fabs
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