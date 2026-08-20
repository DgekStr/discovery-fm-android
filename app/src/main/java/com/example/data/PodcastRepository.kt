package com.example.data

import com.example.model.Category
import com.example.model.Show
import ru.discoveryfm.player.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class PodcastRepository {
    private val apiUrl = "https://discoveryfm.ru/xml/podcast_api.php"

    suspend fun fetchPodcasts(onProgress: ((Int) -> Unit)? = null): List<Category> {
        return withContext(Dispatchers.IO) {
            try {
                val connection = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10_000
                    readTimeout = 15_000
                    requestMethod = "GET"
                }
                val jsonString = try {
                    if (connection.responseCode !in 200..299) {
                        error("Podcast API HTTP ${connection.responseCode}")
                    }
                    connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader -> reader.readText() }
                } finally {
                    connection.disconnect()
                }
                val jsonObject = JSONObject(jsonString)

                if (!jsonObject.getBoolean("success")) {
                    return@withContext getFallbackCategories()
                }

                val categoriesArray: JSONArray = jsonObject.getJSONArray("categories")
                val result = mutableListOf<Category>()
                val totalCategories = categoriesArray.length()

                for (i in 0 until categoriesArray.length()) {
                    // Отчёт прогресса: категория обработана (0..90%)
                    onProgress?.invoke((i * 90) / totalCategories)
                    val catObj = categoriesArray.getJSONObject(i)
                    val categoryName = catObj.getString("name")
                    val podcastsArray = catObj.getJSONArray("podcasts")

                    // === ПОЛУЧАЕМ КАРТИНКУ КАТЕГОРИИ ===
                    var categoryImage = catObj.optString("imageUrl", "")
                    if (categoryImage.isEmpty()) {
                        categoryImage = catObj.optString("image", "")
                    }
                    if (categoryImage.isEmpty()) {
                        categoryImage = catObj.optString("cover", "")
                    }

                    // Очищаем URL картинки категории
                    categoryImage = categoryImage
                        .replace("\\/", "/")
                        .trim()

                    // Формируем полный URL для картинки категории
                    val finalCategoryImage = if (categoryImage.isNotEmpty() &&
                        !categoryImage.startsWith("http://") &&
                        !categoryImage.startsWith("https://")) {
                        val cleanPath = categoryImage.trimStart('/')
                        "https://discoveryfm.ru/$cleanPath"
                    } else {
                        categoryImage
                    }

                    if (BuildConfig.DEBUG) {
                        android.util.Log.d("PodcastRepo", "🏷️ Категория: $categoryName")
                        android.util.Log.d("PodcastRepo", "🖼️ Картинка категории: $finalCategoryImage")
                    }

                    val shows = mutableListOf<Show>()
                    val totalPodcasts = podcastsArray.length()
                    for (j in 0 until podcastsArray.length()) {
                        // Отчёт прогресса: подкасты внутри категории (0..90%)
                        onProgress?.invoke((i * 90) / totalCategories + (j * 90) / totalCategories / totalPodcasts)
                        val item = podcastsArray.getJSONObject(j)

                        val durationStr = item.optString("duration", "")
                        val durationSeconds = parseDurationToSeconds(durationStr)

                        var audioUrl = item.optString("audioUrl", "")
                        if (audioUrl.isNotEmpty()) {
                            audioUrl = audioUrl.replace("\\/", "/")
                            if (audioUrl.startsWith("/")) {
                                audioUrl = "https://discoveryfm.ru$audioUrl"
                            } else if (!audioUrl.startsWith("http")) {
                                audioUrl = "https://discoveryfm.ru/$audioUrl"
                            }
                        }

                        // === ПОЛУЧАЕМ КАРТИНКУ ПОДКАСТА ===
                        var imageUrl = item.optString("imageUrl", "")
                        if (imageUrl.isEmpty()) {
                            imageUrl = item.optString("image", "")
                        }
                        if (imageUrl.isEmpty()) {
                            imageUrl = item.optString("cover", "")
                        }
                        if (imageUrl.isEmpty()) {
                            imageUrl = item.optString("picture", "")
                        }

                        // Если у подкаста нет своей картинки - используем картинку категории
                        if (imageUrl.isEmpty()) {
                            imageUrl = categoryImage // Используем оригинальный URL без изменений
                        }

                        // Очищаем URL
                        imageUrl = imageUrl
                            .replace("\\/", "/")
                            .trim()

                        // Если URL не полный - добавляем базовый
                        if (imageUrl.isNotEmpty() && !imageUrl.startsWith("http://") && !imageUrl.startsWith("https://")) {
                            val cleanPath = imageUrl.trimStart('/')
                            imageUrl = "https://discoveryfm.ru/$cleanPath"
                        }

                        if (BuildConfig.DEBUG) {
                            android.util.Log.d(
                                "PodcastRepo",
                                "  📦 АУДИОframes: ${item.getString("title")}"
                            )
                            android.util.Log.d("PodcastRepo", "  🖼️ imageUrl: $imageUrl")
                        }

                        shows.add(
                            Show(
                                title = Show.cleanDisplayTitle(item.getString("title")),
                                description = Show.cleanDisplayTitle(item.optString("description", "Описание отсутствует")),
                                audioUrl = audioUrl,
                                link = item.optString("link", ""),
                                duration = durationSeconds,
                                imageUrl = imageUrl
                            )
                        )
                    }

                    // Добавляем категорию с картинкой
                    result.add(
                        Category(
                            name = Show.cleanDisplayTitle(categoryName),
                            shows = shows,
                            imageUrl = finalCategoryImage  // <-- КАРТИНКА КАТЕГОРИИ
                        )
                    )
                }

                if (result.isEmpty()) {
                    return@withContext getFallbackCategories()
                }

                onProgress?.invoke(100)
                result

            } catch (e: Exception) {
                if (BuildConfig.DEBUG) e.printStackTrace()
                onProgress?.invoke(100)
                getFallbackCategories()
            }
        }
    }

    internal fun parseDurationToSeconds(durationStr: String): Int {
        if (durationStr.isEmpty()) return 0
        return try {
            val parts = durationStr.split(":")
            when (parts.size) {
                3 -> {
                    val hours = parts[0].toInt()
                    val minutes = parts[1].toInt()
                    val seconds = parts[2].toInt()
                    hours * 3600 + minutes * 60 + seconds
                }
                2 -> {
                    val minutes = parts[0].toInt()
                    val seconds = parts[1].toInt()
                    minutes * 60 + seconds
                }
                else -> 0
            }
        } catch (e: Exception) {
            0
        }
    }

    private fun getFallbackCategories(): List<Category> {
        return listOf(
            Category(
                name = "🎙️ АУДИОframes Discovery FM",
                shows = listOf(
                    Show("АУДИОframes 1", "Описание АУДИОframes 1", "", "", 0, ""),
                    Show("АУДИОframes 2", "Описание АУДИОframes 2", "", "", 0, ""),
                    Show("АУДИОframes 3", "Описание АУДИОframes 3", "", "", 0, "")
                ),
                imageUrl = ""
            )
        )
    }
}