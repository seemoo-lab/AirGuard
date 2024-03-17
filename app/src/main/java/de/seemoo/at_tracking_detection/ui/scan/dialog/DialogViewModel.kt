package de.seemoo.at_tracking_detection.ui.scan.dialog

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow

class DialogViewModel : ViewModel() {

    val playSoundState = MutableStateFlow<ConnectionState>(ConnectionState.Connecting)

    sealed class ConnectionState {
        data object Success : ConnectionState()
        data class Error(val message: String) : ConnectionState()
        data object Connecting : ConnectionState()
        data object Playing : ConnectionState()
    }

}