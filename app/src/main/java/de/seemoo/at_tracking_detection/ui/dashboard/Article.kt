package de.seemoo.at_tracking_detection.ui.dashboard

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.BufferedReader
import java.io.InputStreamReader
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

//fun calculateReadingTime(text: String): Int {
//    val wordsPerMinute = 250 // Average words per minute reading speed
//    val wordCount = text.split(" ").count()
//    val readingTimeInMinutes = wordCount / wordsPerMinute
//    return readingTimeInMinutes * 60 * 1000 // Convert to milliseconds
//}

fun downloadJson(url: String): String {
    return """
        {
            "article1": {
                "title": "Airguard Article Test 1",
                "author": "Alexander Heinrich",
                "readingTime": 5,
                "previewText": "Preview Text 1",
                "cardColor": "blue",
                "filename": "test.md"
            },
            "article2": {
                "title": "Airguard Article Test 2",
                "author": "Dennis Arndt",
                "readingTime": 2,
                "previewText": "Preview Text 2",
                "cardColor": "grey",
                "filename": "test.md"
            },
            "article3": {
                "title": "Airguard Article Test 3",
                "author": "Leon Böttger",
                "readingTime": 4,
                "previewText": "Preview Text 3",
                "cardColor": "grey",
                "filename": "test.md"
            }
        }
        """

//    return try {
//        val connection = URL(url).openConnection()
//        connection.setRequestProperty("Accept", "application/json")
//        connection.connectTimeout = 1000
//
//        // Add a debug statement to print the URL of the connection
//        println("Connection URL: ${connection.url}")
//        val inputStream = connection.getInputStream()
//        val reader = BufferedReader(InputStreamReader(inputStream))
//        val json = reader.readLines().joinToString("\n")
//        reader.close()
//        inputStream.close()
//        json
//    } catch (e: Exception) {
//        println("An error occurred while downloading the JSON file: ${e.message}")
//        // TODO: "{}" // Return an empty JSON file if something goes wrong
//        """
//        {
//            {
//                "title": "Airguard Article Test 1",
//                "author": "Alexander Heinrich",
//                "readingTime": 5,
//                "previewText": "Preview Text 1",
//                "cardColor": "blue",
//                "filename": "test.md"
//            },
//            {
//                "title": "Airguard Article Test 2",
//                "author": "Dennis Arndt",
//                "readingTime": 2,
//                "previewText": "Preview Text 2",
//                "cardColor": "grey",
//                "filename": "test.md"
//            },
//            {
//                "title": "Airguard Article Test 3",
//                "author": "Leon Böttger",
//                "readingTime": 4,
//                "previewText": "Preview Text 3",
//                "cardColor": "grey",
//                "filename": "test.md"
//            }
//        }
//        """
//    }
}