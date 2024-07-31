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
import de.seemoo.at_tracking_detection.database.models.device.DeviceType
import de.seemoo.at_tracking_detection.databinding.FragmentAllDevicesBinding


/**
 * Fragment that shows cards for every relevant filter of the devices
 */
@AndroidEntryPoint
class AllDevicesFragment : Fragment() {

    private var _binding: FragmentAllDevicesBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AllDevicesViewModel by viewModels()

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
                AllDevicesFragmentDirections.actionNavigationAllDevicesFragmentToDevicesFound2(showDevicesFound = true)
            findNavController().navigate(directions)
        }

        view.findViewById<MaterialCardView>(R.id.all_devices_card).setOnClickListener {
            val directions: NavDirections =
                AllDevicesFragmentDirections.actionNavigationAllDevicesFragmentToDevicesFound2(showDevicesFound = true, showAllDevices = true)
            findNavController().navigate(directions)
        }

        view.findViewById<MaterialCardView>(R.id.ignored_devices_card).setOnClickListener {
            val directions: NavDirections =
                AllDevicesFragmentDirections.actionNavigationAllDevicesFragmentToNavigationIgnoredDevicesFragment()
            findNavController().navigate(directions)
        }

        view.findViewById<MaterialCardView>(R.id.airtags_found_card).setOnClickListener {
            val directions = AllDevicesFragmentDirections.actionNavigationAllDevicesFragmentToDevicesFound2(showDevicesFound = true, showAllDevices = true, deviceType = DeviceType.AIRTAG)
            findNavController().navigate(directions)
        }

        view.findViewById<MaterialCardView>(R.id.findmy_found_card).setOnClickListener {
            val directions = AllDevicesFragmentDirections.actionNavigationAllDevicesFragmentToDevicesFound2(showDevicesFound = true, showAllDevices = true, deviceType = DeviceType.FIND_MY)
            findNavController().navigate(directions)
        }

        view.findViewById<MaterialCardView>(R.id.tiles_found_card).setOnClickListener {
            val directions = AllDevicesFragmentDirections.actionNavigationAllDevicesFragmentToDevicesFound2(showDevicesFound = true, showAllDevices = true, deviceType = DeviceType.TILE)
            findNavController().navigate(directions)
        }

        view.findViewById<MaterialCardView>(R.id.chipolos_found_card).setOnClickListener {
            val directions = AllDevicesFragmentDirections.actionNavigationAllDevicesFragmentToDevicesFound2(showDevicesFound = true, showAllDevices = true, deviceType = DeviceType.CHIPOLO)
            findNavController().navigate(directions)
        }

        view.findViewById<MaterialCardView>(R.id.pebblebees_found_card).setOnClickListener {
            val directions = AllDevicesFragmentDirections.actionNavigationAllDevicesFragmentToDevicesFound2(showDevicesFound = true, showAllDevices = true, deviceType = DeviceType.PEBBLEBEE)
            findNavController().navigate(directions)
        }

        view.findViewById<MaterialCardView>(R.id.smarttags_found_card).setOnClickListener {
            val directions = AllDevicesFragmentDirections.actionNavigationAllDevicesFragmentToDevicesFound2(showDevicesFound = true, showAllDevices = true, deviceType = DeviceType.GALAXY_SMART_TAG, deviceType2 = DeviceType.GALAXY_SMART_TAG_PLUS)
            findNavController().navigate(directions)
        }

        view.findViewById<MaterialCardView>(R.id.google_found_card).setOnClickListener {
            val directions = AllDevicesFragmentDirections.actionNavigationAllDevicesFragmentToDevicesFound2(showDevicesFound = true, showAllDevices = true, deviceType = DeviceType.GOOGLE_FIND_MY_NETWORK)
            findNavController().navigate(directions)
        }
    }
}