package de.seemoo.at_tracking_detection.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.statistics.api.Api
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

class DataDeletionFragment : Fragment() {
    @Inject
    lateinit var api: Api

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val rootView = inflater.inflate(R.layout.fragment_data_deletion, container, false)
        return rootView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val sharedPreferences = requireActivity().getSharedPreferences("shared_preferences", 0)
        val deletionButton = view.findViewById<Button>(R.id.delete_button)
        deletionButton.setOnClickListener {
            val token = sharedPreferences.getString("token", null)
            if (token != null) {
                CoroutineScope(Dispatchers.Main).launch {
                    val response = api.deleteStudyData(token)
                    val text = when {
                        response.isSuccessful -> R.string.delete_data_success
                        else -> R.string.delete_data_error
                    }
                    Snackbar.make(view, text, Snackbar.LENGTH_LONG).show()
                    if (response.isSuccessful) {
                        findNavController().popBackStack()
                    }
                }
            } else {
                val text = R.string.delete_data_no_data
                Snackbar.make(view, text, Snackbar.LENGTH_LONG).show()
            }
        }
    }

}