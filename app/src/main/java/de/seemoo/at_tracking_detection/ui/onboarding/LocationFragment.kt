package de.seemoo.at_tracking_detection.ui.onboarding

import android.Manifest
import android.content.DialogInterface
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.github.appintro.SlidePolicy
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import de.seemoo.at_tracking_detection.R

@AndroidEntryPoint
class LocationFragment : Fragment(R.layout.fragment_location_permission), SlidePolicy {

    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                // Hide button immediately if granted
                view?.let { updateButtonState(it) }
            } else {
                showPermissionError()
            }
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val btn = view.findViewById<MaterialButton>(R.id.location_permission_button)
        btn.setOnClickListener {
            this.requestLocationPermission()
        }

        // Initial check
        updateButtonState(view)
    }

    override fun onResume() {
        super.onResume()
        // Check permission again in case user granted it in system settings and returned
        view?.let { updateButtonState(it) }
    }

    private fun updateButtonState(view: View) {
        val btn = view.findViewById<MaterialButton>(R.id.location_permission_button)
        val isGranted = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (isGranted) {
            btn.visibility = View.GONE
        } else {
            btn.visibility = View.VISIBLE
        }
    }

    companion object {
        fun newInstance() = LocationFragment()
    }

    override val isPolicyRespected: Boolean
        get() = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    override fun onUserIllegallyRequestedNextPage() {
        requestLocationPermission()
    }

    private fun requestLocationPermission() {
        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED -> {
                // Already granted, ensure button is hidden
                view?.let { updateButtonState(it) }
            }
            shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) -> {
                showAlertDialogForLocationPermission()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
    }

    private fun showAlertDialogForLocationPermission() {
        MaterialAlertDialogBuilder(requireContext())
            .setMessage(R.string.location_permission_message)
            .setIcon(R.drawable.ic_baseline_location_on_24)
            .setTitle(R.string.location_permission_title)
            .setPositiveButton(R.string.ok_button) { _: DialogInterface, _: Int ->
                this.requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            .setNegativeButton(R.string.cancel_button) { _: DialogInterface, _: Int ->
            }
            .show()
    }

    private fun showPermissionError() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.permission_required)
            .setIcon(R.drawable.ic_baseline_error_outline_24)
            .setMessage(R.string.permission_required_message)
            .setPositiveButton(R.string.ok_button) { _: DialogInterface, _: Int -> }
            .show()
    }
}