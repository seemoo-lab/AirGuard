package de.seemoo.at_tracking_detection.ui.feedback

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.navArgs
import com.google.android.material.card.MaterialCardView
import dagger.hilt.android.AndroidEntryPoint
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.databinding.FragmentFeedbackBinding

@AndroidEntryPoint
class FeedbackFragment : Fragment() {

    private val feedbackViewModel: FeedbackViewModel by viewModels()

    private val safeArgs: FeedbackFragmentArgs by navArgs()

    // This gives the option to highlight the selected feedback location in future updates
    private var selectedLocationCard: MaterialCardView? = null

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

    @SuppressLint("InflateParams")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val locations = listOf(
            LocationItem(getString(R.string.feedback_location_backpack), "Bag", R.drawable.ic_baseline_backpack_24),
            LocationItem(getString(R.string.feedback_location_clothes), "Clothes", R.drawable.ic_baseline_person_24),
            LocationItem(getString(R.string.feedback_location_car), "Car", R.drawable.ic_baseline_car_24),
            LocationItem(getString(R.string.feedback_location_bike), "Bike", R.drawable.ic_baseline_bike_scooter_24),
            LocationItem(getString(R.string.feedback_location_other), "Other", R.drawable.ic_baseline_more_horiz_24),
            LocationItem(getString(R.string.feedback_location_not_found), "NotFound", R.drawable.ic_baseline_cancel_24)
        )

        val locationsLinearLayout = LinearLayout(context)
        locationsLinearLayout.orientation = LinearLayout.VERTICAL
        locationsLinearLayout.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = 24
        }

        val locationLayout = view.findViewById<LinearLayout>(R.id.feedback_location_layout)

        for (locationItem in locations) {
            val locationCard = MaterialCardView(context)

            val layout = LayoutInflater.from(context).inflate(R.layout.item_feedback_selection, null, false)
            val text = layout.findViewById<TextView>(R.id.text)
            val icon = layout.findViewById<ImageView>(R.id.icon)

            icon.setImageResource(locationItem.imageResId)
            text.text = locationItem.visibleString

            locationCard.addView(layout)

            locationCard.setOnClickListener {
                // Clear previously selected location
                selectedLocationCard?.isChecked = false

                // Mark the current location as selected
                locationCard.isChecked = true
                selectedLocationCard = locationCard

                // Update ViewModel with selected location
                feedbackViewModel.location.postValue(locationItem.backendString)

                // Show a Toast message indicating success
                Toast.makeText(requireContext(), R.string.feedback_success, Toast.LENGTH_SHORT).show()
            }

            // Set layout params for location card
            locationCard.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 16
            }

            locationsLinearLayout.addView(locationCard)

            // Check if this location is already selected
            if (locationItem.backendString == feedbackViewModel.location.value) {
                // Mark the current location as selected
                locationCard.isChecked = true
                selectedLocationCard = locationCard
            }
        }

        locationLayout.addView(locationsLinearLayout)
    }

    override fun onPause() {
        feedbackViewModel.submitFeedback(safeArgs.notificationId)
        super.onPause()
    }

}