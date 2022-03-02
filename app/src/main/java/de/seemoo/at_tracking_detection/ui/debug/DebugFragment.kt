package de.seemoo.at_tracking_detection.ui.debug

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavDirections
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.notifications.NotificationService
import de.seemoo.at_tracking_detection.statistics.api.Api
import de.seemoo.at_tracking_detection.ui.dashboard.DashboardRiskFragmentDirections
import de.seemoo.at_tracking_detection.util.Util
import de.seemoo.at_tracking_detection.worker.BackgroundWorkScheduler
import fr.bipi.tressence.file.FileLoggerTree
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class DebugFragment : Fragment() {

    @Inject
    lateinit var notificationService: NotificationService

    @Inject
    lateinit var backgroundWorkScheduler: BackgroundWorkScheduler

    @Inject
    lateinit var sharedPreferences: SharedPreferences

    @Inject
    lateinit var api: Api

    private val debugViewModel: DebugViewModel by viewModels()

    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private val devicesList: ArrayList<BluetoothDevice> = ArrayList()
    private val displayList: ArrayList<String> = ArrayList()
    private lateinit var bluetoothList: ListView
    private var scanning = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val bluetoothManager =
            ATTrackingDetectionApplication.getAppContext()
                .getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothLeScanner = bluetoothManager.adapter?.bluetoothLeScanner
        val root = inflater.inflate(R.layout.fragment_debug, container, false)
        bluetoothList = root.findViewById(R.id.bluetoothList)
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<Button>(R.id.button)?.setOnClickListener {
            devicesList.clear()
            displayList.clear()
            scanLeDevice()
        }
        view.findViewById<Button>(R.id.button2)?.setOnClickListener {
            debugViewModel.viewModelScope.launch { notificationService.sendTrackingNotification("Some device address") }
        }
        view.findViewById<ListView>(R.id.bluetoothList)
            .setOnItemClickListener { _, _, position, _ ->
                debugViewModel.viewModelScope.launch {
                    notificationService.sendTrackingNotification(devicesList[position].address)
                }
            }
        view.findViewById<Button>(R.id.button3)?.setOnClickListener {
            backgroundWorkScheduler.scheduleShareData()
        }

        view.findViewById<Button>(R.id.button4)?.setOnClickListener {
            CoroutineScope(Dispatchers.Main).launch {
                val response = api.ping()
                var text = "Success!"
                if (!response.isSuccessful) {
                    text = "Error: ${response.errorBody().toString()}"
                }
                Snackbar.make(view, text, Snackbar.LENGTH_LONG).show()
            }
        }

        view.findViewById<Button>(R.id.button_debugLog).setOnClickListener {
            val directions: NavDirections =
                DebugFragmentDirections.actionNavigationDebugToDebugLogFragment()
            findNavController().navigate(directions)
        }
    }

    @SuppressLint("MissingPermission")
    private fun scanLeDevice() {
        bluetoothLeScanner?.let { scanner ->
            if (!scanning) { // Stops scanning after a pre-defined scan period.
                Handler(Looper.getMainLooper()).postDelayed({
                    scanning = false
                    scanner.stopScan(leScanCallback)
                }, SCAN_PERIOD)
                scanning = true
                scanner.startScan(buildFilter(), buildSettings(), leScanCallback)
            } else {
                scanning = false
                scanner.stopScan(leScanCallback)
            }
        }
    }

    private fun buildSettings() =
        ScanSettings.Builder().setScanMode(getScanMode()).build()

    private fun getScanMode(): Int {
        val useLowPower = sharedPreferences.getBoolean("use_low_power_ble", false)
        return if (useLowPower) {
            ScanSettings.SCAN_MODE_LOW_POWER
        } else {
            ScanSettings.SCAN_MODE_BALANCED
        }
    }

    private fun buildFilter() =
        mutableListOf<ScanFilter>(
            ScanFilter.Builder()
                .setManufacturerData(0x4C, byteArrayOf((0x12).toByte(), (0x19).toByte()))
                .build()
        )

    private val leScanCallback: ScanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)

            if (!devicesList.contains(result.device)) {
                Timber.d("Found device ${result.device.address}")
                devicesList.add(result.device)
                displayList.add("Address: " + result.device.address + "\t\t Name: " + result.device.name)
                Timber.d(String.format("Address: " + result.device.address + "\t\t Bond State: " + result.device.bondState))

                val arrayAdapter: ArrayAdapter<*> = ArrayAdapter(
                    requireView().context,
                    android.R.layout.simple_list_item_1,
                    displayList.toArray()
                )
                bluetoothList.adapter = arrayAdapter
            }
        }
    }

    @SuppressLint("MissingPermission")
    override fun onDestroyView() {
        super.onDestroyView()
        bluetoothLeScanner?.stopScan(leScanCallback)
    }

    companion object {
        private const val SCAN_PERIOD: Long = 100 * 1000 // 100s
    }
}