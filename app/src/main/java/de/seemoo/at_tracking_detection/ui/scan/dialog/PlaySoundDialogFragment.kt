package de.seemoo.at_tracking_detection.ui.scan.dialog

import android.bluetooth.le.ScanResult
import android.content.*
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication
import de.seemoo.at_tracking_detection.database.models.device.BaseDevice
import de.seemoo.at_tracking_detection.database.models.device.DeviceManager
import de.seemoo.at_tracking_detection.databinding.DialogPlaySoundBinding
import de.seemoo.at_tracking_detection.util.ble.BluetoothConstants
import de.seemoo.at_tracking_detection.util.ble.BluetoothLeService
import kotlinx.coroutines.flow.collect
import timber.log.Timber

class PlaySoundDialogFragment constructor(scanResult: ScanResult) : BottomSheetDialogFragment() {

    private val viewModel: DialogViewModel by viewModels()

    private var _binding: DialogPlaySoundBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogPlaySoundBinding.inflate(LayoutInflater.from(context))
        binding.vm = viewModel
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        val gattServiceIntent = Intent(context, BluetoothLeService::class.java)
        val activity = ATTrackingDetectionApplication.getCurrentActivity()
        LocalBroadcastManager.getInstance(activity)
            .registerReceiver(gattUpdateReceiver, DeviceManager.gattIntentFilter)
        activity.bindService(gattServiceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        lifecycleScope.launchWhenStarted {
            viewModel.playSoundState.collect {
                if (it != DialogViewModel.ConnectionState.Connecting) {
                    binding.spinnerPlaying.visibility = View.GONE
                }
                when (it) {
                    is DialogViewModel.ConnectionState.Success -> {
                        binding.imageSuccess.visibility = View.VISIBLE
                    }
                    is DialogViewModel.ConnectionState.Playing -> {
                        binding.spinnerPlaying.visibility = View.VISIBLE
                    }
                    is DialogViewModel.ConnectionState.Error -> {
                        binding.imageError.visibility = View.VISIBLE
                        binding.errorText.text = it.message
                    }
                    is DialogViewModel.ConnectionState.Connecting -> {
                        binding.spinnerPlaying.visibility = View.VISIBLE
                    }
                }
            }
        }
    }

    private fun dismissWithDelay() {
        Handler(Looper.getMainLooper()).postDelayed({
            dismiss()
        }, DIALOG_CLOSE_DELAY)
    }

    private val gattUpdateReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothConstants.ACTION_GATT_CONNECTED -> {
                    viewModel.playSoundState.value = DialogViewModel.ConnectionState.Playing
                }
                else -> {
                    when (intent.action) {
                        BluetoothConstants.ACTION_GATT_DISCONNECTED -> viewModel.playSoundState.value =
                            DialogViewModel.ConnectionState.Success
                        BluetoothConstants.ACTION_EVENT_FAILED -> viewModel.playSoundState.value =
                            DialogViewModel.ConnectionState.Error("Failed to play Sound!")
                        BluetoothConstants.ACTION_EVENT_COMPLETED -> viewModel.playSoundState.value =
                            DialogViewModel.ConnectionState.Success
                    }
                    dismissWithDelay()
                }
            }
        }
    }

    private val serviceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val bluetoothService = (service as BluetoothLeService.LocalBinder).getService()
            if (!bluetoothService.init()) {
                viewModel.playSoundState.value =
                    DialogViewModel.ConnectionState.Error("Could not connect to the Bluetooth Service!")
            } else {
                if (bluetoothService.connect(BaseDevice(scanResult))) {
                    viewModel.playSoundState.value =
                        DialogViewModel.ConnectionState.Playing
                } else {
                    DialogViewModel.ConnectionState.Error("Failed to connect to device!")
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
        }
    }

    override fun onDestroy() {
        try {
            context?.unregisterReceiver(gattUpdateReceiver)
            context?.unbindService(serviceConnection)
        } catch (e: IllegalArgumentException) {
            Timber.e("Tried to unbind an unbound service!")
        }
        super.onDestroy()
    }

    companion object {
        private const val DIALOG_CLOSE_DELAY = 1500L
    }
}