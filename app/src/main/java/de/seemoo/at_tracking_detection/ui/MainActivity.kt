package de.seemoo.at_tracking_detection.ui

import android.content.SharedPreferences
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import androidx.preference.PreferenceManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import dagger.hilt.android.AndroidEntryPoint
import de.seemoo.at_tracking_detection.BuildConfig
import de.seemoo.at_tracking_detection.R
import org.osmdroid.config.Configuration
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneOffset
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject
    lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
            R.id.navigation_ignoredDevices,
            R.id.navigation_dashboard,
            R.id.navigation_settings
        )
        if (BuildConfig.DEBUG) {
            appBarItems.plus(R.id.navigation_debug)
        }
        val appBarConfiguration = AppBarConfiguration(appBarItems)
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

    }

    override fun onDestroy() {
        sharedPreferences.edit().putString("last_time_opened", dateTime.toString()).apply()
        super.onDestroy()
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.main_host_fragment)
        return navController.navigateUp() || super.onSupportNavigateUp()
    }

    companion object {
        private val dateTime = LocalDateTime.now(ZoneOffset.UTC)
    }
}