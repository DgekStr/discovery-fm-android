package com.example.player

import android.content.Context
import android.media.MediaPlayer
import ru.discoveryfm.player.BuildConfig
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException

class PodcastPlayerManager(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var updatePositionRunnable: Runnable? = null

    // === СОСТОЯНИЯ ===
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPodcast = MutableStateFlow<String?>(null)
    val currentPodcast: StateFlow<String?> = _currentPodcast.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // НОВЫЕ ПОЛЯ ДЛЯ ПРОГРЕСС-БАРА
    private val _currentPosition = MutableStateFlow(0)
    val currentPosition: StateFlow<Int> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0)
    val duration: StateFlow<Int> = _duration.asStateFlow()

    // === МЕТОДЫ ===
    fun play(audioUrl: String, podcastTitle: String) {
        if (BuildConfig.DEBUG) {
            android.util.Log.d("PodcastPlayer", "Play called: $podcastTitle")
        }

        if (_currentPodcast.value == podcastTitle && _isPlaying.value) {
            pause()
            return
        }

        if (_currentPodcast.value == podcastTitle && !_isPlaying.value && mediaPlayer != null) {
            resume()
            return
        }

        if (audioUrl.isEmpty()) {
            if (BuildConfig.DEBUG) android.util.Log.e("PodcastPlayer", "URL пустой!")
            return
        }

        stopPositionUpdater()
        mediaPlayer?.release()
        mediaPlayer = null

        _isLoading.value = true
        _currentPodcast.value = podcastTitle
        _currentPosition.value = 0
        _duration.value = 0
        _isPlaying.value = false

        try {
            mediaPlayer = MediaPlayer().apply {
                setOnPreparedListener { mp ->
                    if (BuildConfig.DEBUG) android.util.Log.d("PodcastPlayer", "MediaPlayer prepared, duration: ${mp.duration}")
                    _duration.value = mp.duration
                    _isLoading.value = false
                    _isPlaying.value = true
                    mp.start()
                    startPositionUpdater()
                }

                setOnErrorListener { _, what, extra ->
                    if (BuildConfig.DEBUG) android.util.Log.e("PodcastPlayer", "Error: what=$what, extra=$extra")
                    _isLoading.value = false
                    _isPlaying.value = false
                    _currentPodcast.value = null
                    true
                }

                setOnCompletionListener {
                    if (BuildConfig.DEBUG) android.util.Log.d("PodcastPlayer", "Playback completed")
                    _isPlaying.value = false
                    stopPositionUpdater()
                    _currentPosition.value = _duration.value
                }

                setAudioStreamType(android.media.AudioManager.STREAM_MUSIC)

                val finalUrl = if (audioUrl.startsWith("/")) {
                    "https://discoveryfm.ru$audioUrl"
                } else {
                    audioUrl
                }
                if (BuildConfig.DEBUG) android.util.Log.d("PodcastPlayer", "Final URL host: ${android.net.Uri.parse(finalUrl).host}")

                setDataSource(finalUrl)
                prepareAsync()
            }
        } catch (e: IOException) {
            if (BuildConfig.DEBUG) android.util.Log.e("PodcastPlayer", "IOException: ${e.message}")
            handleError()
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) android.util.Log.e("PodcastPlayer", "Exception: ${e.message}")
            handleError()
        }
    }

    fun pause() {
        if (BuildConfig.DEBUG) android.util.Log.d("PodcastPlayer", "Pause called")
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                _isPlaying.value = false
                stopPositionUpdater()
            }
        }
    }

    fun resume() {
        if (BuildConfig.DEBUG) android.util.Log.d("PodcastPlayer", "Resume called")
        mediaPlayer?.let {
            if (!it.isPlaying && _currentPodcast.value != null) {
                it.start()
                _isPlaying.value = true
                startPositionUpdater()
            }
        }
    }

    fun seekTo(position: Int) {
        mediaPlayer?.let {
            val targetPosition = position.coerceIn(0, _duration.value)
            it.seekTo(targetPosition)
            _currentPosition.value = targetPosition
        }
    }

    fun stop() {
        if (BuildConfig.DEBUG) android.util.Log.d("PodcastPlayer", "Stop called")
        stopPositionUpdater()
        mediaPlayer?.apply {
            if (isPlaying) {
                stop()
            }
            release()
        }
        mediaPlayer = null
        _isPlaying.value = false
        _currentPodcast.value = null
        _isLoading.value = false
        _currentPosition.value = 0
        _duration.value = 0
    }

    fun release() {
        if (BuildConfig.DEBUG) android.util.Log.d("PodcastPlayer", "Release called")
        stopPositionUpdater()
        mediaPlayer?.release()
        mediaPlayer = null
        _isPlaying.value = false
        _currentPodcast.value = null
        _isLoading.value = false
        _currentPosition.value = 0
        _duration.value = 0
    }

    private fun handleError() {
        _isLoading.value = false
        _isPlaying.value = false
        _currentPodcast.value = null
        mediaPlayer?.release()
        mediaPlayer = null
    }

    private fun startPositionUpdater() {
        stopPositionUpdater()

        updatePositionRunnable = object : Runnable {
            override fun run() {
                mediaPlayer?.let {
                    if (it.isPlaying) {
                        _currentPosition.value = it.currentPosition
                    }
                }
                if (_isPlaying.value) {
                    mainHandler.postDelayed(this, 500)
                }
            }
        }
        mainHandler.post(updatePositionRunnable!!)
    }

    private fun stopPositionUpdater() {
        updatePositionRunnable?.let {
            mainHandler.removeCallbacks(it)
            updatePositionRunnable = null
        }
    }
}