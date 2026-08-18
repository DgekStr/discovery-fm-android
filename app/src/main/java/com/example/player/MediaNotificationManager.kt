package com.example.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import androidx.media.session.MediaButtonReceiver
import androidx.core.content.ContextCompat
import com.example.MainActivity
import ru.discoveryfm.player.R
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat

/**
 * Строит MediaStyle-уведомление для шторки и экрана блокировки.
 * Показывает название, обложку и кнопки управления.
 */
class MediaNotificationManager(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "playback_channel"
        const val NOTIFICATION_ID = 1001

        // Канал для постоянного статусного уведомления (иконка в статус-баре)
        const val STATUS_CHANNEL_ID = "status_channel"
        const val STATUS_NOTIFICATION_ID = 1002
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createChannel()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Воспроизведение",
                // Важность DEFAULT — чтобы значок приложения
                // отображался в статус-баре рядом с часами
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Управление воспроизведением музыки и АУДИОframes"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)

            // Канал для постоянного статусного уведомления
            val statusChannel = NotificationChannel(
                STATUS_CHANNEL_ID,
                "Статус приложения",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Показывает значок приложения в статус-баре"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(statusChannel)
        }
    }

    fun buildNotification(
        isPlaying: Boolean,
        title: String,
        subtitle: String?,
        largeIcon: Bitmap?,
        mediaSessionToken: MediaSessionCompat.Token,
        showPlayPause: Boolean = true,
        showNext: Boolean = false,
        showPrevious: Boolean = false,
        showStop: Boolean = true
    ): Notification {
        val playPauseAction = if (isPlaying) {
            NotificationCompat.Action(
                R.drawable.ic_pause,
                "Пауза",
                MediaButtonReceiver.buildMediaButtonPendingIntent(
                    context,
                    PlaybackStateCompat.ACTION_PLAY_PAUSE
                )
            )
        } else {
            NotificationCompat.Action(
                R.drawable.ic_play,
                "Воспроизвести",
                MediaButtonReceiver.buildMediaButtonPendingIntent(
                    context,
                    PlaybackStateCompat.ACTION_PLAY_PAUSE
                )
            )
        }

        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = MediaButtonReceiver.buildMediaButtonPendingIntent(
            context,
            PlaybackStateCompat.ACTION_STOP
        )

        // Large icon: если не передана обложка — используем логотип приложения
        val effectiveLargeIcon = largeIcon ?: loadAppLogo()

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_play)
            .setContentTitle(title)
            .setContentText(subtitle ?: "Discovery FM")
            .setContentIntent(contentIntent)
            .setDeleteIntent(stopIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setOngoing(isPlaying)
            .setColor(ContextCompat.getColor(context, R.color.ic_launcher_background))
            .setLargeIcon(effectiveLargeIcon)
            .setStyle(
                MediaStyle()
                    .setMediaSession(mediaSessionToken)
                    .setShowActionsInCompactView(0)
                    .setShowCancelButton(true)
                    .setCancelButtonIntent(stopIntent)
            )
            .addAction(
                if (showPrevious) {
                    NotificationCompat.Action(
                        R.drawable.ic_skip_previous,
                        "Предыдущий",
                        MediaButtonReceiver.buildMediaButtonPendingIntent(
                            context,
                            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                        )
                    )
                } else {
                    null
                }
            )
            .addAction(playPauseAction)
            .addAction(
                if (showNext) {
                    NotificationCompat.Action(
                        R.drawable.ic_skip_next,
                        "Следующий",
                        MediaButtonReceiver.buildMediaButtonPendingIntent(
                            context,
                            PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                        )
                    )
                } else {
                    null
                }
            )
            .build()
    }

    /**
     * Загружает логотип приложения из ресурсов как Bitmap
     * для отображения в уведомлении (large icon).
     */
    private fun loadAppLogo(): Bitmap? {
        return try {
            val drawable = context.resources.getDrawable(
                R.drawable.logo,
                context.theme
            )
            if (drawable is android.graphics.drawable.BitmapDrawable) {
                drawable.bitmap
            } else {
                val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 512
                val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 512
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bitmap)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                bitmap
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Показывает постоянное статусное уведомление,
     * которое отображает значок приложения в статус-баре.
     */
    fun showStatusNotification(title: String) {
        val contentIntent = PendingIntent.getActivity(
            context,
            1,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, STATUS_CHANNEL_ID)
            .setSmallIcon(R.drawable.logo)
            .setContentTitle(title)
            .setContentText("Discovery FM слушает вас")
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .build()

        notificationManager.notify(STATUS_NOTIFICATION_ID, notification)
    }

    /**
     * Скрывает статусное уведомление.
     */
    fun hideStatusNotification() {
        notificationManager.cancel(STATUS_NOTIFICATION_ID)
    }
}
