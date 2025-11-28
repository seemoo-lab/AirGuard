package de.seemoo.at_tracking_detection.ui.settings

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.net.toUri
import androidx.recyclerview.widget.RecyclerView
import de.seemoo.at_tracking_detection.R

class TranslationSupporterAdapter(
    private val translationSupports: List<TranslationSupportItem>
) : RecyclerView.Adapter<TranslationSupporterAdapter.TranslationSupporterViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TranslationSupporterViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_translation_supporter, parent, false)
        return TranslationSupporterViewHolder(view)
    }

    override fun onBindViewHolder(holder: TranslationSupporterViewHolder, position: Int) {
        val item = translationSupports[position]
        holder.bind(item)
    }

    override fun getItemCount(): Int = translationSupports.size

    class TranslationSupporterViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val languageTextView: TextView = itemView.findViewById(R.id.textViewLanguage)
        private val translatorsLayout: LinearLayout = itemView.findViewById(R.id.layoutTranslators)

        fun bind(item: TranslationSupportItem) {
            languageTextView.text = item.language
            translatorsLayout.removeAllViews()

            val translatorTextColor = languageTextView.currentTextColor

            // Density for dp -> px conversion
            val density = itemView.context.resources.displayMetrics.density
            val verticalPaddingPx = (4 * density + 0.5f).toInt() // smaller spacing (4dp)

            item.translators.forEach { translator ->
                val translatorView = TextView(itemView.context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    text = translator.name
                    setPadding(0, verticalPaddingPx, 0, verticalPaddingPx)
                    setTextColor(translatorTextColor)
                }

                if (translator.link != null) {
                    translatorView.setOnClickListener {
                        val intent = Intent(Intent.ACTION_VIEW, translator.link.toUri())
                        itemView.context.startActivity(intent)
                    }
                    translatorView.isClickable = true
                    translatorView.isFocusable = true
                }

                translatorsLayout.addView(translatorView)
            }
        }
    }
}

data class TranslationSupportItem(
    val language: String,
    val translators: List<Translator>
)

data class Translator(
    val name: String,
    val link: String? = null
)
