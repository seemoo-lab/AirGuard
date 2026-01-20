package de.seemoo.at_tracking_detection.ui.onboarding

import android.Manifest
import android.content.DialogInterface
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.github.appintro.SlidePolicy
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import de.seemoo.at_tracking_detection.R

@AndroidEntryPoint
class BackgroundLocationFragment : Fragment(R.layout.fragment_background_location_permission_onboarding), SlidePolicy {

    private var userDeclinedOnce = false

    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                // Hide button immediately if granted
                view?.let { updateButtonState(it) }
            } else {
                userDeclinedOnce = true
            }
        }

    companion object {
        fun newInstance() = BackgroundLocationFragment()
    }

    override val isPolicyRespected: Boolean
        get() {
            val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
            return granted || userDeclinedOnce
        }

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val btn = view.findViewById<MaterialButton>(R.id.location_permission_button)
        btn.setOnClickListener {
            requestLocationPermission()
        }

        // Initial check
        updateButtonState(view)
    }

    override fun onResume() {
        super.onResume()
        // Check permission again in case user granted it in system settings and returned
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            view?.let { updateButtonState(it) }
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun updateButtonState(view: View) {
        val btn = view.findViewById<MaterialButton>(R.id.location_permission_button)
        val isGranted = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_BACKGROUND_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (isGranted) {
            btn.visibility = View.GONE
        } else {
            btn.visibility = View.VISIBLE
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onUserIllegallyRequestedNextPage() {
        requestLocationPermission()
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun showAlertDialogForLocationPermission() {
        MaterialAlertDialogBuilder(requireContext())
            .setMessage(R.string.onboarding_4_description)
            .setTitle(R.string.onboarding_4_title)
            .setIcon(R.drawable.ic_baseline_location_on_24)
            .setPositiveButton(R.string.ok_button) { _: DialogInterface, _: Int ->
                this.requestPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
            .setNegativeButton(R.string.cancel_button) { _: DialogInterface, _: Int ->
                userDeclinedOnce = true
            }
            .show()
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    fun requestLocationPermission() {
        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_BACKGROUND_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED -> {
                // Already granted
                view?.let { updateButtonState(it) }
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