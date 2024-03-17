package de.seemoo.at_tracking_detection.ui.settings

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
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

@AndroidEntryPoint
class DataDeletionFragment : Fragment() {
    @Inject
    lateinit var api: Api

    @Inject
    lateinit var sharedPreferences: SharedPreferences

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_data_deletion, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val deletionButton = view.findViewById<Button>(R.id.delete_button)
        deletionButton.setOnClickListener {
            val token = SharedPrefs.token

            CoroutineScope(Dispatchers.Main).launch {
                if (!api.ping().isSuccessful) {
                    Timber.e("Server not available!")
                    val text = R.string.delete_data_server_error
                    Snackbar.make(view, text, Snackbar.LENGTH_LONG).show()
                } else if (token == null) {
                    Timber.e("Token is null! Could not delete data!")
                    val text = R.string.delete_data_no_data
                    Snackbar.make(view, text, Snackbar.LENGTH_LONG).show()
                } else {
                    val response = api.deleteStudyData(token)

                    if (response.isSuccessful) {
                        Timber.e("Data Deletion Successful!")
                        SharedPrefs.token = null
                        sharedPreferences.edit().putBoolean("share_data", false).apply()
                        val text = R.string.delete_data_success
                        Snackbar.make(view, text, Snackbar.LENGTH_LONG).show()
                        findNavController().popBackStack()
                    } else {
                        Timber.e("Data Deletion Failed! Server sent error!")
                        val text = R.string.delete_data_error
                        Snackbar.make(view, text, Snackbar.LENGTH_LONG).show()
                    }
                }
            }


        }
    }

}