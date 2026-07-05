package com.example.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

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
}

// Data-класс для хранения информации о треке
data class NowPlayingInfo(
    val artist: String,
    val title: String
)