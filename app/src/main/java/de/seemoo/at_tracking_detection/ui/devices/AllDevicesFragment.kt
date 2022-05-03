package de.seemoo.at_tracking_detection.ui.devices

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.viewModels
import androidx.navigation.NavDirections
import androidx.navigation.fragment.findNavController
import com.google.android.material.card.MaterialCardView
import dagger.hilt.android.AndroidEntryPoint
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.databinding.FragmentAllDevicesBinding
import de.seemoo.at_tracking_detection.ui.dashboard.RiskDetailFragmentDirections


/**
 * Fragment that shows cards for every relevant filter of the devices
 */
@AndroidEntryPoint
class AllDevicesFragment : Fragment() {

    private var _binding: FragmentAllDevicesBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AllDevicesViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = DataBindingUtil.inflate(
            inflater,
            R.layout.fragment_all_devices,
            container,
            false
        )

        binding.viewModel = viewModel
        binding.lifecycleOwner = viewLifecycleOwner

        return binding.root
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (requireActivity() as AppCompatActivity).supportActionBar?.title =
            getString(R.string.title_devices)

        view.findViewById<MaterialCardView>(R.id.tracker_devices_card).setOnClickListener {
            val directions: NavDirections =
                AllDevicesFragmentDirections.actionNavigationAllDevicesFragmentToDevicesFound(true)
            findNavController().navigate(directions)
        }

        view.findViewById<MaterialCardView>(R.id.all_devices_card).setOnClickListener {
            val directions: NavDirections =
                AllDevicesFragmentDirections.actionNavigationAllDevicesFragmentToDevicesFound(true, true)
            findNavController().navigate(directions)
        }

        view.findViewById<MaterialCardView>(R.id.ignored_devices_card).setOnClickListener {
            val directions: NavDirections =
                AllDevicesFragmentDirections.actionNavigationAllDevicesFragmentToNavigationIgnoredDevicesFragment()
            findNavController().navigate(directions)
        }
    }
}