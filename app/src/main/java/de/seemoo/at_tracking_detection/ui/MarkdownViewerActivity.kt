package de.seemoo.at_tracking_detection.ui

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import de.seemoo.at_tracking_detection.R
import io.noties.markwon.Markwon

class MarkdownViewerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_markdown_viewer)

        val markdown = """
            # Hello Markdown
            
            This is a sample Markdown file rendered using Markwon library in Kotlin.
            
            - List item 1
            - List item 2
            - List item 3
            
            **Bold Text**
            
            *Italic Text*
            
            ![Image](https://example.com/image.jpg)
            
            `Inline Code`
            
            ```kotlin
            fun main() {
                println("Hello, Markdown!")
            }
            ```
        """.trimIndent()

        val markwon = Markwon.builder(this)
            .build()

        val markdownTextView = findViewById<TextView>(R.id.markdownTextView)
        markwon.setMarkdown(markdownTextView, markdown)
    }
}