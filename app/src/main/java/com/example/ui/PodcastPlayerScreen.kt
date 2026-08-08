package com.example.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.Show
import ru.discoveryfm.player.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PodcastPlayerScreen(
    show: Show,
    isPlaying: Boolean,
    isLoading: Boolean,
    currentPosition: Int,
    duration: Int,
    isFirst: Boolean,
    isLast: Boolean,
    onPlayPauseToggle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Int) -> Unit,
    onBack: () -> Unit
) {
    val currentTime = formatTime(currentPosition)
    val totalTime = formatTime(duration)
    val progress = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f

    // === ИСПРАВЛЕНИЕ: ФОРМИРУЕМ URL КАРТИНКИ ===
    val imageUrl = if (show.imageUrl.isNotEmpty()) {
        val cleanUrl = show.imageUrl
            .replace("\\/", "/")
            .trim()

        // Если URL уже полный - оставляем как есть
        if (cleanUrl.startsWith("http://") || cleanUrl.startsWith("https://")) {
            cleanUrl
        } else {
            // Убираем лишние слеши в начале
            val path = cleanUrl.trimStart('/')
            "https://discoveryfm.ru/$path"
        }
    } else {
        ""
    }

    // Отладка - проверяем URL картинки
    android.util.Log.d("PodcastPlayer", "========================================")
    android.util.Log.d("PodcastPlayer", "🎵 Подкаст: ${show.title}")
    android.util.Log.d("PodcastPlayer", "🖼️ show.imageUrl: '${show.imageUrl}'")
    android.util.Log.d("PodcastPlayer", "🖼️ final imageUrl: '$imageUrl'")
    android.util.Log.d("PodcastPlayer", "========================================")

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = show.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 16.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        containerColor = Color(0xFF0F172A)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // === ОБЛОЖКА ПОДКАСТА ===
            if (imageUrl.isNotEmpty()) {
                // Если есть обложка - показываем её
                AsyncImage(
                    model = imageUrl,
                    contentDescription = "Обложка подкаста",
                    modifier = Modifier
                        .size(220.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)),
                    contentScale = ContentScale.Crop,
                    placeholder = painterResource(id = R.drawable.logo),
                    error = painterResource(id = R.drawable.logo),
                    onError = {
                        android.util.Log.e("PodcastPlayer", "❌ Ошибка загрузки картинки: $imageUrl")
                    }
                )
            } else {
                // Если обложки нет - показываем заглушку с инициалами
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
                    modifier = Modifier.size(220.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = show.title.take(2).uppercase(),
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = show.title,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = show.description,
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(32.dp))

            // === ПРОГРЕСС-БАР ===
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (duration > 0) currentTime else "00:00",
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                    Text(
                        text = if (duration > 0) totalTime else "--:--",
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Slider(
                    value = if (duration > 0) progress else 0f,
                    onValueChange = { newProgress ->
                        if (duration > 0) {
                            val newPosition = (newProgress * duration).toInt()
                            onSeek(newPosition)
                        }
                    },
                    enabled = duration > 0,
                    colors = SliderDefaults.colors(
                        activeTrackColor = Color(0xFF0D9488),
                        inactiveTrackColor = Color.White.copy(alpha = 0.24f),
                        thumbColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("podcast_progress_slider")
                )

                if (isLoading) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp),
                        color = Color(0xFF0D9488),
                        trackColor = Color.White.copy(alpha = 0.1f)
                    )
                }

                Text(
                    text = if (duration > 0) "${(progress * 100).toInt()}%" else "0%",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.align(Alignment.End)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // === КНОПКИ УПРАВЛЕНИЯ ===
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(
                    onClick = onPrevious,
                    enabled = !isFirst,
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipPrevious,
                        contentDescription = "Предыдущий",
                        tint = if (isFirst) Color.White.copy(alpha = 0.3f) else Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(76.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.fillMaxSize(),
                            strokeWidth = 3.dp,
                            color = Color(0xFF0D9488)
                        )
                    }

                    FloatingActionButton(
                        onClick = onPlayPauseToggle,
                        shape = CircleShape,
                        containerColor = if (isPlaying) Color.White else Color(0xFF0D9488),
                        contentColor = if (isPlaying) Color.Black else Color.White,
                        modifier = Modifier
                            .size(64.dp)
                            .testTag("podcast_play_pause_button")
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (isPlaying) "Пауза" else "Воспроизвести",
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                IconButton(
                    onClick = onNext,
                    enabled = !isLast,
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = "Следующий",
                        tint = if (isLast) Color.White.copy(alpha = 0.3f) else Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }
}

private fun formatTime(position: Int): String {
    val seconds = position / 1000
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return String.format("%d:%02d", minutes, remainingSeconds)
}