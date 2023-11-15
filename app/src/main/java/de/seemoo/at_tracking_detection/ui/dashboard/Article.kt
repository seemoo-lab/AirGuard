package de.seemoo.at_tracking_detection.ui.dashboard

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import de.seemoo.at_tracking_detection.ATTrackingDetectionApplication
import de.seemoo.at_tracking_detection.R
import timber.log.Timber
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

data class Article(
    val title: String,
    val author: String,
    val readingTime: Int,
    val previewText: String,
    val cardColor: String,
    val filename: String
)

fun parseArticles(jsonString: String): List<Article> {
    val gson = Gson()
    val listType = object : TypeToken<Map<String, Article>>() {}.type
    val articleMap: Map<String, Article> = gson.fromJson(jsonString, listType)
    return articleMap.values.toList()
}

fun getURL(filename: String): URL {
    return URL("https://tpe.seemoo.tu-darmstadt.de/static/articles/$filename")
}

fun downloadJson(): String {
    val url = ATTrackingDetectionApplication.getAppContext().resources.getString(R.string.article_download_url)

    val articleOfflineTitle = ATTrackingDetectionApplication.getAppContext().resources.getString(R.string.article_offline_header)
    val articleOfflineText = ATTrackingDetectionApplication.getAppContext().resources.getString(R.string.article_offline_text)
    val iveGotANotification = ATTrackingDetectionApplication.getAppContext().resources.getString(R.string.i_got_a_notification_what_should_i_do)
    val searchManually = ATTrackingDetectionApplication.getAppContext().resources.getString(R.string.notification_help)
    val iCanNotFindTracker = ATTrackingDetectionApplication.getAppContext().resources.getString(R.string.i_cannot_find_the_tracker)
    val findTackerHelp = ATTrackingDetectionApplication.getAppContext().resources.getString(R.string.find_tracker_help)

    val errorReturnValue = """{
            "article0": {
                "title": "$articleOfflineTitle",
                "author": "Dennis Arndt",
                "readingTime": 0,
                "previewText": "$articleOfflineText",
                "cardColor": "blue_card_background",
                "filename": ""
            },
            "article1": {
                "title": "$iveGotANotification",
                "author": "Alexander Heinrich",
                "readingTime": 0,
                "previewText": "$searchManually",
                "cardColor": "gray_card_background",
                "filename": ""
            },
            "article2": {
                "title": "$iCanNotFindTracker",
                "author": "Alexander Heinrich",
                "readingTime": 0,
                "previewText": "$findTackerHelp",
                "cardColor": "gray_card_background",
                "filename": ""
            }
        }
        """.trimIndent()

    val connection = URL(url).openConnection() as HttpURLConnection

    return try {
        connection.requestMethod = "GET"
        val responseCode = connection.responseCode

        if (responseCode == HttpURLConnection.HTTP_OK) {
            val reader = BufferedReader(InputStreamReader(connection.inputStream))
            val response = StringBuilder()
            var inputLine: String?
            while (reader.readLine().also { inputLine = it } != null) {
                response.append(inputLine)
            }
            reader.close()
            response.toString()
        } else {
            errorReturnValue
        }
    } catch (e: Exception) {
        Timber.e(e)
        errorReturnValue
    } finally {
        connection.disconnect()
    }
}