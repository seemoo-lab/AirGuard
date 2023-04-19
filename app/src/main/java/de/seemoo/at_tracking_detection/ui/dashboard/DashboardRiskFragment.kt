package de.seemoo.at_tracking_detection.ui.dashboard

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.NavDirections
import androidx.navigation.fragment.findNavController
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.databinding.FragmentDashboardRiskBinding
import de.seemoo.at_tracking_detection.util.SharedPrefs
import de.seemoo.at_tracking_detection.util.Util



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

        /*
        binding.scanFab.setOnClickListener {
            val bluetoothManager = ATTrackingDetectionApplication.getAppContext()
                .getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val hasScanPermission =
                Build.VERSION.SDK_INT < Build.VERSION_CODES.S || Util.checkAndRequestPermission(
                    Manifest.permission.BLUETOOTH_SCAN
                )
            val isBluetoothEnabled = bluetoothManager.adapter?.state == BluetoothAdapter.STATE_ON

            if (!isBluetoothEnabled || !hasScanPermission) {
                MaterialAlertDialogBuilder(view.context)
                    .setIcon(R.drawable.ic_warning)
                    .setTitle(getString(R.string.scan_enable_bluetooth_title))
                    .setMessage(getString(R.string.scan_enable_bluetooth_message))
                    .setPositiveButton(getString(R.string.ok_button), null)
                    .create()
                    .show()
            } else {
                showManualScan()
            }
        }
        */

        val riskCard: MaterialCardView = view.findViewById(R.id.risk_card)
        riskCard.setOnClickListener {
            val directions: NavDirections =
                DashboardRiskFragmentDirections.actionNavigationDashboardToRiskDetailFragment()
            findNavController().navigate(directions)
        }

        view.findViewById<MaterialButton>(R.id.participate_button).setOnClickListener {
            participateInSurvey()
        }

        view.findViewById<ImageButton>(R.id.hide_survey_card_button).setOnClickListener {
            dismissSurveyCard()
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.updateRiskLevel()
    }

    private fun participateInSurvey() {
        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse(ATTrackingDetectionApplication.SURVEY_URL)
        )
        startActivity(intent)
    }

    private fun dismissSurveyCard() {


        val dialogBuilder = context?.let { MaterialAlertDialogBuilder(it) }
        dialogBuilder?.setTitle(R.string.survey_alert_title)
        dialogBuilder?.setMessage(R.string.survey_alert_message)
        dialogBuilder?.setPositiveButton(R.string.remind_later) { dialog, which ->
            SharedPrefs.dismissSurveyInformation = true
            ATTrackingDetectionApplication.getCurrentApp()?.notificationService?.scheduleSurveyNotification(true)
        }

        dialogBuilder?.setNegativeButton(R.string.show_not_again) { dialog, which ->
            SharedPrefs.dismissSurveyInformation = true
            viewModel.dismissSurveyInformation.postValue(true)
        }

        dialogBuilder?.setOnCancelListener {

        }

        val alert = dialogBuilder?.create()

        alert?.show()
    }
}