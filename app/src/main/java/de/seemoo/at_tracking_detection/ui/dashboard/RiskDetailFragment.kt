package de.seemoo.at_tracking_detection.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.NavDirections
import androidx.navigation.fragment.findNavController
import com.google.android.material.card.MaterialCardView
import dagger.hilt.android.AndroidEntryPoint
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.databinding.FragmentRiskDetailBinding
import de.seemoo.at_tracking_detection.util.risk.RiskLevelEvaluator
import javax.inject.Inject


@AndroidEntryPoint
class RiskDetailFragment : Fragment() {

    @Inject
    lateinit var riskLevelEvaluator: RiskLevelEvaluator

    private val viewModel: RiskDetailViewModel by viewModels()

    private var _binding: FragmentRiskDetailBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = DataBindingUtil.inflate(
            inflater,
            R.layout.fragment_risk_detail,
            container,
            false
        )
        binding.viewModel = viewModel
        binding.lifecycleOwner = viewLifecycleOwner
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        //Not sure if we can link to Apple's content.
//        val deactivateAirTagCard: MaterialCardView = view.findViewById(R.id.find_airtag_card)

//        deactivateAirTagCard.setOnClickListener {
//            val intent = Intent(
//                Intent.ACTION_VIEW,
//                Uri.parse("https://support.apple.com/en-us/HT212227")
//            )
//            startActivity(intent)
//        }

        view.findViewById<MaterialCardView>(R.id.card_trackers_found).setOnClickListener {
            val directions: NavDirections =
                RiskDetailFragmentDirections.actionRiskDetailFragmentToNavigationDevices(true)
            findNavController().navigate(directions)
        }

        view.findViewById<MaterialCardView>(R.id.card_tracked_locations).setOnClickListener {
            val directions =
                RiskDetailFragmentDirections.actionRiskDetailFragmentToDeviceMapFragment()
            findNavController().navigate(directions)
        }

        view.findViewById<MaterialCardView>(R.id.card_devices_found).setOnClickListener {
            val directions = RiskDetailFragmentDirections.actionRiskDetailFragmentToNavigationDevices(showAllDevices = true, showDevicesFound = true)
            findNavController().navigate(directions)
        }

//        view.findViewById<View>(R.id.card_beacons_found).setOnClickListener {
//            val directions =
//                RiskDetailFragmentDirections.actionRiskDetailFragmentToDeviceMapFragment()
//            findNavController().navigate(directions)
//        }
    }
}