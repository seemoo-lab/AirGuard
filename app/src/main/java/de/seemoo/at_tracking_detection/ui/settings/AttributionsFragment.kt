package de.seemoo.at_tracking_detection.ui.settings

import AttributionAdapter
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import de.seemoo.at_tracking_detection.R

class AttributionsFragment : Fragment() {

    private lateinit var attributionList: RecyclerView
    private lateinit var attributionAdapter: AttributionAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val rootView = inflater.inflate(R.layout.fragment_attributions, container, false)
        attributionList = rootView.findViewById(R.id.recyclerViewAttributions)

        setupRecyclerView()

        return rootView
    }

    private fun setupRecyclerView() {
        val attributions = getAttributions()
        attributionAdapter = AttributionAdapter(attributions) { attribution ->
            openAttributionLink(attribution.link)
        }

        attributionList.apply {
            layoutManager = LinearLayoutManager(activity)
            adapter = attributionAdapter
        }
    }

    private fun openAttributionLink(link: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
        startActivity(intent)
    }

    private fun getAttributions(): List<AttributionItem> {
        // Replace this with your list of attributions
        return listOf(
            AttributionItem("Image by storyset on Freepik", "https://www.freepik.com/free-vector/push-notifications-concept-illustration_12463949.htm#query=notification&position=0&from_view=search&track=sph"),
            // Add more attributions as needed
        )
    }
}

data class AttributionItem(val name: String, val link: String)