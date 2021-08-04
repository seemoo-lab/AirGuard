package de.seemoo.at_tracking_detection.ui.onboarding

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import dagger.hilt.android.AndroidEntryPoint
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.databinding.FragmentIgnoreBatteryOptimizationBinding
import javax.inject.Inject

@AndroidEntryPoint
class IgnoreBatteryOptimizationFragment : Fragment() {

    @Inject
    lateinit var applicationContext: Context

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding: FragmentIgnoreBatteryOptimizationBinding = DataBindingUtil.inflate(
            inflater,
            R.layout.fragment_ignore_battery_optimization,
            container,
            false
        )
        binding.lifecycleOwner = viewLifecycleOwner
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val ignoreBatteryOptimizationButton =
            view.findViewById<Button>(R.id.onboarding_ignore_battery_optimization_button)
        ignoreBatteryOptimizationButton.setOnClickListener {
            requestIgnoreBatteryOptimization()
        }
    }

    // This App fulfills the requirements to request ignore battery optimization. Further
    // information can be found here:
    // https://developer.android.com/training/monitoring-device-state/doze-standby.html#exemption-cases
    @SuppressLint("BatteryLife")
    private fun requestIgnoreBatteryOptimization() {
        val intent = Intent()
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        val packageName = applicationContext.packageName
        val pm: PowerManager =
            applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (pm.isIgnoringBatteryOptimizations(packageName))
            intent.action = Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
        else {
            intent.action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
            intent.data = Uri.parse("package:$packageName")
        }
        applicationContext.startActivity(intent)
    }

    companion object {
        fun newInstance() = IgnoreBatteryOptimizationFragment()
    }
}