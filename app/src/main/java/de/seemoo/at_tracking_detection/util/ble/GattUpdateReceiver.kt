package de.seemoo.at_tracking_detection.util.ble

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import de.seemoo.at_tracking_detection.ui.tracking.TrackingViewModel
import javax.inject.Inject

@AndroidEntryPoint
class GattUpdateReceiver : BroadcastReceiver() {

    @Inject
    lateinit var trackingViewModel: TrackingViewModel

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            BluetoothLeService.ACTION_GATT_CONNECTED -> {
                trackingViewModel.soundPlaying.postValue(
                    true
                )
                trackingViewModel.connecting.postValue(false)
            }
            BluetoothLeService.ACTION_GATT_DISCONNECTED -> {
                trackingViewModel.soundPlaying.postValue(false)
                trackingViewModel.connecting.postValue(false)
            }
            BluetoothLeService.ACTION_EVENT_FAILED -> {
                trackingViewModel.error.postValue(true)
                trackingViewModel.connecting.postValue(false)
                trackingViewModel.soundPlaying.postValue(false)
            }
            BluetoothLeService.ACTION_EVENT_COMPLETED -> trackingViewModel.soundPlaying.postValue(
                false
            )
        }
    }
}