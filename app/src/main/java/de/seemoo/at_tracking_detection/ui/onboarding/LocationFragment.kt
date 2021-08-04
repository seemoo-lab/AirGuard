package de.seemoo.at_tracking_detection.ui.onboarding

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import androidx.fragment.app.Fragment
import com.github.appintro.SlidePolicy
import dagger.hilt.android.AndroidEntryPoint
import de.seemoo.at_tracking_detection.R
import javax.inject.Inject

@AndroidEntryPoint
class LocationFragment : Fragment(R.layout.fragment_location_permission), SlidePolicy {
    @Inject
    lateinit var applicationContext: Context

    var canContinue = false


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
        builder?.setTitle(R.string.location_permission_title)
        builder?.setPositiveButton(R.string.ok_button) { _: DialogInterface, _: Int ->
            canContinue = true
        }

        val dialog = builder?.create()
        dialog?.show()
    }
}