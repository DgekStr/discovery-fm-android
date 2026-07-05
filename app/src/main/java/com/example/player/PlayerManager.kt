package com.example.player

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException

class PlayerManager(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _state = MutableStateFlow(PlayerPlaybackState.IDLE)
    val state: StateFlow<PlayerPlaybackState> = _state.asStateFlow()

    private val _volume = MutableStateFlow(0.5f)
    val volume: StateFlow<Float> = _volume.asStateFlow()

    // ИСПРАВЛЕННЫЙ URL — используем 128 кбит/с для стабильности
    private val streamUrl = "https://stream.discoveryfm.ru/discovery128.mp3"

    init {
        setVolume(_volume.value)
    }

    fun play() {
        if (mediaPlayer == null) {
            preparePlayer()
        } else {
            mediaPlayer?.let {
                if (!it.isPlaying) {
                    it.start()
                    _state.value = PlayerPlaybackState.PLAYING
                }
            }
        }
    }

    private fun preparePlayer() {
        _state.value = PlayerPlaybackState.PREPARING
        mediaPlayer = MediaPlayer().apply {
            setAudioStreamType(AudioManager.STREAM_MUSIC)
            setOnPreparedListener {
                _state.value = PlayerPlaybackState.PLAYING
                it.start()
            }
            setOnErrorListener { _, what, extra ->
                _state.value = PlayerPlaybackState.ERROR
                true
            }
            setOnCompletionListener {
                _state.value = PlayerPlaybackState.IDLE
            }
            try {
                setDataSource(streamUrl)
                prepareAsync()
            } catch (e: IOException) {
                _state.value = PlayerPlaybackState.ERROR
            }
        }
    }

    fun pause() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                _state.value = PlayerPlaybackState.PAUSED
            }
        }
    }

    fun setVolume(volume: Float) {
        val normalizedVolume = volume.coerceIn(0f, 1f)
        _volume.value = normalizedVolume
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val newVolume = (normalizedVolume * maxVolume).toInt()
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0)
    }

    fun getVolume(): Float {
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        return if (maxVolume > 0) currentVolume.toFloat() / maxVolume else 0f
    }

    fun getCurrentState(): PlayerPlaybackState = _state.value

    fun release() {
        mediaPlayer?.release()
        mediaPlayer = null
        _state.value = PlayerPlaybackState.IDLE
    }
}