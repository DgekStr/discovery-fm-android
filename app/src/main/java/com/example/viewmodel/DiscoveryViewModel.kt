package com.example.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.NowPlayingInfo
import com.example.data.NowPlayingRepository
import com.example.data.PodcastRepository
import com.example.data.ThemePreferences
import com.example.model.Category
import com.example.model.Show
import com.example.player.PlaybackService
import com.example.player.PlayerPlaybackState
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class PodcastLoadState {
    object Idle : PodcastLoadState()
    object Loading : PodcastLoadState()
    data class Success(val categories: List<Category>, val message: String, val isFallback: Boolean = false) : PodcastLoadState()
    data class Error(val message: String) : PodcastLoadState()
}

/** Результат поиска: подкаст + название его категории */
data class SearchResultItem(
    val show: Show,
    val categoryName: String
)

class DiscoveryViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = application.applicationContext
    private val nowPlayingRepository = NowPlayingRepository()
    private val podcastRepository = PodcastRepository()
    private val themePreferences = ThemePreferences(application)

    // === СЕРВИС ВОСПРОИЗВЕДЕНИЯ ===
    private var playbackService: PlaybackService? = null
    private var serviceBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            playbackService = (binder as? PlaybackService.LocalBinder)?.getService()
            serviceBound = true
            bindServiceFlows()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            playbackService = null
            serviceBound = false
        }
    }

    // === ТЕМА ===
    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    // === РАДИО-ПЛЕЕР (проксируется из сервиса) ===
    private val _nowPlayingInfo = MutableStateFlow<NowPlayingInfo?>(null)
    val nowPlayingInfo: StateFlow<NowPlayingInfo?> = _nowPlayingInfo.asStateFlow()

    // Фото/логотип текущего исполнителя (из Deezer)
    private val _artistImageUrl = MutableStateFlow<String?>(null)
    val artistImageUrl: StateFlow<String?> = _artistImageUrl.asStateFlow()

    // Текущий артист, для которого искали фото
    private val _artistImageArtist = MutableStateFlow<String?>(null)

    // Кэш: имя артиста -> URL фото (чтобы не дёргать Deezer для одного и того же артиста)
    private val artistImageCache = mutableMapOf<String, String?>()

    private val _playerState = MutableStateFlow(PlayerPlaybackState.IDLE)
    val playerState: StateFlow<PlayerPlaybackState> = _playerState.asStateFlow()

    private val _playerVolume = MutableStateFlow(0.5f)
    val playerVolume: StateFlow<Float> = _playerVolume.asStateFlow()

    // === ПОДКАСТЫ (проксируется из сервиса) ===
    private val _podcastState = MutableStateFlow<PodcastLoadState>(PodcastLoadState.Idle)
    val podcastState: StateFlow<PodcastLoadState> = _podcastState.asStateFlow()

    private val _podcastIsPlaying = MutableStateFlow(false)
    val podcastIsPlaying: StateFlow<Boolean> = _podcastIsPlaying.asStateFlow()

    private val _currentPodcastTitle = MutableStateFlow<String?>(null)
    val currentPodcastTitle: StateFlow<String?> = _currentPodcastTitle.asStateFlow()

    private val _podcastIsLoading = MutableStateFlow(false)
    val podcastIsLoading: StateFlow<Boolean> = _podcastIsLoading.asStateFlow()

    private val _podcastCurrentPosition = MutableStateFlow(0)
    val podcastCurrentPosition: StateFlow<Int> = _podcastCurrentPosition.asStateFlow()

    private val _podcastDuration = MutableStateFlow(0)
    val podcastDuration: StateFlow<Int> = _podcastDuration.asStateFlow()

    // === НАВИГАЦИЯ ПО ПОДКАСТАМ ===
    private var currentPlaylist: List<Show> = emptyList()
    private var currentPlaylistIndex: Int = -1

    init {
        // Загружаем сохранённый режим темы из DataStore
        viewModelScope.launch {
            themePreferences.themeMode.collect { mode ->
                _themeMode.value = mode
            }
        }

        loadPodcasts()
        fetchAndDisplayNowPlaying()

        // Привязываемся к сервису воспроизведения
        bindPlaybackService()
    }

    private fun bindPlaybackService() {
        val intent = Intent(appContext, PlaybackService::class.java)
        appContext.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun unbindPlaybackService() {
        if (serviceBound) {
            appContext.unbindService(serviceConnection)
            serviceBound = false
        }
        playbackService = null
    }

    private fun bindServiceFlows() {
        val service = playbackService ?: return

        // Радио
        viewModelScope.launch {
            service.radioState.collect { state ->
                _playerState.value = state
            }
        }
        viewModelScope.launch {
            service.radioVolume.collect { volume ->
                _playerVolume.value = volume
            }
        }

        // Подкасты
        viewModelScope.launch {
            service.podcastIsPlaying.collect { playing ->
                _podcastIsPlaying.value = playing
            }
        }
        viewModelScope.launch {
            service.podcastIsLoading.collect { loading ->
                _podcastIsLoading.value = loading
            }
        }
        viewModelScope.launch {
            service.currentPodcastTitle.collect { title ->
                _currentPodcastTitle.value = title
            }
        }
        viewModelScope.launch {
            service.podcastCurrentPosition.collect { position ->
                _podcastCurrentPosition.value = position
            }
        }
        viewModelScope.launch {
            service.podcastDuration.collect { duration ->
                _podcastDuration.value = duration
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

    // === ПОИСК ПО ПОДКАСТАМ ===

    /**
     * Ищет подкасты по названию подкаста ИЛИ по названию категории (без учёта регистра,
     * по частичному совпадению).
     */
    fun searchPodcasts(query: String): List<SearchResultItem> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()

        val state = _podcastState.value
        if (state !is PodcastLoadState.Success) return emptyList()

        val lowerQ = q.lowercase()
        val results = mutableListOf<SearchResultItem>()

        for (category in state.categories) {
            val catLower = category.name.lowercase()
            for (show in category.shows) {
                val titleLower = show.title.lowercase()
                if (titleLower.contains(lowerQ) || catLower.contains(lowerQ)) {
                    results.add(SearchResultItem(show = show, categoryName = category.name))
                }
            }
        }
        return results
    }

    /** Возвращает плейлист (подкасты категории) для воспроизведения из поиска */
    fun getCategoryPlaylist(categoryName: String): List<Show> {
        val state = _podcastState.value
        if (state !is PodcastLoadState.Success) return emptyList()
        return state.categories
            .firstOrNull { it.name == categoryName }
            ?.shows
            ?: emptyList()
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

                // === ФОТО ИСПОЛНИТЕЛЯ (Deezer) ===
                if (artist.isNotEmpty()) {
                    // Если артист сменился — сбрасываем фото
                    if (_artistImageArtist.value != artist) {
                        _artistImageUrl.value = null
                        _artistImageArtist.value = artist

                        // Ищем в кэше или запрашиваем Deezer
                        val cached = artistImageCache[artist]
                        val imageUrl = if (cached != null) {
                            cached
                        } else {
                            nowPlayingRepository.fetchArtistImage(artist).also {
                                artistImageCache[artist] = it
                            }
                        }

                        withContext(Dispatchers.Main) {
                            _artistImageUrl.value = imageUrl
                            android.util.Log.d("NowPlaying", "Фото артиста: $imageUrl")
                        }
                    }
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

        currentPlaylist = playlist
        currentPlaylistIndex = playlist.indexOfFirst { it.title == show.title }
        if (currentPlaylistIndex == -1) currentPlaylistIndex = 0

        val service = playbackService
        if (service != null) {
            service.playPodcast(show, playlist)
        } else {
            // Сервис ещё не привязан — запускаем через Intent
            val intent = Intent(appContext, PlaybackService::class.java).apply {
                action = PlaybackService.ACTION_PLAY_PODCAST
                putExtra(PlaybackService.EXTRA_SHOW, show)
                putExtra(PlaybackService.EXTRA_PLAYLIST, ArrayList(playlist))
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                appContext.startForegroundService(intent)
            } else {
                appContext.startService(intent)
            }
        }
    }

    fun togglePodcastPlayback(show: Show) {
        if (show.audioUrl.isEmpty()) return
        playbackService?.togglePodcastPlayback(show)
    }

    fun playNextPodcast() {
        playbackService?.playNextPodcast()
    }

    fun playPreviousPodcast() {
        playbackService?.playPreviousPodcast()
    }

    fun seekPodcast(position: Int) {
        playbackService?.seekPodcast(position)
    }

    fun isLastPodcast(): Boolean {
        return playbackService?.isLastPodcast() ?: (currentPlaylist.isNotEmpty() && currentPlaylistIndex == currentPlaylist.size - 1)
    }

    fun isFirstPodcast(): Boolean {
        return playbackService?.isFirstPodcast() ?: (currentPlaylistIndex <= 0)
    }

    // === УПРАВЛЕНИЕ РАДИО ===
    fun togglePlay() {
        playbackService?.toggleRadio()
    }

    fun setVolume(volume: Float) {
        playbackService?.setRadioVolume(volume)
    }

    // === УПРАВЛЕНИЕ ТЕМОЙ ===
    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        viewModelScope.launch {
            themePreferences.setThemeMode(mode)
        }
    }

    override fun onCleared() {
        super.onCleared()
        // НЕ останавливаем сервис — музыка должна продолжать играть в фоне
        unbindPlaybackService()
    }
}
