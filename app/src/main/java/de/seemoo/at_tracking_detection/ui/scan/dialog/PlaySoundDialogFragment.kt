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
import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.databinding.DialogPlaySoundBinding
import de.seemoo.at_tracking_detection.util.Util
import de.seemoo.at_tracking_detection.util.ble.BluetoothConstants
import de.seemoo.at_tracking_detection.util.ble.BluetoothLeService
import timber.log.Timber

class PlaySoundDialogFragment constructor(scanResult: ScanResult) : BottomSheetDialogFragment() {

    private val dialogViewModel: DialogViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = DialogPlaySoundBinding.inflate(LayoutInflater.from(context))
        binding.vm = dialogViewModel
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        val gattServiceIntent = Intent(context, BluetoothLeService::class.java)
        val activity = ATTrackingDetectionApplication.getCurrentActivity()
        LocalBroadcastManager.getInstance(activity)
            .registerReceiver(gattUpdateReceiver, Util.gattIntentFilter)
        activity.bindService(gattServiceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
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
                    dialogViewModel.playing.postValue(true)
                    dialogViewModel.connecting.postValue(false)
                }
                else -> {
                    dialogViewModel.playing.postValue(false)
                    dismissWithDelay()
                    when (intent.action) {
                        BluetoothConstants.ACTION_GATT_DISCONNECTED -> dialogViewModel.success.postValue(
                            true
                        )
                        BluetoothConstants.ACTION_EVENT_FAILED -> {
                            dialogViewModel.error.postValue(true)
                        }
                        BluetoothConstants.ACTION_EVENT_COMPLETED -> dialogViewModel.success.postValue(
                            true
                        )
                    }
                }
            }
        }
    }

    private val serviceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val bluetoothService = (service as BluetoothLeService.LocalBinder).getService()
            if (!bluetoothService.init()) {
                dialogViewModel.error.postValue(true)
            } else {
                dialogViewModel.connecting.postValue(true)
                bluetoothService.connect(scanResult.device.address)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            dialogViewModel.playing.postValue(false)
            dialogViewModel.error.postValue(false)
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
        private const val DIALOG_CLOSE_DELAY = 3000L
    }
}