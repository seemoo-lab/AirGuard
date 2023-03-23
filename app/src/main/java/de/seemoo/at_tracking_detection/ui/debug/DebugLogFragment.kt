package de.seemoo.at_tracking_detection.ui.debug

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.viewModels
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.databinding.FragmentDebugLogBinding


class DebugLogFragment : Fragment() {

    private val viewModel: DebugLogViewModel by viewModels()
    private var binding: FragmentDebugLogBinding? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding =
            DataBindingUtil.inflate(inflater, R.layout.fragment_debug_log, container, false)

        binding?.lifecycleOwner = viewLifecycleOwner
        binding?.vm = viewModel
        return binding?.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<Button>(R.id.clear_button).setOnClickListener {
            //Clear all the logs
            viewModel.clearLogs()
        }
    }

}