package de.seemoo.at_tracking_detection.ui.dashboard

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URL

data class Article(
    val title: String,
    val author: String,
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
    return try {
        val connection = URL(url).openConnection()
        connection.setRequestProperty("Accept", "application/json")
        connection.connectTimeout = 1000

        // Add a debug statement to print the URL of the connection
        println("Connection URL: ${connection.url}")
        val inputStream = connection.getInputStream()
        val reader = BufferedReader(InputStreamReader(inputStream))
        val json = reader.readLines().joinToString("\n")
        reader.close()
        inputStream.close()
        json
    } catch (e: Exception) {
        println("An error occurred while downloading the JSON file: ${e.message}")
        // TODO: "{}" // Return an empty JSON file if something goes wrong
        "{\n" +
        "\t\"article0\": {\n" +
        "\t\t\"title\": \"Airguard Article Test 1\",\n" +
        "\t\t\"author\": \"Alexander Heinrich\",\n" +
        "\t\t\"filename\": \"test.md\"\n" +
        "\t},\n" +
        "\t\"article1\": {\n" +
        "\t\t\"title\": \"Airguard Article Test 2\",\n" +
        "\t\t\"author\": \"Dennis Arndt\",\n" +
        "\t\t\"filename\": \"test.md\"\n" +
        "\t},\n" +
        "\t\"article2\": {\n" +
        "\t\t\"title\": \"Airguard Article Test 3\",\n" +
        "\t\t\"author\": \"Leon Böttger\",\n" +
        "\t\t\"filename\": \"test.md\"\n" +
        "\t}\n" +
        "}"
    }
}