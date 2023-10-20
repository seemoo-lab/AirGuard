package de.seemoo.at_tracking_detection.ui.settings

import AttributionAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.NavDirections
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import de.seemoo.at_tracking_detection.R

class InformationFragment : Fragment() {

    private lateinit var attributionList: RecyclerView
    private lateinit var attributionAdapter: AttributionAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val rootView = inflater.inflate(R.layout.fragment_information, container, false)
        attributionList = rootView.findViewById(R.id.recyclerViewAttributions)

        setupRecyclerView()

        val versionNumberTextView = rootView.findViewById<TextView>(R.id.versionNumber)
        try {
            val versionNumber = requireActivity().packageManager.getPackageInfo(
                requireActivity().packageName,
                0
            ).versionName
            val versionText = getString(R.string.version_number, versionNumber)
            versionNumberTextView.text = versionText
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }

        return rootView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val developerText = view.findViewById<TextView>(R.id.developer_text)
        val maintainerText = view.findViewById<TextView>(R.id.maintainer_text)

        developerText.text = getString(R.string.developer, "Dennis Arndt")
        maintainerText.text = getString(R.string.maintainer, "Alexander Heinrich")

        val contact = view.findViewById<LinearLayout>(R.id.contact_mail)
        val developer = view.findViewById<LinearLayout>(R.id.developer_mail)
        val maintainer = view.findViewById<LinearLayout>(R.id.maintainer_mail)
        val libraries = view.findViewById<LinearLayout>(R.id.libraries)
        val twitter = view.findViewById<LinearLayout>(R.id.twitter)

        contact.setOnClickListener {
            val emailAddress = "airguard@seemoo.tu-darmstadt.de"
            composeEmail(emailAddress)
        }
        developer.setOnClickListener {
            val emailAddress = "darndt@seemoo.tu-darmstadt.de"
            composeEmail(emailAddress)
        }
        maintainer.setOnClickListener {
            val emailAddress = "aheinrich@seemoo.tu-darmstadt.de"
            composeEmail(emailAddress)
        }
        libraries.setOnClickListener {
            val directions: NavDirections =
                InformationFragmentDirections.actionInformationToAboutLibs()
            findNavController().navigate(directions)
        }
        twitter.setOnClickListener{
            val intent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://twitter.com/AirGuardAndroid")
            )
            startActivity(intent)
        }
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

    private fun composeEmail(emailAddress: String) {
        val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:$emailAddress")
            putExtra(Intent.EXTRA_SUBJECT, "Subject of the email")
            putExtra(Intent.EXTRA_TEXT, "Body of the email")
        }

        startActivity(emailIntent)
    }

}

data class AttributionItem(val name: String, val link: String)