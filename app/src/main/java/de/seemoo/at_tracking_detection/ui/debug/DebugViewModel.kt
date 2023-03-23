package de.seemoo.at_tracking_detection.ui.debug

import android.content.SharedPreferences
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import de.seemoo.at_tracking_detection.database.repository.DeviceRepository
import de.seemoo.at_tracking_detection.util.SharedPrefs
import javax.inject.Inject

@HiltViewModel
class DebugViewModel @Inject constructor(
    sharedPreferences: SharedPreferences
) : ViewModel() {

    private var sharedPreferencesListener: SharedPreferences.OnSharedPreferenceChangeListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                "isScanningInBackground" -> {
                   updateScanText()
                }
            }
        }

    var scanText = MutableLiveData<String>("Not scanning")



    init {
        sharedPreferences.registerOnSharedPreferenceChangeListener(sharedPreferencesListener)
        updateScanText()
    }

    fun updateScanText() {
        if (SharedPrefs.isScanningInBackground) {
            scanText.postValue("Scanning in background")
        }else {
            scanText.postValue("Not scanning")
        }
    }
}