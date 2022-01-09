package de.seemoo.at_tracking_detection.ui.onboarding

import android.Manifest
import android.app.AlertDialog
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

@AndroidEntryPoint
class LocationFragment : Fragment(R.layout.fragment_location_permission), SlidePolicy {

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
                showPermissionError()
            }
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val btn = view.findViewById<Button>(R.id.location_permission_button)
        btn.setOnClickListener {
            this.requestLocationPermission()
        }
    }

    companion object {
        fun newInstance() = LocationFragment()
    }

    override val isPolicyRespected: Boolean
        get() = canContinue

    override fun onUserIllegallyRequestedNextPage() {
        showAlertDialogForLocationPermission()
    }

    fun showAlertDialogForLocationPermission() {
        val builder: AlertDialog.Builder? = context.let { AlertDialog.Builder(it) }

        builder?.setMessage(R.string.location_permission_message)
        builder?.setIcon(R.drawable.ic_baseline_location_on_24)
        builder?.setTitle(R.string.location_permission_title)
        builder?.setPositiveButton(R.string.ok_button) { _: DialogInterface, _: Int ->
            this.requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        builder?.setNegativeButton("Cancel") { _: DialogInterface, _:Int ->

        }

        val dialog = builder?.create()
        dialog?.show()
    }

    private fun requestLocationPermission() {
        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED -> {
                // You can use the API that requires the permission.
            }
            shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) -> {
                showAlertDialogForLocationPermission()
            }
            else -> {
                showAlertDialogForLocationPermission()
            }
        }
    }

    private fun showPermissionError() {
        AlertDialog.Builder(context).setTitle(R.string.permission_required)
            .setIcon(R.drawable.ic_baseline_error_outline_24)
            .setMessage(R.string.permission_required_message)
            .setPositiveButton(R.string.ok_button) { _: DialogInterface, _: Int ->

            }.create()
    }
}