package com.example.data

import com.example.model.Category
import com.example.model.Show
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL

class PodcastRepository {
    private val apiUrl = "https://discoveryfm.ru/xml/podcast_api.php"

    suspend fun fetchPodcasts(): List<Category> {
        return withContext(Dispatchers.IO) {
            try {
                val jsonString = URL(apiUrl)
                    .openConnection()
                    .getInputStream()
                    .bufferedReader(Charsets.UTF_8)
                    .use { it.readText() }
                val jsonObject = JSONObject(jsonString)

                if (!jsonObject.getBoolean("success")) {
                    return@withContext getFallbackCategories()
                }

                val categoriesArray: JSONArray = jsonObject.getJSONArray("categories")
                val result = mutableListOf<Category>()

                for (i in 0 until categoriesArray.length()) {
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

                    android.util.Log.d("PodcastRepo", "🏷️ Категория: $categoryName")
                    android.util.Log.d("PodcastRepo", "🖼️ Картинка категории: $finalCategoryImage")

                    val shows = mutableListOf<Show>()
                    for (j in 0 until podcastsArray.length()) {
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

                        android.util.Log.d(
                            "PodcastRepo",
                            "  📦 Подкаст: ${item.getString("title")}"
                        )
                        android.util.Log.d("PodcastRepo", "  🖼️ imageUrl: $imageUrl")

                        shows.add(
                            Show(
                                title = item.getString("title"),
                                description = item.optString("description", "Описание отсутствует"),
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
                            name = categoryName,
                            shows = shows,
                            imageUrl = finalCategoryImage  // <-- КАРТИНКА КАТЕГОРИИ
                        )
                    )
                }

                if (result.isEmpty()) {
                    return@withContext getFallbackCategories()
                }

                result

            } catch (e: Exception) {
                e.printStackTrace()
                getFallbackCategories()
            }
        }
    }

    private fun parseDurationToSeconds(durationStr: String): Int {
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
                name = "🎙️ Подкасты Discovery FM",
                shows = listOf(
                    Show("Подкаст 1", "Описание подкаста 1", "", "", 0, ""),
                    Show("Подкаст 2", "Описание подкаста 2", "", "", 0, ""),
                    Show("Подкаст 3", "Описание подкаста 3", "", "", 0, "")
                ),
                imageUrl = ""
            )
        )
    }
}