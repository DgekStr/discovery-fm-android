package com.example.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.model.Show
import ru.discoveryfm.player.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PodcastPlayerScreen(
    show: Show,
    categoryName: String = "",
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
    onSearchClick: () -> Unit = {},
    onBack: () -> Unit
) {
    val currentTime = formatTime(currentPosition)
    val totalTime = formatTime(duration)
    val progress = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f

    // === ДИАЛОГ С ОПИСАНИЕМ ===
    var showDescriptionDialog by remember { mutableStateOf(false) }

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

    // Подробные данные URL не логируются в release-сборке
    if (ru.discoveryfm.player.BuildConfig.DEBUG) {
        android.util.Log.d("PodcastPlayer", "Обложка загружена для: ${show.title}")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = show.cleanTitle,
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
                actions = {
                    IconButton(onClick = onSearchClick) {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = "Поиск",
                            tint = Color.White
                        )
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
            // === НАЗВАНИЕ КАТЕГОРИИ (над картинкой) ===
            if (categoryName.isNotEmpty()) {
                Text(
                    text = categoryName.uppercase(),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    letterSpacing = 2.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            // === ОБЛОЖКА ПОДКАСТА (кликабельная, с бейджем «i» для описания) ===
            Box(
                modifier = Modifier
                    .size(220.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(enabled = show.description.isNotEmpty()) {
                        showDescriptionDialog = true
                    }
            ) {
                if (imageUrl.isNotEmpty()) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = "Обложка АУДИОframes",
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)),
                        contentScale = ContentScale.Crop,
                        placeholder = painterResource(id = R.drawable.logo),
                        error = painterResource(id = R.drawable.logo),
                        onError = {
                            if (ru.discoveryfm.player.BuildConfig.DEBUG) {
                                android.util.Log.e("PodcastPlayer", "Ошибка загрузки картинки")
                            }
                        }
                    )
                } else {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
                        modifier = Modifier.fillMaxSize()
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

                // === БЕЙДЖ «i» (если есть описание) ===
                if (show.description.isNotEmpty()) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.55f))
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = "Описание",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = show.cleanTitle,
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

    // === ДИАЛОГ С ОПИСАНИЕМ ПОДКАСТА ===
    if (showDescriptionDialog) {
        Dialog(
            onDismissRequest = { showDescriptionDialog = false },
            properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Заголовок
                    Text(
                        text = show.cleanTitle,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Категория
                    if (categoryName.isNotEmpty()) {
                        Text(
                            text = categoryName.uppercase(),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.5.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    } else {
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // Описание с красивым выравниванием и прокруткой
                    Text(
                        text = show.description,
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Justify,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)      // сначала ограничиваем высоту
                            .verticalScroll(rememberScrollState()) // затем скроллим внутри
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Кнопка закрыть
                    Button(
                        onClick = { showDescriptionDialog = false },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Закрыть",
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
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