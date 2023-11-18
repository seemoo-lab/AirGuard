package de.seemoo.at_tracking_detection.ui.onboarding

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.annotation.RequiresApi
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import dagger.hilt.android.AndroidEntryPoint
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.databinding.FragmentIgnoreBatteryOptimizationBinding
import de.seemoo.at_tracking_detection.util.startActivitySafe

@AndroidEntryPoint
class IgnoreBatteryOptimizationFragment : Fragment() {

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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestIgnoreBatteryOptimization()
            }
        }
    }

    // This App fulfills the requirements to request ignore battery optimization. Further
    // information can be found here:
    // https://developer.android.com/training/monitoring-device-state/doze-standby.html#exemption-cases
    @RequiresApi(Build.VERSION_CODES.M)
    @SuppressLint("BatteryLife")
    private fun requestIgnoreBatteryOptimization() {
        val intent = Intent()
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        val packageName = requireContext().packageName
        val pm: PowerManager =
            requireContext().getSystemService(Context.POWER_SERVICE) as PowerManager
        if (pm.isIgnoringBatteryOptimizations(packageName))
            intent.action = Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
        else {
            intent.action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
            intent.data = Uri.parse("package:$packageName")
        }
        requireContext().startActivitySafe(intent)
    }

    companion object {
        fun newInstance() = IgnoreBatteryOptimizationFragment()
    }
}