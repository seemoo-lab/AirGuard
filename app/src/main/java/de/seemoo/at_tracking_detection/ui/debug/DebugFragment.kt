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
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavDirections
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.database.models.device.BaseDevice
import de.seemoo.at_tracking_detection.database.models.device.DeviceManager
import de.seemoo.at_tracking_detection.database.models.device.DeviceType
import de.seemoo.at_tracking_detection.databinding.FragmentDebugBinding
import de.seemoo.at_tracking_detection.notifications.NotificationService
import de.seemoo.at_tracking_detection.statistics.api.Api
import de.seemoo.at_tracking_detection.util.ble.BLEScanCallback
import de.seemoo.at_tracking_detection.worker.BackgroundWorkScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.LocalDateTime
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
    private val scanResultMap: HashMap<String, ScanResult> = HashMap()
    private val displayList: ArrayList<String> = ArrayList()
    private lateinit var bluetoothList: ListView
    private var scanning = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val bluetoothManager =
            ATTrackingDetectionApplication.getAppContext()
                .getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothLeScanner = bluetoothManager.adapter?.bluetoothLeScanner

        val binding: FragmentDebugBinding = DataBindingUtil.inflate(inflater, R.layout.fragment_debug, container, false)
        binding.lifecycleOwner = viewLifecycleOwner
        binding.vm = debugViewModel

        bluetoothList = binding.root.findViewById(R.id.bluetoothList)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<Button>(R.id.start_ble_scanning)?.setOnClickListener {
            devicesList.clear()
            displayList.clear()
            scanLeDevice()
        }
        view.findViewById<Button>(R.id.button2)?.setOnClickListener {
            val testBaseDevice = BaseDevice(
                address = "00:11:22:33:44:55",
                ignore = false,
                connectable = true,
                payloadData = null,
                firstDiscovery = LocalDateTime.now(),
                lastSeen = LocalDateTime.now(),
                deviceType = DeviceType.SAMSUNG_TRACKER
            ).apply {
                notificationSent = true
                lastNotificationSent = LocalDateTime.now()
                subDeviceType = "SMART_TAG_2"
            }


            debugViewModel.viewModelScope.launch {
                debugViewModel.addDeviceToDb(testBaseDevice)
                notificationService.sendTrackingNotification(testBaseDevice)
            }
        }
        view.findViewById<ListView>(R.id.bluetoothList)
            .setOnItemClickListener { _, _, position, _ ->
                debugViewModel.viewModelScope.launch {
                    val bluetoothDevice = devicesList[position]
                    val baseDevice = BaseDevice(scanResultMap[bluetoothDevice.address]!!)
                    notificationService.sendTrackingNotification(baseDevice)
                }
            }
        view.findViewById<Button>(R.id.donate_data)?.setOnClickListener {
            backgroundWorkScheduler.scheduleShareDataDebug()
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

        view.findViewById<Button>(R.id.start_background_scan).setOnClickListener {
            backgroundWorkScheduler.launch()
        }

        view.findViewById<Button>(R.id.button_debugScans).setOnClickListener {
            findNavController().navigate(DebugFragmentDirections.actionNavigationDebugToDebugScansFragment())
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
                BLEScanCallback.startScanning(scanner, DeviceManager.scanFilter, buildSettings(), leScanCallback)
            } else {
                scanning = false
                BLEScanCallback.stopScanning(scanner)
            }
        }
    }

    private fun buildSettings() =
        ScanSettings.Builder().setScanMode(getScanMode()).build()

    private fun getScanMode(): Int {
        return ScanSettings.SCAN_MODE_LOW_LATENCY
    }


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

            scanResultMap[result.device.address] = result
        }
    }

    @SuppressLint("MissingPermission")
    override fun onDestroyView() {
        super.onDestroyView()
        bluetoothLeScanner?.let { BLEScanCallback.stopScanning(it) }
    }

    companion object {
        private const val SCAN_PERIOD: Long = 100 * 1000 // 100s
    }
}