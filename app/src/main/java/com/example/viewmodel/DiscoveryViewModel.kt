package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.NowPlayingInfo
import com.example.data.NowPlayingRepository
import com.example.data.PodcastRepository
import com.example.model.Category
import com.example.model.Show
import com.example.player.PlayerManager
import com.example.player.PlayerPlaybackState
import com.example.player.PodcastPlayerManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed class PodcastLoadState {
    object Idle : PodcastLoadState()
    object Loading : PodcastLoadState()
    data class Success(val categories: List<Category>, val message: String, val isFallback: Boolean = false) : PodcastLoadState()
    data class Error(val message: String) : PodcastLoadState()
}

class DiscoveryViewModel(application: Application) : AndroidViewModel(application) {
    private val playerManager = PlayerManager(application)
    private val nowPlayingRepository = NowPlayingRepository()
    private val podcastRepository = PodcastRepository()
    private val podcastPlayerManager = PodcastPlayerManager(application)

    // === РАДИО-ПЛЕЕР ===
    private val _nowPlayingInfo = MutableStateFlow<NowPlayingInfo?>(null)
    val nowPlayingInfo: StateFlow<NowPlayingInfo?> = _nowPlayingInfo.asStateFlow()

    private val _playerState = MutableStateFlow(PlayerPlaybackState.IDLE)
    val playerState: StateFlow<PlayerPlaybackState> = _playerState.asStateFlow()

    private val _playerVolume = MutableStateFlow(playerManager.getVolume())
    val playerVolume: StateFlow<Float> = _playerVolume.asStateFlow()

    // === ПОДКАСТЫ ===
    private val _podcastState = MutableStateFlow<PodcastLoadState>(PodcastLoadState.Idle)
    val podcastState: StateFlow<PodcastLoadState> = _podcastState.asStateFlow()

    // === ПЛЕЕР ПОДКАСТОВ - ПРОКСИРУЕМ ИЗ PodcastPlayerManager ===
    val podcastIsPlaying: StateFlow<Boolean> = podcastPlayerManager.isPlaying
    val currentPodcastTitle: StateFlow<String?> = podcastPlayerManager.currentPodcast
    val podcastIsLoading: StateFlow<Boolean> = podcastPlayerManager.isLoading
    val podcastCurrentPosition: StateFlow<Int> = podcastPlayerManager.currentPosition
    val podcastDuration: StateFlow<Int> = podcastPlayerManager.duration

    // === НАВИГАЦИЯ ПО ПОДКАСТАМ ===
    private var currentPlaylist: List<Show> = emptyList()
    private var currentPlaylistIndex: Int = -1

    // Флаг для отслеживания ручной остановки/паузы
    private var isManualPause = false

    init {
        loadPodcasts()
        fetchAndDisplayNowPlaying()

        // Подписка на радио-плеер
        viewModelScope.launch {
            playerManager.state.collect { state ->
                _playerState.value = state
            }
        }

        viewModelScope.launch {
            playerManager.volume.collect { volume ->
                _playerVolume.value = volume
            }
        }

        // Автоматическое воспроизведение следующего подкаста
        viewModelScope.launch {
            podcastPlayerManager.isPlaying.collect { isPlaying ->
                // Если плеер остановлен вручную - не переключаем
                if (isManualPause) {
                    android.util.Log.d("Podcast", "Ручная пауза, автопереход отключен")
                    return@collect
                }

                // Когда плеер закончил воспроизведение (isPlaying стал false после завершения)
                if (!isPlaying && currentPodcastTitle.value != null && !podcastIsLoading.value) {
                    // Небольшая задержка, чтобы убедиться, что это действительно завершение
                    kotlinx.coroutines.delay(500)
                    // Проверяем, что плеер всё ещё остановлен и не было ручной паузы
                    if (!podcastIsPlaying.value && currentPodcastTitle.value != null && !isManualPause) {
                        android.util.Log.d("Podcast", "Автопереход на следующий трек")
                        playNextPodcast()
                    }
                }
            }
        }
    }

    // === ЗАГРУЗКА ПОДКАСТОВ ===
    fun loadPodcasts() {
        viewModelScope.launch(Dispatchers.IO) {
            _podcastState.value = PodcastLoadState.Loading
            try {
                val categories = podcastRepository.fetchPodcasts()
                withContext(Dispatchers.Main) {
                    if (categories.isNotEmpty()) {
                        _podcastState.value = PodcastLoadState.Success(
                            categories = categories,
                            message = "Загружено ${categories.size} категорий подкастов"
                        )
                    } else {
                        _podcastState.value = PodcastLoadState.Success(
                            categories = emptyList(),
                            message = "Нет доступных подкастов",
                            isFallback = true
                        )
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _podcastState.value = PodcastLoadState.Error("Ошибка загрузки: ${e.message}")
                }
            }
        }
    }

    // === ИНФОРМАЦИЯ О ТЕКУЩЕМ ТРЕКЕ (РАДИО) ===
    fun fetchAndDisplayNowPlaying() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val url = java.net.URL("https://discoveryfm.ru/xml/cur_playing.txt")
                val text = url.readText()
                val parts = text.split(";", "-", limit = 2)
                val artist = parts[0].trim()
                val title = if (parts.size > 1) parts[1].trim() else "Неизвестный трек"
                withContext(Dispatchers.Main) {
                    _nowPlayingInfo.value = NowPlayingInfo(
                        artist = artist,
                        title = title
                    )
                    android.util.Log.d("NowPlaying", "Загружено: $artist - $title")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _nowPlayingInfo.value = NowPlayingInfo(
                        artist = "Неизвестно",
                        title = "Ошибка: ${e.message}"
                    )
                    android.util.Log.e("NowPlaying", "Ошибка загрузки: ${e.message}")
                }
            }
        }
    }

    // === УПРАВЛЕНИЕ ПОДКАСТАМИ ===
    fun playPodcast(show: Show, playlist: List<Show>) {
        if (show.audioUrl.isEmpty()) {
            android.util.Log.e("Podcast", "Нет аудио для: ${show.title}")
            return
        }

        // Сбрасываем флаг ручной паузы
        isManualPause = false

        // Сохраняем плейлист и текущий индекс
        currentPlaylist = playlist
        currentPlaylistIndex = playlist.indexOfFirst { it.title == show.title }
        if (currentPlaylistIndex == -1) currentPlaylistIndex = 0

        // Останавливаем радио, если играет
        if (playerManager.getCurrentState() == PlayerPlaybackState.PLAYING) {
            playerManager.pause()
        }

        // Воспроизводим подкаст
        podcastPlayerManager.play(show.audioUrl, show.title)
    }

    fun togglePodcastPlayback(show: Show) {
        if (show.audioUrl.isEmpty()) {
            android.util.Log.e("Podcast", "Нет аудио для: ${show.title}")
            return
        }

        // Если уже играет этот же подкаст — ставим на паузу или возобновляем
        if (currentPodcastTitle.value == show.title) {
            if (podcastIsPlaying.value) {
                // ПАУЗА - устанавливаем флаг ручной паузы
                isManualPause = true
                podcastPlayerManager.pause()
            } else {
                // ВОЗОБНОВЛЕНИЕ - сбрасываем флаг
                isManualPause = false
                podcastPlayerManager.resume()
            }
            return
        }

        // Останавливаем радио, если играет
        if (playerManager.getCurrentState() == PlayerPlaybackState.PLAYING) {
            playerManager.pause()
        }

        // Сбрасываем флаг ручной паузы
        isManualPause = false

        // Воспроизводим новый подкаст
        podcastPlayerManager.play(show.audioUrl, show.title)
    }

    fun playNextPodcast() {
        if (currentPlaylist.isEmpty() || currentPlaylistIndex == -1) return

        val nextIndex = currentPlaylistIndex + 1
        if (nextIndex < currentPlaylist.size) {
            currentPlaylistIndex = nextIndex
            val nextShow = currentPlaylist[nextIndex]
            // Сбрасываем флаг перед воспроизведением
            isManualPause = false
            podcastPlayerManager.play(nextShow.audioUrl, nextShow.title)
            android.util.Log.d("Podcast", "Автопереход: ${nextShow.title}")
        } else {
            // Если больше нет подкастов — останавливаем
            isManualPause = false
            podcastPlayerManager.stop()
            currentPlaylistIndex = -1
        }
    }

    fun playPreviousPodcast() {
        if (currentPlaylist.isEmpty() || currentPlaylistIndex == -1) return

        val prevIndex = currentPlaylistIndex - 1
        if (prevIndex >= 0) {
            currentPlaylistIndex = prevIndex
            val prevShow = currentPlaylist[prevIndex]
            isManualPause = false
            podcastPlayerManager.play(prevShow.audioUrl, prevShow.title)
        }
    }

    fun seekPodcast(position: Int) {
        podcastPlayerManager.seekTo(position)
    }

    fun isLastPodcast(): Boolean {
        return currentPlaylist.isNotEmpty() && currentPlaylistIndex == currentPlaylist.size - 1
    }

    fun isFirstPodcast(): Boolean {
        return currentPlaylistIndex <= 0
    }

    // === УПРАВЛЕНИЕ РАДИО ===
    fun togglePlay() {
        // Если играет подкаст — останавливаем его
        if (podcastIsPlaying.value) {
            isManualPause = true
            podcastPlayerManager.stop()
        }

        if (playerManager.getCurrentState() == PlayerPlaybackState.PLAYING) {
            playerManager.pause()
        } else {
            playerManager.play()
            fetchAndDisplayNowPlaying()
        }
    }

    fun setVolume(volume: Float) {
        playerManager.setVolume(volume)
    }

    override fun onCleared() {
        super.onCleared()
        playerManager.release()
        podcastPlayerManager.release()
    }
}