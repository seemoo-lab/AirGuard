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
// Dynamically discovers available languages from assets/articles/airguard_articles_*.json
private fun resolveLanguageCode(context: Context): String {
    val assetPath = "articles"
    val availableCodes: Set<String> = try {
        val entries = context.assets.list(assetPath)?.toList().orEmpty()
        val regex = Regex("^airguard_articles_(.+)\\.json$", RegexOption.IGNORE_CASE)
        entries.mapNotNull { name ->
            regex.matchEntire(name)?.groupValues?.getOrNull(1)
        }.toSet()
    } catch (e: Exception) {
        Timber.w(e, "Could not list assets in %s; falling back to defaults", assetPath)
        emptySet()
    }

    val locale = context.resources.configuration.locales[0]
    val lang = locale.language // e.g. "en"
    val country = locale.country // e.g. "US"

    // Build candidates in preference order.
    val candidates = buildList {
        if (country.isNotEmpty()) {
            add("$lang-r$country") // e.g. zh-rTW
            add("$lang-$country")  // e.g. zh-TW
        }
        add(lang) // e.g. zh
    }

    fun findMatch(c: String): String? = availableCodes.firstOrNull { it.equals(c, ignoreCase = true) }

    for (cand in candidates) {
        findMatch(cand)?.let { return it }
    }

    // Fallback preference: English if present; otherwise any available; otherwise "en"
    findMatch("en")?.let { return it }
    return availableCodes.firstOrNull() ?: "en"
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
