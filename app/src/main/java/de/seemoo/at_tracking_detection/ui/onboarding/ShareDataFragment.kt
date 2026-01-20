package de.seemoo.at_tracking_detection.ui.onboarding

import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import com.github.appintro.SlidePolicy
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.databinding.FragmentShareDataBinding
import de.seemoo.at_tracking_detection.statistics.api.Api
import de.seemoo.at_tracking_detection.worker.BackgroundWorkScheduler
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class ShareDataFragment : Fragment(), SlidePolicy {

    @Inject
    lateinit var sharedPreferences: SharedPreferences

    @Inject
    lateinit var backgroundWorkScheduler: BackgroundWorkScheduler

    @Inject
    lateinit var api: Api

    private var buttonPressed: Boolean = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding: FragmentShareDataBinding = DataBindingUtil.inflate(
            inflater,
            R.layout.fragment_share_data,
            container,
            false
        )
        binding.lifecycleOwner = viewLifecycleOwner
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val yesButton = view.findViewById<MaterialButton>(R.id.onboarding_share_data_yes)
        val noButton = view.findViewById<MaterialButton>(R.id.onboarding_share_data_no)

        // Using defined colors from colors.xml
        val colorSafe = ContextCompat.getColor(requireContext(), R.color.md_theme_primary)
        val colorSafeText = ContextCompat.getColor(requireContext(), R.color.md_theme_onPrimary)
        val colorRisk = ContextCompat.getColor(requireContext(), R.color.md_theme_error)
        val colorRiskText = ContextCompat.getColor(requireContext(), R.color.md_theme_onError)
        val colorTransparent = Color.TRANSPARENT
        val colorDefaultText = ContextCompat.getColor(requireContext(), R.color.md_theme_onSurface)

        // Set default colors
        yesButton.setTextColor(colorDefaultText)
        noButton.setTextColor(colorDefaultText)

        yesButton.setOnClickListener {
            buttonPressed = true
            yesButton.setBackgroundColor(colorSafe)
            yesButton.setTextColor(colorSafeText)
            noButton.setBackgroundColor(colorTransparent)
            noButton.setTextColor(colorDefaultText)

            sharedPreferences.edit { putBoolean("share_data", true) }
        }
        noButton.setOnClickListener {
            buttonPressed = true
            yesButton.setBackgroundColor(colorTransparent)
            yesButton.setTextColor(colorDefaultText)
            noButton.setBackgroundColor(colorRisk)
            noButton.setTextColor(colorRiskText)

            sharedPreferences.edit { putBoolean("share_data", false) }
        }
    }

    companion object {
        fun newInstance() = ShareDataFragment()
    }

    override val isPolicyRespected: Boolean
        get() = buttonPressed

    override fun onUserIllegallyRequestedNextPage() {
        if (!buttonPressed) {
            Timber.d("User illegally requested the next page!")
            Snackbar.make(requireView(), R.string.onboarding_share_data_dialog, Snackbar.LENGTH_SHORT)
                .show()
        }
    }
}