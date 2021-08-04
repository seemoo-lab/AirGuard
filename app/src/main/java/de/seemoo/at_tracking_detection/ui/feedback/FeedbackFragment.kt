package de.seemoo.at_tracking_detection.ui.feedback

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.navArgs
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import dagger.hilt.android.AndroidEntryPoint
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.databinding.FragmentFeedbackBinding

@AndroidEntryPoint
class FeedbackFragment : Fragment() {

    private val feedbackViewModel: FeedbackViewModel by viewModels()

    private val safeArgs: FeedbackFragmentArgs by navArgs()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding: FragmentFeedbackBinding =
            DataBindingUtil.inflate(inflater, R.layout.fragment_feedback, container, false)
        binding.lifecycleOwner = viewLifecycleOwner
        binding.vm = feedbackViewModel
        feedbackViewModel.loadFeedback(safeArgs.notificationId)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val locationChipGroup = view.findViewById<ChipGroup>(R.id.feedback_location_chip_group)

        val locations = arrayOf(
            R.string.feedback_location_backpack, R.string.feedback_location_clothes,
            R.string.feedback_location_car, R.string.feedback_location_bike
        )
        for (location in locations) {
            val chip =
                layoutInflater.inflate(
                    R.layout.include_choice_chip,
                    locationChipGroup,
                    false
                ) as Chip
            chip.setText(location)
            feedbackViewModel.location.observe(viewLifecycleOwner) {
                if (it == getString(location)) {
                    chip.isChecked = true
                }
            }
            chip.setOnClickListener {
                feedbackViewModel.location.postValue(getString(location))
            }
            locationChipGroup.addView(chip)
        }
    }

    override fun onPause() {
        feedbackViewModel.submitFeedback(safeArgs.notificationId)
        super.onPause()
    }

}