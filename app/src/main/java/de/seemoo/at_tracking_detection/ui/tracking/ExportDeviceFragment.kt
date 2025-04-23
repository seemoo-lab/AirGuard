package de.seemoo.at_tracking_detection.ui.tracking

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.navArgs
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.databinding.FragmentExportDeviceBinding

class ExportDeviceFragment: Fragment() {
    private val viewModel: ExportDeviceViewModel by viewModels()
    private val safeArgs: ExportDeviceFragmentArgs by navArgs()

    private var deviceAddress: String? = null

    private lateinit var binding: FragmentExportDeviceBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DataBindingUtil.inflate(
            inflater,
            R.layout.fragment_export_device,
            container,
            false
        )
        binding.lifecycleOwner = viewLifecycleOwner
        binding.vm = viewModel

        deviceAddress = safeArgs.deviceAddress

        return binding.root
    }
}