package de.seemoo.at_tracking_detection.ui.dashboard

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.viewModels
import androidx.navigation.NavDirections
import androidx.navigation.fragment.findNavController
import com.google.android.material.card.MaterialCardView
import dagger.hilt.android.AndroidEntryPoint
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.databinding.FragmentRiskDetailBinding


/**
 * A simple [Fragment] subclass.
 * Use the [RiskDetailFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
@AndroidEntryPoint
class RiskDetailFragment : Fragment() {
    private val riskDetailViewModel: RiskDetailViewModel by viewModels()

    private lateinit var binding: FragmentRiskDetailBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        binding = DataBindingUtil.inflate(
            inflater,
            R.layout.fragment_risk_detail,
            container,
            false
        )

        binding.lifecycleOwner = viewLifecycleOwner
        binding.viewModel = riskDetailViewModel

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
            val directions: NavDirections = RiskDetailFragmentDirections.actionRiskDetailFragmentToNavigationDevices(true)
            findNavController().navigate(directions)
        }
    }
}