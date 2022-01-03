package de.seemoo.at_tracking_detection.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.NavDirections
import androidx.navigation.Navigation.findNavController
import androidx.navigation.fragment.findNavController
import com.google.android.material.card.MaterialCardView
import dagger.hilt.android.AndroidEntryPoint
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.databinding.FragmentDashboardRiskBinding


/**
 * A simple [Fragment] subclass.
 * Use the [DashboardRiskFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
@AndroidEntryPoint
class DashboardRiskFragment : Fragment() {

    private val viewModel: RiskCardViewModel by viewModels()

    private var _binding: FragmentDashboardRiskBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = DataBindingUtil.inflate(
            inflater,
            R.layout.fragment_dashboard_risk,
            container,
            false
        )
        binding.lifecycleOwner = viewLifecycleOwner
        binding.vm = viewModel
        return binding.root
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.scanFab.setOnClickListener {
            val directions: NavDirections =
                DashboardRiskFragmentDirections.dashboardToScanFragment()
            findNavController(it).navigate(directions)
        }

        val riskCard: MaterialCardView = view.findViewById(R.id.risk_card)
        riskCard.setOnClickListener {
            val directions: NavDirections =
                DashboardRiskFragmentDirections.actionNavigationDashboardToRiskDetailFragment()
            findNavController().navigate(directions)
        }
    }
}