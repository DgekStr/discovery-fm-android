package com.example.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
import org.json.JSONObject

class NowPlayingRepository {
    // Функция загружает данные с сервера и парсит их
    suspend fun fetchNowPlaying(): NowPlayingInfo? {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("https://discoveryfm.ru/xml/cur_playing.txt")
                val text = url.readText()
                parseNowPlaying(text)
            } catch (e: Exception) {
                // В случае ошибки (нет интернета, сервер не отвечает) возвращаем null
                null
            }
        }
    }

    // Парсинг строки вида "Исполнитель;Название"
    private fun parseNowPlaying(text: String): NowPlayingInfo? {
        val parts = text.split(";", ignoreCase = false, limit = 2)
        return if (parts.size == 2) {
            NowPlayingInfo(
                artist = parts[0].trim(),
                title = parts[1].trim()
            )
        } else {
            null
        }
    }

    /**
     * Ищет фото исполнителя через бесплатный Deezer API по имени артиста.
     * Сначала берёт первую часть имени артиста (до ";" или " / "), чтоб не искать дуэты целиком.
     * Возвращает URL картинки или null, если не нашли.
     */
    suspend fun fetchArtistImage(artist: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                // Берём основного исполнителя — до разделителей
                val mainArtist = artist
                    .split(";", " / ", "/", " feat", " ft.", " & ")
                    .first()
                    .trim()

                if (mainArtist.isEmpty()) return@withContext null

                val query = java.net.URLEncoder.encode(mainArtist, "UTF-8")
                val url = URL("https://api.deezer.com/search/artist?q=$query&limit=1")

                val text = url.readText()
                val json = JSONObject(text)
                val data = json.optJSONArray("data")
                if (data != null && data.length() > 0) {
                    val first = data.getJSONObject(0)
                    val picture = first.optString("picture_xl", "")
                    if (picture.isNotEmpty()) return@withContext picture
                }
                null
            } catch (e: Exception) {
                null
            }
        }
    }
}

// Data-класс для хранения информации о треке
data class NowPlayingInfo(
    val artist: String,
    val title: String
)
