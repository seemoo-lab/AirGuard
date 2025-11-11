package de.seemoo.at_tracking_detection.ui.dashboard

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import de.seemoo.at_tracking_detection.R
import io.noties.markwon.Markwon
import io.noties.markwon.SoftBreakAddsNewLinePlugin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.net.URL


class ArticleFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the fragment layout
        val view = inflater.inflate(R.layout.fragment_article, container, false)

        fun errorHandling() {
            Toast.makeText(requireContext(), "No internet connection. Cannot load article.", Toast.LENGTH_SHORT).show()
        }

        // Commented lines: old code that still showed meta data
        // val titleTextView = view.findViewById<TextView>(R.id.article_title) // We removed this in the current version
        // val authorTextView = view.findViewById<TextView>(R.id.article_author)
        val markdownView = view.findViewById<TextView>(R.id.markdown_view)
        /// val articleReadingTimeView = view.findViewById<TextView>(R.id.article_reading_time)

        val title = arguments?.getString("title")
        val author = arguments?.getString("author")
        val readingTime = arguments?.getInt("readingTime")
        val filename = arguments?.getString("filename")

        if (filename == null) {
            Timber.e("Filename is null")
            errorHandling()
            return view
        }

        val url = getURL(filename)

        // titleTextView.text = title
        // authorTextView.text = author
        // articleReadingTimeView.text = context?.getString(R.string.article_reading_time, readingTime)

        val connectivityManager = context?.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkCapabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)

        if ((networkCapabilities != null) && networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            val markwon = Markwon.builder(requireContext())
                .usePlugin(SoftBreakAddsNewLinePlugin.create()) // This makes a single line in Markdown break be rendered as a single line break
                .build()

            // Launch coroutine to fetch markdown content
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    // IO thread: network operation
                    val markdownString = withContext(Dispatchers.IO) {
                        URL(url).readText()
                    }

                    // Main thread: update the UI
                    markwon.setMarkdown(markdownView, markdownString)

                } catch (e: Exception) {
                    Timber.d(e)
                    errorHandling()
                }
            }

        } else {
            errorHandling()
        }

        return view
    }
}