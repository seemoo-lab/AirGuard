package de.seemoo.at_tracking_detection.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import com.mukesh.MarkDown
import de.seemoo.at_tracking_detection.R


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

        markdownView.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                MarkDown(
                    url = url,
                    modifier = modifier
                )
            }
        }

        return view
    }
}

