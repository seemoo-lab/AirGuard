package de.seemoo.at_tracking_detection.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import dagger.hilt.android.AndroidEntryPoint
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.ui.tracking.TrackingFragmentArgs
import timber.log.Timber

@AndroidEntryPoint
class TrackingNotificationActivity : AppCompatActivity() {

    private lateinit var navController: NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tracking)
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.tracking_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        val deviceAddress = intent.getStringExtra("deviceAddress")
        val notificationId = intent.getIntExtra("notificationId", -1)
        Timber.d("Tracking Activity with device $deviceAddress and notification $notificationId started!")

        if (deviceAddress == null) {
            Timber.e("Device address is needed! Going home...")
            this.onSupportNavigateUp()
        } else {
            val args = TrackingFragmentArgs(
                deviceAddress = deviceAddress,
                notificationId = notificationId
            ).toBundle()
            navController.setGraph(R.navigation.tracking_navigation, args)
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                onNavigateUp()
            }
        })
    }

    override fun onSupportNavigateUp(): Boolean {
        if (!navController.navigateUp()) {
            startActivity(Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
        }
        return true
    }
}