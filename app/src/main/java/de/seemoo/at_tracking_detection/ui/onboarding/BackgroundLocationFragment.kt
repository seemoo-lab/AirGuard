package de.seemoo.at_tracking_detection.ui.onboarding

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.github.appintro.SlidePolicy
import dagger.hilt.android.AndroidEntryPoint
import de.seemoo.at_tracking_detection.R
import javax.inject.Inject

@AndroidEntryPoint
class BackgroundLocationFragment : Fragment(R.layout.fragment_background_location_permission_onboarding), SlidePolicy {
    @Inject
    lateinit var applicationContext: Context

    var canContinue = true

    // Register the permissions callback, which handles the user's response to the
    // system permissions dialog. Save the return value, an instance of
    // ActivityResultLauncher. You can use either a val, as shown in this snippet,
    // or a lateinit var in your onAttach() or onCreate() method.
    val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                // Permission is granted. Continue the action or workflow in your
                // app.

            } else {
                // Explain to the user that the feature is unavailable because the
                // features requires a permission that the user has denied. At the
                // same time, respect the user's decision. Don't link to system
                // settings in an effort to convince the user to change their
                // decision.
            }
        }

    companion object {
        fun newInstance() = BackgroundLocationFragment()
    }

    override val isPolicyRespected: Boolean
        get() = canContinue

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val btn = view.findViewById<Button>(R.id.location_permission_button)
        btn.setOnClickListener {
            requestLocationPermission()
        }
    }

    override fun onUserIllegallyRequestedNextPage() {
        showAlertDialogForLocationPermission()
    }

    private fun showAlertDialogForLocationPermission() {
        val builder: AlertDialog.Builder? = context.let { AlertDialog.Builder(it) }

        builder?.setMessage(R.string.onboarding_4_description)
        builder?.setTitle(R.string.onboarding_4_title)
        builder?.setIcon(R.drawable.ic_baseline_location_on_24)

        builder?.setPositiveButton(R.string.ok_button) { _: DialogInterface, _: Int ->
            this.requestPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }

        builder?.setNegativeButton(getString(R.string.cancel)) { _: DialogInterface, _:Int ->

        }

        val dialog = builder?.create()
        dialog?.show()
    }

    fun requestLocationPermission() {
        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_BACKGROUND_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED -> {
                // You can use the API that requires the permission.
            }
            shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_BACKGROUND_LOCATION) -> {
               showAlertDialogForLocationPermission()
            }
            else -> {
                showAlertDialogForLocationPermission()
            }
        }
    }

}