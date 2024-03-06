package de.seemoo.at_tracking_detection.ui.dashboard

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavDirections
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.google.android.material.card.MaterialCardView
import dagger.hilt.android.AndroidEntryPoint
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.databinding.FragmentDashboardRiskBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber


@AndroidEntryPoint
class DashboardRiskFragment : Fragment() {

    private val viewModel: RiskCardViewModel by viewModels()

    private var _binding: FragmentDashboardRiskBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = DataBindingUtil.inflate(
            inflater,
            R.layout.fragment_dashboard_risk,
            container,
            false
        )
        binding.lifecycleOwner = viewLifecycleOwner
        binding.vm = viewModel
        return binding.root
    }


    @SuppressLint("DiscouragedApi")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val riskCard: MaterialCardView = view.findViewById(R.id.risk_card)
        riskCard.setOnClickListener {
            val directions: NavDirections =
                DashboardRiskFragmentDirections.actionNavigationDashboardToRiskDetailFragment()
            findNavController().navigate(directions)
        }

        val articlesContainer = view.findViewById<ConstraintLayout>(R.id.articles_container)
        val progressBar = view.findViewById<ProgressBar>(R.id.loading_progress_bar)

        lifecycleScope.launch(Dispatchers.IO) {
            progressBar.visibility = View.VISIBLE

            val articlesJSON = downloadJson()
            Timber.d("Articles JSON: %s", articlesJSON)

            withContext(Dispatchers.Main) {
                val articles = parseArticles(articlesJSON)
                Timber.d("Number of Articles: %s", articles.size)

                // Create a new LinearLayout to hold the ArticleCards
                val articleCardsLinearLayout = LinearLayout(context)
                articleCardsLinearLayout.orientation = LinearLayout.VERTICAL
                articleCardsLinearLayout.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

                for (article in articles) {
                    val articleCard = MaterialCardView(context)

                    val layout = LayoutInflater.from(context).inflate(R.layout.include_article_card, null)
                    val textViewTitle = layout.findViewById<TextView>(R.id.card_title)
                    val textViewPreviewText = layout.findViewById<TextView>(R.id.card_text_preview)
                    val imageViewPreview = layout.findViewById<ImageView>(R.id.preview_image)
                    val materialCard = layout.findViewById<MaterialCardView>(R.id.material_card)

                    textViewTitle.text = article.title
                    if (article.previewText.isNotEmpty()){
                        textViewPreviewText.text = article.previewText
                    } else {
                        textViewPreviewText.visibility = View.GONE
                    }

                    val colorResourceId = resources.getIdentifier(article.cardColor, "color", context?.packageName)
                    materialCard.setBackgroundColor(colorResourceId)

                    articleCard.addView(layout)
                    Timber.tag("CardAdded").d("Article card added: %s", article.title)

                    articleCard.layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ).apply {
                        topMargin = 22
                    }

                    if (!article.preview_image.isNullOrEmpty()) { // TODO: Rename when in production to PreviewImage, also in JSON
                        val imageURL = getURL(article.preview_image) // TODO: Rename when in production to PreviewImage, also in JSON
                        context?.let {
                            Glide.with(it)
                                .load(imageURL)
                                .fitCenter()
                                .into(imageViewPreview)
                        }
                    } else {
                        imageViewPreview.visibility = View.GONE
                    }

                    if (!article.filename.isNullOrEmpty()) {
                        articleCard.setOnClickListener {
                            val directions: NavDirections =
                                DashboardRiskFragmentDirections.actionNavigationDashboardToArticleFragment(
                                    author = article.author,
                                    title = article.title,
                                    filename = article.filename,
                                    readingTime = article.readingTime
                                )
                            findNavController().navigate(directions)
                        }
                    }

                    articleCardsLinearLayout.addView(articleCard)
                }

                articlesContainer.addView(articleCardsLinearLayout)
                progressBar.visibility = View.GONE
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.updateRiskLevel()
    }

}
