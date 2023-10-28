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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import com.mukesh.MarkDown
import de.seemoo.at_tracking_detection.R
import timber.log.Timber


class ArticleFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the fragment layout
        val view = inflater.inflate(R.layout.fragment_article, container, false)

        val titleTextView = view.findViewById<TextView>(R.id.article_title)
        val authorTextView = view.findViewById<TextView>(R.id.article_author)
        val markdownView = view.findViewById<ComposeView>(R.id.markdown_view)
        val articleReadingTimeView = view.findViewById<TextView>(R.id.article_reading_time)

        val title = arguments?.getString("title")
        val author = arguments?.getString("author")
        val readingTime = arguments?.getInt("readingTime")
        val filename = arguments?.getString("filename")

        val url = getURL(filename!!)

        titleTextView.text = title
        authorTextView.text = author
        articleReadingTimeView.text = context?.getString(R.string.article_reading_time, readingTime)

        var modifier = Modifier.fillMaxSize()

        val connectivityManager = context?.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkCapabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)

        fun errorHandling() {
            Toast.makeText(requireContext(), "No internet connection. Cannot load article.", Toast.LENGTH_SHORT).show()
        }

        if ((networkCapabilities != null) && networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            try {
                markdownView.apply {
                    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                    setContent {
                        MarkDown(
                            url = url,
                            modifier = modifier
                        )
                    }
                }
            } catch (e: Exception) {
                Timber.d(e)
                errorHandling()
            }

        } else {
            errorHandling()
        }

        return view
    }
}

