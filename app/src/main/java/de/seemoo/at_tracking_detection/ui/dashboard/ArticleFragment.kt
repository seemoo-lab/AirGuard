package de.seemoo.at_tracking_detection.ui.dashboard

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

class ArticleFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the fragment layout
        val view = inflater.inflate(R.layout.fragment_article, container, false)

        fun errorHandling() {
            Toast.makeText(requireContext(), getString(R.string.article_load_error), Toast.LENGTH_SHORT).show()
        }

        val markdownView = view.findViewById<TextView>(R.id.markdown_view)

        val filename = arguments?.getString("filename")
        if (filename == null || filename.isEmpty()) {
            Timber.e("Filename is null or empty")
            errorHandling()
            return view
        }

        val assetPath = "articles/$filename" // filename already contains language subfolder, e.g. en/faq.md

        val markwon = Markwon.builder(requireContext())
            .usePlugin(SoftBreakAddsNewLinePlugin.create()) // This makes a single line in Markdown break be rendered as a single line break
            .build()

        // Launch coroutine to fetch markdown content
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val markdownString = withContext(Dispatchers.IO) {
                    // Read from assets
                    requireContext().assets.open(assetPath).bufferedReader().use { it.readText() }
                }

                // Main thread: update the UI
                markwon.setMarkdown(markdownView, markdownString)

            } catch (e: Exception) {
                Timber.d(e, "Failed to load markdown from assets: %s", assetPath)
                errorHandling()
            }
        }

        return view
    }
}