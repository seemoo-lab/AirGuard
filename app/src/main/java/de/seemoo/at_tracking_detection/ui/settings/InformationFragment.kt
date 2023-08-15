package de.seemoo.at_tracking_detection.ui.settings

import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import de.seemoo.at_tracking_detection.R

class InformationFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val rootView = inflater.inflate(R.layout.fragment_information, container, false)

        val versionNumberTextView = rootView.findViewById<TextView>(R.id.versionNumber)

        try {
            val versionNumber = requireActivity().packageManager.getPackageInfo(
                requireActivity().packageName,
                0
            ).versionName
            val versionText = getString(R.string.version_number, versionNumber)
            versionNumberTextView.text = versionText
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }

        return rootView
    }

}