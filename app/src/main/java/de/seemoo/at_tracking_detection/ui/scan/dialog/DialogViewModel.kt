package de.seemoo.at_tracking_detection.ui.scan.dialog

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import timber.log.Timber

class DialogViewModel : ViewModel() {

    val error = MutableLiveData(false)

    val playing = MutableLiveData(false)

    val success = MutableLiveData(false)

    val connecting = MutableLiveData(false)

}