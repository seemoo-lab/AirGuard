package de.seemoo.at_tracking_detection.ui

import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.os.StrictMode
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.marginBottom
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import dagger.hilt.android.AndroidEntryPoint
import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication
import de.seemoo.at_tracking_detection.BuildConfig
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.util.MapUtils
import de.seemoo.at_tracking_detection.util.SharedPrefs
import de.seemoo.at_tracking_detection.util.ble.BLEScanner
import de.seemoo.at_tracking_detection.worker.BackgroundWorkScheduler
import org.osmdroid.config.Configuration
import timber.log.Timber
import java.time.LocalDateTime
import java.time.ZoneOffset
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity(), SharedPreferences.OnSharedPreferenceChangeListener {
    @Inject
    lateinit var sharedPreferences: SharedPreferences

    @Inject
    lateinit var backgroundWorkScheduler: BackgroundWorkScheduler

    private var floatingNavHeight = 0
    private lateinit var navView: BottomNavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        Timber.d("MainActivity onCreate called")

        enableEdgeToEdge()

        if (BuildConfig.DEBUG && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder()
                .detectUnsafeIntentLaunch()
                .penaltyLog()
                .build())
        }

        super.onCreate(savedInstanceState)
        updateSecureFlag()
        setContentView(R.layout.activity_main)

        sharedPreferences.registerOnSharedPreferenceChangeListener(this)

        // Initialize osmdroid configuration
        MapUtils.initializeOsmDroidConfiguration(this)

        navView = findViewById(R.id.main_nav_view)

        if (BuildConfig.DEBUG) {
            Configuration.getInstance().isDebugMode = true
        }

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.main_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        navView.setupWithNavController(navController)

        if (!SharedPrefs.advancedMode) {
            val menu = navView.menu
            val item = menu.findItem(R.id.navigation_allDevicesFragment)
            item.isVisible = false
        }

        // Handle Floating Nav Bar Positioning
        setupFloatingBottomNavigation(navView)

        // Automagically handle Padding for Content inside Fragments
        setupFragmentContentPadding()

        val navOptions = androidx.navigation.NavOptions.Builder()
            .setPopUpTo(R.id.main_navigation, inclusive = true, saveState = false)
            .setLaunchSingleTop(true)
            .build()

        navView.setOnItemSelectedListener {
            when (it.itemId) {
                R.id.navigation_dashboard -> navController.navigate(R.id.navigation_dashboard, null, navOptions)
                R.id.navigation_manual_scan -> navController.navigate(R.id.navigation_manual_scan, null, navOptions)
                R.id.navigation_allDevicesFragment -> navController.navigate(R.id.navigation_allDevicesFragment, null, navOptions)
                R.id.navigation_settings -> navController.navigate(R.id.navigation_settings, null, navOptions)
                R.id.navigation_debug -> navController.navigate(R.id.navigation_debug, null, navOptions)
            }
            return@setOnItemSelectedListener true
        }
    }

    /**
     * Lifts the BottomNavigationView above the system gesture area.
     * Also calculates the total height needed for content padding.
     */
    private fun setupFloatingBottomNavigation(navView: BottomNavigationView) {
        val originalBottomMargin = (navView.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin

        ViewCompat.setOnApplyWindowInsetsListener(navView) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            // Update bottom margin to clear system gestures
            view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = originalBottomMargin + bars.bottom
            }

            // Store total height immediately
            floatingNavHeight = view.height + view.marginBottom
            if (floatingNavHeight == 0) {
                // Fallback estimate if not laid out yet: approx. 80dp + bars.bottom
                // (56dp nav + 16dp margin + 8dp elevation/shadow approx)
                val density = view.resources.displayMetrics.density
                val approximateHeight = (88 * density).toInt()
                floatingNavHeight = approximateHeight + bars.bottom
            }
            insets
        }
    }

    /**
     * Registers a lifecycle callback to intercept Fragments as they are created.
     * It finds the scrollable view and applies padding.
     * It finds FABs and applies margins.
     */
    private fun setupFragmentContentPadding() {
        supportFragmentManager.registerFragmentLifecycleCallbacks(object : FragmentManager.FragmentLifecycleCallbacks() {
            override fun onFragmentViewCreated(fm: FragmentManager, f: Fragment, v: View, savedInstanceState: Bundle?) {

                // Handle Scrolling Content (RecyclerView, etc.)
                // DevicesFragment handles its own layering
                if (f.javaClass.simpleName != "DevicesFragment") {
                    val scrollingView = findScrollingView(v) ?: v

                    // allows content to scroll behind the nav bar
                    if (scrollingView is ViewGroup) {
                        scrollingView.clipToPadding = false
                    }

                    ViewCompat.setOnApplyWindowInsetsListener(scrollingView) { view, insets ->
                        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

                        // Use the calculated floatingNavHeight or current height
                        val currentNavHeight = if (navView.height > 0)
                            navView.height + navView.marginBottom
                        else
                            floatingNavHeight

                        view.updatePadding(
                            top = bars.top,
                            bottom = currentNavHeight
                        )
                        insets
                    }

                    // Fix for Articles behind the navbar (Race Condition)
                    // waits for the NavView, forces one update, then removes itself
                    if (navView.isLaidOut && navView.height > 0) {
                        // If NavView is already ready, just trigger the update now
                        ViewCompat.requestApplyInsets(scrollingView)
                    } else {
                        // wait for NavView to be ready
                        navView.addOnLayoutChangeListener(object : View.OnLayoutChangeListener {
                            override fun onLayoutChange(
                                v: View?, left: Int, top: Int, right: Int, bottom: Int,
                                oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int
                            ) {
                                // Remove itself
                                navView.removeOnLayoutChangeListener(this)

                                // Force scrolling view to update its padding
                                ViewCompat.requestApplyInsets(scrollingView)
                            }
                        })
                    }
                }

                // Handle Floating Action Buttons
                // Exclude DeviceMapFragment because it manually manages its own Legend FAB positioning
                if (f.javaClass.simpleName != "DeviceMapFragment") {
                    val fabs = findFloatingActionButtons(v)
                    fabs.forEach { fab ->
                        val originalFabMargin = (fab.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin

                        ViewCompat.setOnApplyWindowInsetsListener(fab) { view, insets ->
                            val currentNavHeight = if (navView.height > 0)
                                navView.height + navView.marginBottom
                            else
                                floatingNavHeight

                            view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                                // Ensure FAB floats above the Nav Bar
                                bottomMargin = originalFabMargin + currentNavHeight
                            }
                            insets
                        }
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

    private fun updateSecureFlag() {
        if (SharedPrefs.preventScreenshots) {
            window.setFlags(
                android.view.WindowManager.LayoutParams.FLAG_SECURE,
                android.view.WindowManager.LayoutParams.FLAG_SECURE
            )
        } else {
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            if (!BLEScanner.startBluetoothScan(this.applicationContext)) {
                Timber.e("Failed to start Bluetooth scan.")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error starting Bluetooth scan")
        }
        backgroundWorkScheduler.scheduleImmediateBackgroundScan()
    }

    override fun onPause() {
        super.onPause()
        try {
            BLEScanner.stopBluetoothScan()
        } catch (e: Exception) {
            Timber.e(e, "Error stopping Bluetooth scan")
        }
    }

    override fun onStart() {
        super.onStart()
        if (ATTrackingDetectionApplication.getCurrentApp().showOnboarding() or !ATTrackingDetectionApplication.getCurrentApp().hasPermissions()) {
            ATTrackingDetectionApplication.getCurrentApp().startOnboarding()
        } else {
            backgroundWorkScheduler.launch()
        }
    }

    override fun onDestroy() {
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
        SharedPrefs.lastTimeOpened = LocalDateTime.now(ZoneOffset.UTC)
        super.onDestroy()
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.main_host_fragment)
        return navController.navigateUp() || super.onSupportNavigateUp()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == "advanced_mode") {
            val navView: BottomNavigationView = findViewById(R.id.main_nav_view)
            val menu = navView.menu
            val item = menu.findItem(R.id.navigation_allDevicesFragment)
            item.isVisible = sharedPreferences?.getBoolean(key, false) ?: false
        } else if (key == "prevent_screenshots") {
            updateSecureFlag()
        } else if (key == "app_theme" || key == "use_dynamic_colors") {
            recreate()
        }
    }
}