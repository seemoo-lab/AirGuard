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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.database.models.device.BaseDevice
import de.seemoo.at_tracking_detection.databinding.DialogPlaySoundBinding
import de.seemoo.at_tracking_detection.util.ble.BluetoothEvent
import de.seemoo.at_tracking_detection.util.ble.BluetoothEventManager
import de.seemoo.at_tracking_detection.util.ble.BluetoothLeService
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class PlaySoundDialogFragment(scanResult: ScanResult) : BottomSheetDialogFragment() {

    private val viewModel: DialogViewModel by viewModels()

    private var _binding: DialogPlaySoundBinding? = null
    private val binding get() = _binding!!

    @Inject
    lateinit var bluetoothEventManager: BluetoothEventManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogPlaySoundBinding.inflate(LayoutInflater.from(context))
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        val gattServiceIntent = Intent(context, BluetoothLeService::class.java)
        val activity = ATTrackingDetectionApplication.getCurrentActivity() ?: return
        activity.bindService(gattServiceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Observe Bluetooth events
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    bluetoothEventManager.events.collectLatest { event ->
                        when (event) {
                            BluetoothEvent.Connecting -> {
                                viewModel.playSoundState.value = DialogViewModel.ConnectionState.Connecting
                            }
                            BluetoothEvent.EventRunning -> {
                                viewModel.playSoundState.value = DialogViewModel.ConnectionState.Playing
                            }
                            BluetoothEvent.Disconnected -> {
                                viewModel.playSoundState.value = DialogViewModel.ConnectionState.Success
                                dismissWithDelay()
                            }
                            BluetoothEvent.EventFailed -> {
                                viewModel.playSoundState.value = DialogViewModel.ConnectionState.Error(
                                    ATTrackingDetectionApplication.getAppContext()
                                        .getString(R.string.play_sound_error_fail)
                                )
                                dismissWithDelay()
                            }
                            BluetoothEvent.EventCompleted -> {
                                viewModel.playSoundState.value = DialogViewModel.ConnectionState.Success
                                dismissWithDelay()
                            }
                        }
                    }
                }

                launch {
                    viewModel.playSoundState.collect {
                        when (it) {
                            is DialogViewModel.ConnectionState.Playing -> {
                                binding.spinnerConnecting.visibility = View.INVISIBLE
                                binding.spinnerPlaying.visibility = View.VISIBLE
                            }

                            is DialogViewModel.ConnectionState.Connecting -> {
                                binding.spinnerConnecting.visibility = View.VISIBLE
                            }

                            else -> {
                                binding.spinnerConnecting.visibility = View.INVISIBLE
                                binding.spinnerPlaying.visibility = View.INVISIBLE
                                when (it) {
                                    is DialogViewModel.ConnectionState.Error -> {
                                        binding.imageError.visibility = View.VISIBLE
                                        binding.errorText.visibility = View.VISIBLE
                                        binding.errorText.text = it.message
                                    }

                                    is DialogViewModel.ConnectionState.Success -> {
                                        binding.imageSuccess.visibility = View.VISIBLE
                                    }

                                    else -> {
                                        Timber.d("Reached unknown state $it!")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun dismissWithDelay() {
        if (isAdded && !isDetached && !isRemoving) {
            Handler(Looper.getMainLooper()).postDelayed({
                dismiss()
            }, DIALOG_CLOSE_DELAY)
        }
    }

    private val serviceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val bluetoothService = (service as BluetoothLeService.LocalBinder).getService()
            if (!bluetoothService.init()) {
                viewModel.playSoundState.value =
                    DialogViewModel.ConnectionState.Error(getString(R.string.play_sound_error_init))
            } else {
                viewModel.playSoundState.value =
                    DialogViewModel.ConnectionState.Connecting
                if (!bluetoothService.connect(BaseDevice(scanResult))) {
                    DialogViewModel.ConnectionState.Error(getString(R.string.play_sound_error_connect))
                }
            }
        }

        override fun onServiceDisconnected(p0: ComponentName?) {
        }
    }

    override fun onDestroy() {
        try {
            context?.unbindService(serviceConnection)
        } catch (e: IllegalArgumentException) {
            Timber.e("Tried to unbind an unbound service!")
        }
        super.onDestroy()
    }

    companion object {
        private const val DIALOG_CLOSE_DELAY = 3000L
    }
}