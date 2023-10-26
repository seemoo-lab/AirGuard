package de.seemoo.at_tracking_detection.ui.dashboard

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.NavDirections
import androidx.navigation.fragment.findNavController
import com.google.android.material.card.MaterialCardView
import dagger.hilt.android.AndroidEntryPoint
import de.seemoo.at_tracking_detection.R
import de.seemoo.at_tracking_detection.databinding.FragmentDashboardRiskBinding
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


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val riskCard: MaterialCardView = view.findViewById(R.id.risk_card)
        riskCard.setOnClickListener {
            val directions: NavDirections =
                DashboardRiskFragmentDirections.actionNavigationDashboardToRiskDetailFragment()
            findNavController().navigate(directions)
        }

//        val articleCard: MaterialCardView = view.findViewById(R.id.article_card)
//        articleCard.setOnClickListener {
//            val directions: NavDirections =
//                DashboardRiskFragmentDirections.actionNavigationDashboardToArticleFragment(author = "test", title = "test", filename = "test.md")
//            findNavController().navigate(directions)
//        }

        val articlesJSON = downloadJson("https://tpe.seemoo.tu-darmstadt.de/articles/airguard_articles.json")

        Timber.d("Articles JSON: %s", articlesJSON)

        val articles = parseArticles(articlesJSON)

        val articlesContainer = view.findViewById<ConstraintLayout>(R.id.articles_container)

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
            val materialCard = layout.findViewById<MaterialCardView>(R.id.material_card)

            textViewTitle.text = article.title
            textViewPreviewText.text = article.previewText

            // TODO: for some reason not picking correct color
            val colorResourceId = resources.getIdentifier(article.cardColor, "color", context?.packageName)
            materialCard.setBackgroundColor(colorResourceId)

            articleCard.addView(layout)
            Timber.tag("CardAdded").d("Article card added: %s", article.title)

            articleCard.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )

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

            articleCardsLinearLayout.addView(articleCard)
        }

        articlesContainer.addView(articleCardsLinearLayout)

    }

    override fun onStart() {
        super.onStart()
        viewModel.updateRiskLevel()
    }

}
