package de.seemoo.at_tracking_detection.ui.dashboard

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.viewModels
import androidx.navigation.NavDirections
import androidx.navigation.Navigation.findNavController
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
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
    private val riskViewModel: RiskCardViewModel by viewModels()

    private lateinit var binding: FragmentDashboardRiskBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        binding = DataBindingUtil.inflate(
            inflater,
            R.layout.fragment_dashboard_risk,
            container,
            false
        )
        binding.lifecycleOwner = viewLifecycleOwner
        binding.vm = riskViewModel

        // Inflate the layout for this fragment
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val fabScan = view.findViewById<ExtendedFloatingActionButton>(R.id.dashboard_scan_fab)

        fabScan.setOnClickListener {
            val directions: NavDirections = DashboardRiskFragmentDirections.dashboardToScanFragment()
            findNavController(it).navigate(directions)
        }
    }
}