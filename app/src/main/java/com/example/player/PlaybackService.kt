package com.example.player

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import com.example.model.Show
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Фоновый сервис воспроизведения.
 * Живёт в foreground, пока играет музыка, и переживает закрытие Activity.
 * Объединяет радио (PlayerManager) и подкасты (PodcastPlayerManager).
 */
class PlaybackService : Service() {

    companion object {
        const val ACTION_PLAY_RADIO = "com.example.player.action.PLAY_RADIO"
        const val ACTION_PLAY_PODCAST = "com.example.player.action.PLAY_PODCAST"
        const val ACTION_STOP = "com.example.player.action.STOP"

        // Extras
        const val EXTRA_SHOW = "extra_show"
        const val EXTRA_PLAYLIST = "extra_playlist"
        const val EXTRA_URL = "extra_url"
        const val EXTRA_TITLE = "extra_title"
    }

    private val binder = LocalBinder()

    // === ПЛЕЕРЫ ===
    lateinit var radioPlayer: PlayerManager
        private set
    lateinit var podcastPlayer: PodcastPlayerManager
        private set

    // === MEDIA SESSION ===
    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var notificationManager: MediaNotificationManager

    // === ПУБЛИЧНОЕ СОСТОЯНИЕ ===
    private val _radioState = MutableStateFlow(PlayerPlaybackState.IDLE)
    val radioState: StateFlow<PlayerPlaybackState> = _radioState.asStateFlow()

    private val _radioVolume = MutableStateFlow(0.5f)
    val radioVolume: StateFlow<Float> = _radioVolume.asStateFlow()

    val podcastIsPlaying: StateFlow<Boolean> get() = podcastPlayer.isPlaying
    val podcastIsLoading: StateFlow<Boolean> get() = podcastPlayer.isLoading
    val currentPodcastTitle: StateFlow<String?> get() = podcastPlayer.currentPodcast
    val podcastCurrentPosition: StateFlow<Int> get() = podcastPlayer.currentPosition
    val podcastDuration: StateFlow<Int> get() = podcastPlayer.duration

    // === ПЛЕЙЛИСТ ПОДКАСТОВ (для next/prev в уведомлении) ===
    private var playlist: List<Show> = emptyList()
    private var playlistIndex: Int = -1

    private var isManualPause = false
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        radioPlayer = PlayerManager(this)
        podcastPlayer = PodcastPlayerManager(this)
        notificationManager = MediaNotificationManager(this)

        // Инициализация MediaSession
        mediaSession = MediaSessionCompat(this, "PlaybackService").apply {
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                    MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )
            setCallback(MediaSessionCallback())
            isActive = true
        }

        // Подписка на состояние радио — обновление уведомления
        serviceScope.launch {
            radioPlayer.state.collect { state ->
                _radioState.value = state
                updateNotification()
            }
        }
        serviceScope.launch {
            radioPlayer.volume.collect { volume ->
                _radioVolume.value = volume
            }
        }

        // Автопереход на следующий подкаст при завершении
        serviceScope.launch {
            podcastPlayer.isPlaying.collect { isPlaying ->
                if (isManualPause) return@collect
                if (!isPlaying && podcastPlayer.currentPodcast.value != null &&
                    !podcastPlayer.isLoading.value
                ) {
                    delay(500)
                    if (!podcastPlayer.isPlaying.value &&
                        podcastPlayer.currentPodcast.value != null && !isManualPause
                    ) {
                        playNextPodcast()
                    }
                }
            }
        }

        // Подписка на состояние подкастов — обновление уведомления
        serviceScope.launch {
            podcastPlayer.isPlaying.collect { updateNotification() }
        }
        serviceScope.launch {
            podcastPlayer.currentPodcast.collect { updateNotification() }
        }
    }

    // === BINDER ===
    inner class LocalBinder : Binder() {
        fun getService(): PlaybackService = this@PlaybackService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_RADIO -> {
                startRadio()
            }
            ACTION_PLAY_PODCAST -> {
                val show = intent.getParcelableExtra<Show>(EXTRA_SHOW)
                val list = intent.getParcelableArrayListExtra<Show>(EXTRA_PLAYLIST) ?: emptyList()
                if (show != null) playPodcast(show, list)
            }
            ACTION_STOP -> stopPlayback()
        }
        return START_STICKY
    }

    // === УПРАВЛЕНИЕ РАДИО ===
    fun toggleRadio() {
        if (podcastPlayer.isPlaying.value) {
            isManualPause = true
            podcastPlayer.stop()
        }
        if (radioPlayer.getCurrentState() == PlayerPlaybackState.PLAYING) {
            radioPlayer.pause()
            updateNotification()
        } else {
            radioPlayer.play()
            updateNotification()
        }
    }

    fun startRadio() {
        // Останавливаем подкаст, если играет
        if (podcastPlayer.isPlaying.value) {
            podcastPlayer.stop()
        }
        radioPlayer.play()
        updateNotification()
    }

    fun setRadioVolume(volume: Float) {
        radioPlayer.setVolume(volume)
    }

    // === УПРАВЛЕНИЕ ПОДКАСТАМИ ===
    fun playPodcast(show: Show, list: List<Show>) {
        if (show.audioUrl.isEmpty()) return
        isManualPause = false

        playlist = list
        playlistIndex = list.indexOfFirst { it.title == show.title }
        if (playlistIndex == -1) playlistIndex = 0

        // Останавливаем радио, если играет
        if (radioPlayer.getCurrentState() == PlayerPlaybackState.PLAYING) {
            radioPlayer.pause()
        }

        podcastPlayer.play(show.audioUrl, show.title)
        updateNotification()
    }

    fun togglePodcastPlayback(show: Show) {
        if (show.audioUrl.isEmpty()) return

        if (podcastPlayer.currentPodcast.value == show.title) {
            if (podcastPlayer.isPlaying.value) {
                isManualPause = true
                podcastPlayer.pause()
            } else {
                isManualPause = false
                podcastPlayer.resume()
            }
            updateNotification()
            return
        }

        if (radioPlayer.getCurrentState() == PlayerPlaybackState.PLAYING) {
            radioPlayer.pause()
        }
        isManualPause = false
        podcastPlayer.play(show.audioUrl, show.title)
        updateNotification()
    }

    fun playNextPodcast() {
        if (playlist.isEmpty() || playlistIndex == -1) return
        val nextIndex = playlistIndex + 1
        if (nextIndex < playlist.size) {
            playlistIndex = nextIndex
            val nextShow = playlist[nextIndex]
            isManualPause = false
            podcastPlayer.play(nextShow.audioUrl, nextShow.title)
            updateNotification()
        } else {
            isManualPause = false
            podcastPlayer.stop()
            playlistIndex = -1
            stopForegroundCompat()
        }
    }

    fun playPreviousPodcast() {
        if (playlist.isEmpty() || playlistIndex == -1) return
        val prevIndex = playlistIndex - 1
        if (prevIndex >= 0) {
            playlistIndex = prevIndex
            val prevShow = playlist[prevIndex]
            isManualPause = false
            podcastPlayer.play(prevShow.audioUrl, prevShow.title)
            updateNotification()
        }
    }

    fun seekPodcast(position: Int) {
        podcastPlayer.seekTo(position)
    }

    fun isLastPodcast(): Boolean =
        playlist.isNotEmpty() && playlistIndex == playlist.size - 1

    fun isFirstPodcast(): Boolean = playlistIndex <= 0

    // === ОСТАНОВКА ===
    fun stopPlayback() {
        radioPlayer.pause()
        podcastPlayer.stop()
        stopForegroundCompat()
        stopSelf()
    }

    private fun stopForegroundCompat() {
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    // === УВЕДОМЛЕНИЕ ===
    private fun buildCurrentNotification(): android.app.Notification {
        val podcastPlaying = podcastPlayer.isPlaying.value
        val radioPlaying = radioPlayer.getCurrentState() == PlayerPlaybackState.PLAYING

        return if (podcastPlaying) {
            val title = podcastPlayer.currentPodcast.value ?: "АУДИОframes"
            notificationManager.buildNotification(
                isPlaying = true,
                title = title,
                subtitle = "АУДИОframes • Discovery FM",
                largeIcon = null,
                mediaSessionToken = mediaSession.sessionToken,
                showNext = !isLastPodcast(),
                showPrevious = !isFirstPodcast()
            )
        } else if (radioPlaying) {
            notificationManager.buildNotification(
                isPlaying = true,
                title = "Радио Открытие",
                subtitle = "Слушаем онлайн",
                largeIcon = null,
                mediaSessionToken = mediaSession.sessionToken
            )
        } else {
            notificationManager.buildNotification(
                isPlaying = false,
                title = "Discovery FM",
                subtitle = "Воспроизведение остановлено",
                largeIcon = null,
                mediaSessionToken = mediaSession.sessionToken
            )
        }
    }

    private fun updateNotification() {
        val anyPlaying = podcastPlayer.isPlaying.value ||
            radioPlayer.getCurrentState() == PlayerPlaybackState.PLAYING
        if (anyPlaying) {
            val notification = buildCurrentNotification()
            // Сервис должен быть в foreground, пока играет музыка,
            // чтобы система не убила процесс при закрытии приложения
            startForeground(MediaNotificationManager.NOTIFICATION_ID, notification)
            // Показываем статусное уведомление для иконки в статус-баре
            notificationManager.showStatusNotification("Discovery FM")
        } else {
            stopForegroundCompat()
            notificationManager.hideStatusNotification()
        }
    }

    // === MEDIA SESSION CALLBACK ===
    private inner class MediaSessionCallback : MediaSessionCompat.Callback() {
        override fun onPlay() {
            if (podcastPlayer.currentPodcast.value != null) {
                podcastPlayer.resume()
            } else {
                radioPlayer.play()
            }
            updateNotification()
        }

        override fun onPause() {
            isManualPause = true
            podcastPlayer.pause()
            radioPlayer.pause()
            updateNotification()
        }

        override fun onStop() {
            stopPlayback()
        }

        override fun onSkipToNext() {
            playNextPodcast()
        }

        override fun onSkipToPrevious() {
            playPreviousPodcast()
        }

        override fun onSeekTo(pos: Long) {
            podcastPlayer.seekTo(pos.toInt())
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        mediaSession.run {
            isActive = false
            release()
        }
        radioPlayer.release()
        podcastPlayer.release()
        super.onDestroy()
    }
}
