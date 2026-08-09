package com.example.data

import com.example.model.Category
import com.example.model.Show
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
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

                // === ДОПОЛНИТЕЛЬНО: ОЦЕНКА ДЛИТЕЛЬНОСТИ ДЛЯ ПОДКАСТОВ БЕЗ НЕЁ ===
                // Если API не отдал duration, оцениваем по размеру аудиофайла (HEAD-запрос)
                val withEstimates = estimateMissingDurations(result)

                withEstimates

            } catch (e: Exception) {
                e.printStackTrace()
                getFallbackCategories()
            }
        }
    }

    /**
     * Для подкастов без длительности делает HEAD-запрос к аудиофайлу
     * и оценивает длительность по размеру файла.
     * Возвращает новый список категорий с заполненными длительностями.
     */
    private suspend fun estimateMissingDurations(categories: List<Category>): List<Category> {
        // Сопоставляем «show → длительность» для всех без неё
        val showsToEstimate = categories
            .flatMap { it.shows }
            .filter { it.duration <= 0 && it.audioUrl.isNotEmpty() }

        if (showsToEstimate.isEmpty()) return categories

        android.util.Log.d("PodcastRepo", "⏱️ Оцениваем длительность для ${showsToEstimate.size} подкастов...")

        // id объекта -> оценённая длительность
        val estimatedByIdentity = mutableMapOf<Show, Int>()

        // Обрабатываем порциями (по 8 параллельных HEAD-запросов)
        val chunkSize = 8
        coroutineScope {
            for (chunk in showsToEstimate.chunked(chunkSize)) {
                val deferreds = chunk.map { show ->
                    async(Dispatchers.IO) {
                        show to estimateDurationFromFileSize(show.audioUrl)
                    }
                }

                for ((show, estimated) in deferreds.awaitAll()) {
                    if (estimated > 0) {
                        estimatedByIdentity[show] = estimated
                    }
                }
            }
        }

        android.util.Log.d("PodcastRepo", "⏱️ Оценено: ${estimatedByIdentity.size} из ${showsToEstimate.size}")

        // Пересоздаём категории с учётом оценённых длительностей
        return categories.map { cat ->
            val newShows = cat.shows.map { show ->
                val estimated = estimatedByIdentity[show]
                if (estimated != null && estimated > 0) {
                    show.copy(duration = estimated)
                } else {
                    show
                }
            }
            cat.copy(shows = newShows)
        }
    }

    /**
     * HEAD-запрос к аудиофайлу, оценка длительности по размеру.
     * Битрейт файлов DiscoveryFM ≈ 111 kbps (проверено по файлу с известной длительностью).
     * ~13860 байт/сек.
     */
    private fun estimateDurationFromFileSize(audioUrl: String): Int {
        return try {
            val url = URL(audioUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "HEAD"
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.instanceFollowRedirects = true

            val contentLength = connection.contentLengthLong
            connection.disconnect()

            if (contentLength > 0) {
                val bytesPerSecond = 13860.0 // ~111 kbps (с запасом на заголовки MP3)
                (contentLength / bytesPerSecond).toInt().coerceAtLeast(1)
            } else {
                0
            }
        } catch (e: Exception) {
            android.util.Log.e("PodcastRepo", "Ошибка HEAD-запроса для $audioUrl: ${e.message}")
            0
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