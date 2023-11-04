package de.seemoo.at_tracking_detection.ui.dashboard

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
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

fun downloadJson(language: String): String {
    val fallbackURL = "https://tpe.seemoo.tu-darmstadt.de/articles/airguard_articles.json"

    // TODO: set different language article urls
    val url = when (language) {
        "de" -> "https://tpe.seemoo.tu-darmstadt.de/articles/airguard_articles.json"
        "en" -> "https://tpe.seemoo.tu-darmstadt.de/articles/airguard_articles.json"
        else -> fallbackURL
    }

    val errorReturnValue = when (language) {
        "de" -> """{
            "article0": {
                "title": "Keine Internetverbindung",
                "author": "Dennis Arndt",
                "readingTime": 0,
                "previewText": "Es besteht aktuell keine Internetverbindung. Hier werden Artikel angezeigt, die dir helfen die App zu bedienen, sobald du wieder mit dem Internet verbunden bist.",
                "cardColor": "blue_card_background",
                "filename": ""
            }
        }
        """.trimIndent()
        else -> """{
            "article0": {
                "title": "No internet connection",
                "author": "Dennis Arndt",
                "readingTime": 0,
                "previewText": "You are currently not connected to the internet. You will find articles that will help you navigate the app here when you are connected to the internet.",
                "cardColor": "blue_card_background",
                "filename": ""
            }
        }
        """.trimIndent()
    }

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