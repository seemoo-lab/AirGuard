package de.seemoo.at_tracking_detection.ui.dashboard

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication
import de.seemoo.at_tracking_detection.R
import timber.log.Timber
import java.util.Locale

data class Article(
    val title: String,
    val author: String,
    val readingTime: Int,
    val previewText: String,
    val cardColor: String,
    val preview_image: String, // This has to be named like this because of the file format
    val filename: String
)

fun parseArticles(jsonString: String): List<Article> {
    val gson = Gson()
    val listType = object : TypeToken<Map<String, Article>>() {}.type
    val articleMap: Map<String, Article> = gson.fromJson(jsonString, listType)
    return articleMap.values.toList()
}

// Returns the best matching language code for which an articles file exists.
// TODO: rewrite to not use fixed list of values
private fun resolveLanguageCode(context: Context): String {
    val available = listOf(
        "en", "de", "fr", "it", "ja", "ru", "sk", "cs", "zh-rTW"
    )
    val locale = context.resources.configuration.locales[0]
    val lang = locale.language // e.g. "en"
    val country = locale.country // e.g. "US"

    val candidates = mutableListOf<String>()
    if (country.isNotEmpty()) {
        candidates += "$lang-$country" // try language-country (e.g. zh-TW)
    }
    candidates += lang // try plain language

    // Normalize candidate casing for special file like zh-rTW
    val normalized = candidates.map { cand ->
        if (cand.equals("zh-tw", ignoreCase = true)) "zh-rTW" else cand
    }

    for (c in normalized) {
        if (available.contains(c)) return c
    }
    return "en" // fallback
}

fun loadArticlesJson(context: Context = ATTrackingDetectionApplication.getAppContext()): String {
    val languageCode = resolveLanguageCode(context)
    val assetFileName = "articles/airguard_articles_${languageCode}.json"

    return try {
        context.assets.open(assetFileName).bufferedReader().use { it.readText() }
    } catch (e: Exception) {
        Timber.e(e, "Failed to load articles for %s, falling back to en", languageCode)
        try {
            context.assets.open("articles/airguard_articles_en.json").bufferedReader().use { it.readText() }
        } catch (inner: Exception) {
            Timber.e(inner, "Failed to load fallback english articles")
            // Build a minimal fallback inline so UI still shows something.
            val offlineTitle = context.getString(R.string.article_offline_header)
            val offlineText = context.getString(R.string.article_offline_text)
            """{"article0":{"title":"$offlineTitle","author":"System","readingTime":0,"previewText":"$offlineText","cardColor":"blue_card_background","preview_image":"","filename":""}}"""
        }
    }
}

fun getLocalImageUri(imageFilename: String): String {
    return "file:///android_asset/articles/$imageFilename"
}
