package de.seemoo.at_tracking_detection.ui.settings

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.statistics.api.Api
import de.seemoo.at_tracking_detection.util.SharedPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

@AndroidEntryPoint
class DataDeletionDialog : BottomSheetDialogFragment() {
    @Inject
    lateinit var api: Api

    @Inject
    lateinit var sharedPreferences: SharedPreferences

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_data_deletion, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(bottom = bars.bottom)
            WindowInsetsCompat.CONSUMED
        }

        val deletionButton = view.findViewById<Button>(R.id.delete_button)
        deletionButton.setOnClickListener {
            val token = SharedPrefs.token

            CoroutineScope(Dispatchers.Main).launch {
                val rootView = requireView() // Get the root view of the fragment

                if (!api.ping().isSuccessful) {
                    Timber.e("Server not available!")
                    val text = R.string.delete_data_server_error
                    Snackbar.make(rootView, text, Snackbar.LENGTH_LONG).show()
                } else if (token == null) {
                    Timber.e("Token is null! Could not delete data!")
                    val text = R.string.delete_data_no_data
                    Snackbar.make(rootView, text, Snackbar.LENGTH_LONG).show()
                } else {
                    val response = api.deleteStudyData(token)

                    if (response.isSuccessful) {
                        Timber.e("Data Deletion Successful!")
                        SharedPrefs.token = null
                        sharedPreferences.edit { putBoolean("share_data", false) }
                        val text = R.string.delete_data_success
                        Snackbar.make(rootView, text, Snackbar.LENGTH_LONG).show()
                        dismiss()
                    } else {
                        Timber.e("Data Deletion Failed! Server sent error!")
                        val text = R.string.delete_data_error
                        Snackbar.make(rootView, text, Snackbar.LENGTH_LONG).show()
                    }
                }
            }
        }
    }
}