package de.seemoo.at_tracking_detection.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import de.seemoo.at_tracking_detection.R

class DataDeletionFragment : Fragment() {

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
            if (token == null) {
                // TODO
            } else {
                // TODO
            }
        }
    }

}