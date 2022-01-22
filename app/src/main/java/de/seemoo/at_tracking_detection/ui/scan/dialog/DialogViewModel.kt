package de.seemoo.at_tracking_detection.ui.scan.dialog

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class DialogViewModel : ViewModel() {

    val playSoundState = MutableStateFlow<ConnectionState>(ConnectionState.Connecting)

    sealed class ConnectionState {
        object Success : ConnectionState()
        data class Error(val message: String) : ConnectionState()
        object Connecting : ConnectionState()
        object Playing : ConnectionState()
    }

}