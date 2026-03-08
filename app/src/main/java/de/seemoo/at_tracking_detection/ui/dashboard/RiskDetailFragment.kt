package de.seemoo.at_tracking_detection.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.NavDirections
import androidx.navigation.fragment.findNavController
import com.google.android.material.card.MaterialCardView
import dagger.hilt.android.AndroidEntryPoint
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.databinding.FragmentRiskDetailBinding
import de.seemoo.at_tracking_detection.util.SharedPrefs
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

        view.findViewById<MaterialCardView>(R.id.card_trackers_found).setOnClickListener {
            val directions: NavDirections =
                RiskDetailFragmentDirections.actionRiskDetailFragmentToNavigationDevices(
                    showDevicesFound = true,
                    preselectNotifiedFilter = "INCLUDING",
                    preselectIgnoredFilter = "EXCLUDING"
                )
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

        if (!SharedPrefs.advancedMode) {
            view.findViewById<MaterialCardView>(R.id.card_devices_found).visibility = View.GONE
        } else {
            view.findViewById<MaterialCardView>(R.id.card_devices_found).visibility = View.VISIBLE
        }
    }
}