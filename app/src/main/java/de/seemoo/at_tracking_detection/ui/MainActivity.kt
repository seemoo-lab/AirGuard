package de.seemoo.at_tracking_detection.ui

import android.content.SharedPreferences
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavOptions
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import androidx.preference.PreferenceManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.elevation.SurfaceColors
import dagger.hilt.android.AndroidEntryPoint
import de.seemoo.at_tracking_detection.BuildConfig
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.util.SharedPrefs
import de.seemoo.at_tracking_detection.util.ble.BLEScanner
import de.seemoo.at_tracking_detection.worker.BackgroundWorkScheduler
import org.osmdroid.config.Configuration
import timber.log.Timber
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneOffset
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity(), SharedPreferences.OnSharedPreferenceChangeListener {
    @Inject
    lateinit var sharedPreferences: SharedPreferences

    @Inject
    lateinit var backgroundWorkScheduler: BackgroundWorkScheduler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sharedPreferences.registerOnSharedPreferenceChangeListener(this)

        window.navigationBarColor = SurfaceColors.SURFACE_2.getColor(this)
        val configuration = Configuration.getInstance()
        configuration.load(this, PreferenceManager.getDefaultSharedPreferences(this))
        setContentView(R.layout.activity_main)
        val navView: BottomNavigationView = findViewById(R.id.main_nav_view)

        configuration.userAgentValue = BuildConfig.APPLICATION_ID
        // Crete osmdroid folder
        val osmDroidDir = File(filesDir, "osmDroid")
        osmDroidDir.mkdir()
        val tilesDir = File(osmDroidDir, "tiles")
        configuration.osmdroidBasePath = osmDroidDir
        configuration.osmdroidTileCache = tilesDir

        if (BuildConfig.DEBUG) {
            Configuration.getInstance().isDebugMode = true
        }

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.main_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        val appBarItems: Set<Int> = setOf(
            R.id.navigation_dashboard,
            R.id.navigation_manual_scan,
            R.id.navigation_allDevicesFragment,
            R.id.navigation_settings
        )
        if (BuildConfig.DEBUG) {
            appBarItems.plus(R.id.navigation_debug)
        }

        if (!SharedPrefs.advancedMode) {
            val menu = navView.menu
            val item = menu.findItem(R.id.navigation_allDevicesFragment)
            item.isVisible = false
        }

        val appBarConfiguration = AppBarConfiguration(appBarItems)
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

        val navOptions = NavOptions.Builder().setPopUpTo(R.id.main_navigation, inclusive = true, saveState = false).setLaunchSingleTop(true).build()

        navView.setOnItemSelectedListener {
            when (it.itemId) {
                R.id.navigation_dashboard -> navController.navigate(R.id.navigation_dashboard, args=null, navOptions = navOptions)
                R.id.navigation_manual_scan -> navController.navigate(R.id.navigation_manual_scan, args=null, navOptions = navOptions)
                R.id.navigation_allDevicesFragment -> navController.navigate(R.id.navigation_allDevicesFragment, args=null, navOptions = navOptions)
                R.id.navigation_settings -> navController.navigate(R.id.navigation_settings, args=null, navOptions = navOptions)
                R.id.navigation_debug -> navController.navigate(R.id.navigation_debug, args=null, navOptions = navOptions)
            }
            return@setOnItemSelectedListener true
        }
    }

    override fun onResume() {
        super.onResume()
        Timber.d("MainActivity onResume called")
        BLEScanner.startBluetoothScan(this.applicationContext)
    }


    override fun onPause() {
        super.onPause()
        Timber.d("MainActivity onPause called")
        BLEScanner.stopBluetoothScan()
    }


    override fun onDestroy() {
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
        SharedPrefs.lastTimeOpened = dateTime
        super.onDestroy()
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.main_host_fragment)
        return navController.navigateUp() || super.onSupportNavigateUp()
    }

    companion object {
        private val dateTime = LocalDateTime.now(ZoneOffset.UTC)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        // Check if the changed preference is the advancedMode
        if (key == "advanced_mode") {
            // Update the visibility of the All Devices fragment menu item
            val navView: BottomNavigationView = findViewById(R.id.main_nav_view)
            val menu = navView.menu
            val item = menu.findItem(R.id.navigation_allDevicesFragment)
            item.isVisible = sharedPreferences?.getBoolean(key, false) ?: false
        }
    }
}