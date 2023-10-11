package de.seemoo.at_tracking_detection.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import com.mukesh.MarkDown
import de.seemoo.at_tracking_detection.R
import java.net.URL


class ArticleFragment : Fragment() {

    private val titleTextView: TextView by lazy { view?.findViewById(R.id.article_title) as TextView }
    private val authorTextView: TextView by lazy { view?.findViewById(R.id.article_author) as TextView }
    private val readingTimeTextView: TextView by lazy { view?.findViewById(R.id.article_reading_time) as TextView }
    // private val articleWebView: WebView by lazy { view?.findViewById(R.id.article_webview) as WebView }
    private val composeView: ComposeView by lazy { view?.findViewById(R.id.markdown) as ComposeView }


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the fragment layout
        val view = inflater.inflate(R.layout.fragment_article, container, false)

        val markdown = """
            ### What's included 🚀
            
            - Super simple setup
            - Cross-platform ready
            - Lightweight
            """.trimIndent()

        composeView.apply {
            // Dispose of the Composition when the view's LifecycleOwner is destroyed
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                MarkDown(
                    text = markdown,
                )
            }
        }

        return view
    }
}