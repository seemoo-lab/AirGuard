package de.seemoo.at_tracking_detection.ui.scan

import androidx.fragment.app.viewModels
import android.os.Bundle
import android.widget.Button
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import de.seemoo.at_tracking_detection.R

class SuperScanFragment : Fragment() {

    companion object {
        fun newInstance() = SuperScanFragment()
    }

    private val viewModel: SuperScanViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // TODO: Use the ViewModel
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_super_scan, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val scanButton = view.findViewById<Button>(R.id.start_scan_button)

        scanButton.setOnClickListener {
            // TODO: Implement the scan / analysis functionality
        }
    }
}