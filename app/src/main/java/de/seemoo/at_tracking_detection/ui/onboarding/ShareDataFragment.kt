package de.seemoo.at_tracking_detection.ui.onboarding

import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import com.github.appintro.SlidePolicy
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.databinding.FragmentShareDataBinding
import de.seemoo.at_tracking_detection.worker.BackgroundWorkScheduler
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class ShareDataFragment : Fragment(), SlidePolicy {

    @Inject
    lateinit var sharedPreferences: SharedPreferences

    @Inject
    lateinit var backgroundWorkScheduler: BackgroundWorkScheduler

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
        val yesButton = view.findViewById<Button>(R.id.onboarding_share_data_yes)
        val noButton = view.findViewById<Button>(R.id.onboarding_share_data_no)
        yesButton.setOnClickListener {
            buttonPressed = true
            it.setBackgroundColor(Color.GREEN)
            noButton.setBackgroundColor(Color.TRANSPARENT)
            sharedPreferences.edit().putBoolean("share_data", true).apply()
        }
        noButton.setOnClickListener {
            buttonPressed = true
            yesButton.setBackgroundColor(Color.TRANSPARENT)
            it.setBackgroundColor(Color.RED)
            sharedPreferences.edit().putBoolean("share_data", false).apply()
        }
    }

    companion object {
        fun newInstance() = ShareDataFragment()
    }

    override val isPolicyRespected: Boolean
        get() = buttonPressed

    override fun onUserIllegallyRequestedNextPage() {
        Timber.d("User illegally requested the next page!")
        Snackbar.make(requireView(), R.string.onboarding_share_data_dialog, Snackbar.LENGTH_SHORT)
            .show()
    }
}