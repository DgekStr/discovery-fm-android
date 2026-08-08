package com.example.ui

import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.discoveryfm.player.R
import com.example.model.Category
import com.example.model.Show
import com.example.player.PlayerPlaybackState
import com.example.viewmodel.DiscoveryViewModel
import com.example.viewmodel.PodcastLoadState
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoveryApp(
    viewModel: DiscoveryViewModel = viewModel()
) {
    val playerState by viewModel.playerState.collectAsState()
    val volume by viewModel.playerVolume.collectAsState()
    val podcastState by viewModel.podcastState.collectAsState()
    val nowPlayingInfo by viewModel.nowPlayingInfo.collectAsState()
    val podcastIsPlaying by viewModel.podcastIsPlaying.collectAsState()
    val podcastIsLoading by viewModel.podcastIsLoading.collectAsState()
    val currentPodcastTitle by viewModel.currentPodcastTitle.collectAsState()
    val podcastCurrentPosition by viewModel.podcastCurrentPosition.collectAsState()
    val podcastDuration by viewModel.podcastDuration.collectAsState()

    var selectedCategory by remember { mutableStateOf<Category?>(null) }
    var selectedPodcast by remember { mutableStateOf<Show?>(null) }

    LaunchedEffect(Unit) {
        viewModel.loadPodcasts()
        viewModel.fetchAndDisplayNowPlaying()
    }

    LaunchedEffect(playerState) {
        if (playerState == PlayerPlaybackState.PLAYING) {
            viewModel.fetchAndDisplayNowPlaying()
            while (isActive) {
                delay(10000)
                viewModel.fetchAndDisplayNowPlaying()
            }
        }
    }

    // === ЭКРАН ПЛЕЕРА ПОДКАСТА ===
    if (selectedPodcast != null) {
        val playlist = selectedCategory?.shows ?: emptyList()
        val currentIndex = playlist.indexOfFirst { it.title == selectedPodcast!!.title }

        PodcastPlayerScreen(
            show = selectedPodcast!!,
            isPlaying = podcastIsPlaying && currentPodcastTitle == selectedPodcast!!.title,
            isLoading = podcastIsLoading,
            currentPosition = podcastCurrentPosition,
            duration = podcastDuration,
            isFirst = currentIndex <= 0,
            isLast = currentIndex >= playlist.size - 1,
            onPlayPauseToggle = {
                viewModel.togglePodcastPlayback(selectedPodcast!!)
            },
            onNext = {
                val nextIndex = currentIndex + 1
                if (nextIndex < playlist.size) {
                    val nextShow = playlist[nextIndex]
                    selectedPodcast = nextShow
                    viewModel.playPodcast(nextShow, playlist)
                }
            },
            onPrevious = {
                val prevIndex = currentIndex - 1
                if (prevIndex >= 0) {
                    val prevShow = playlist[prevIndex]
                    selectedPodcast = prevShow
                    viewModel.playPodcast(prevShow, playlist)
                }
            },
            onSeek = { position ->
                viewModel.seekPodcast(position)
            },
            onBack = {
                selectedPodcast = null
            }
        )
        return
    }

    // === ЭКРАН КАТЕГОРИИ ===
    if (selectedCategory != null) {
        CategoryScreen(
            category = selectedCategory!!,
            playerState = playerState,
            podcastIsPlaying = podcastIsPlaying,
            podcastIsLoading = podcastIsLoading,
            currentPodcastTitle = currentPodcastTitle,
            currentPosition = podcastCurrentPosition,
            duration = podcastDuration,
            onPlayPauseToggle = { show ->
                viewModel.togglePodcastPlayback(show)
            },
            onPodcastClick = { show ->
                val playlist = selectedCategory?.shows ?: emptyList()

                // Логируем для отладки
                android.util.Log.d("DiscoveryApp", "📱 Клик по подкасту: ${show.title}")
                android.util.Log.d("DiscoveryApp", "🖼️ imageUrl: ${show.imageUrl}")

                // Если у подкаста нет картинки, пробуем взять из категории
                var showWithImage = show
                if (show.imageUrl.isEmpty() && selectedCategory?.imageUrl?.isNotEmpty() == true) {
                    showWithImage = show.copy(imageUrl = selectedCategory!!.imageUrl)
                    android.util.Log.d("DiscoveryApp", "🔄 Используем картинку категории: ${showWithImage.imageUrl}")
                }

                viewModel.playPodcast(showWithImage, playlist)
                selectedPodcast = showWithImage
            },
            onBack = { selectedCategory = null }
        )
        return
    }

    // === ГЛАВНЫЙ ЭКРАН ===
    Scaffold(
        topBar = {
            YouTubeStyleHeader(
                onRefreshClick = {
                    viewModel.loadPodcasts()
                    viewModel.fetchAndDisplayNowPlaying()
                },
                isLoading = podcastState is PodcastLoadState.Loading
            )
        },
        contentWindowInsets = WindowInsets.systemBars,
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("main_scrollable_container"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                RadioPlayerCard(
                    playerState = playerState,
                    volume = volume,
                    nowPlayingInfo = nowPlayingInfo,
                    onPlayPauseToggle = { viewModel.togglePlay() },
                    onVolumeChange = { viewModel.setVolume(it) }
                )
            }

            item {
                when (val state = podcastState) {
                    is PodcastLoadState.Loading -> {
                        StatusCard(
                            icon = null,
                            text = "Загрузка категорий...",
                            isLoading = true
                        )
                    }
                    is PodcastLoadState.Success -> {
                        if (state.isFallback) {
                            StatusCard(
                                icon = Icons.Rounded.WifiOff,
                                text = state.message,
                                isWarning = true
                            )
                        } else {
                            StatusCard(
                                icon = Icons.Rounded.Wifi,
                                text = state.message,
                                isWarning = false
                            )
                        }
                    }
                    is PodcastLoadState.Error -> {
                        StatusCard(
                            icon = Icons.Rounded.Error,
                            text = state.message,
                            isError = true
                        )
                    }
                    else -> {}
                }
            }

            item {
                Text(
                    text = "📂 Категории подкастов",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            when (val state = podcastState) {
                is PodcastLoadState.Loading -> {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator()
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Загрузка...",
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }
                is PodcastLoadState.Success -> {
                    if (state.categories.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Нет доступных категорий",
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                                )
                            }
                        }
                    } else {
                        items(state.categories) { category ->
                            CategoryCard(
                                category = category,
                                onCategoryClick = {
                                    selectedCategory = category
                                }
                            )
                        }
                    }
                }
                is PodcastLoadState.Error -> {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Error,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = state.message,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }
                else -> {}
            }
        }
    }
}

// ==================== ВСЕ ФУНКЦИИ КОМПОНЕНТОВ ====================

@Composable
fun StatusCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    text: String,
    isLoading: Boolean = false,
    isWarning: Boolean = false,
    isError: Boolean = false
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = when {
                isError -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                isWarning -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                else -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
            }
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                }
                icon != null -> {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = when {
                            isError -> MaterialTheme.colorScheme.error
                            isWarning -> MaterialTheme.colorScheme.secondary
                            else -> MaterialTheme.colorScheme.primary
                        },
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                }
            }
            Text(
                text = text,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = when {
                    isError -> MaterialTheme.colorScheme.onErrorContainer
                    isWarning -> MaterialTheme.colorScheme.onSecondaryContainer
                    else -> MaterialTheme.colorScheme.onPrimaryContainer
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun YouTubeStyleHeader(
    onRefreshClick: () -> Unit,
    isLoading: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primary)
            .padding(
                top = 32.dp,
                start = 12.dp,
                end = 12.dp,
                bottom = 10.dp
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Image(
                painter = painterResource(id = R.drawable.logo),
                contentDescription = "Логотип РАДИО ОТКРЫТИЕ",
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "РАДИО ОТКРЫТИЕ",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 18.sp
                )
                Text(
                    text = "Радио и подкасты",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 12.sp
                )
            }
        }

        IconButton(
            onClick = onRefreshClick,
            modifier = Modifier
                .testTag("refresh_button")
                .minimumInteractiveComponentSize(),
            enabled = !isLoading
        ) {
            Icon(
                imageVector = Icons.Rounded.Refresh,
                contentDescription = "Обновить",
                tint = Color.White
            )
        }
    }
}

@Composable
fun RadioPlayerCard(
    playerState: PlayerPlaybackState,
    volume: Float,
    nowPlayingInfo: com.example.data.NowPlayingInfo?,
    onPlayPauseToggle: () -> Unit,
    onVolumeChange: (Float) -> Unit
) {
    val darkBlue = Color(0xFF0F172A)
    val royalPurple = Color(0xFF3B0764)
    val neonTeal = Color(0xFF0D9488)

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = darkBlue),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("player_section_card")
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        colors = listOf(royalPurple.copy(alpha = 0.85f), darkBlue)
                    )
                )
                .padding(24.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    color = when (playerState) {
                        PlayerPlaybackState.PLAYING -> Color(0xFFDC2626)
                        PlayerPlaybackState.PREPARING -> Color(0xFFD97706)
                        else -> Color(0xFF475569)
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    Text(
                        text = when (playerState) {
                            PlayerPlaybackState.PLAYING -> "●  В ЭФИРЕ"
                            PlayerPlaybackState.PREPARING -> "СОЕДИНЕНИЕ..."
                            PlayerPlaybackState.ERROR -> "ОШИБКА"
                            else -> "ОФЛАЙН"
                        },
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                if (nowPlayingInfo != null && nowPlayingInfo.artist.isNotEmpty()) {
                    Text(
                        text = nowPlayingInfo.artist,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = nowPlayingInfo.title,
                        fontSize = 16.sp,
                        color = Color.White.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Text(
                        text = "РАДИО ОТКРЫТИЕ",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Радио, которое вдохновляет",
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.65f),
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                WaveformVisualizer(isPlaying = playerState == PlayerPlaybackState.PLAYING)

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(76.dp)
                    ) {
                        if (playerState == PlayerPlaybackState.PREPARING) {
                            CircularProgressIndicator(
                                modifier = Modifier.fillMaxSize(),
                                strokeWidth = 3.dp,
                                color = neonTeal
                            )
                        }

                        FloatingActionButton(
                            onClick = onPlayPauseToggle,
                            shape = CircleShape,
                            containerColor = if (playerState == PlayerPlaybackState.PLAYING) Color.White else neonTeal,
                            contentColor = if (playerState == PlayerPlaybackState.PLAYING) Color.Black else Color.White,
                            modifier = Modifier
                                .size(64.dp)
                                .testTag("play_pause_button")
                                .minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                imageVector = if (playerState == PlayerPlaybackState.PLAYING) {
                                    Icons.Rounded.Pause
                                } else {
                                    Icons.Rounded.PlayArrow
                                },
                                contentDescription = if (playerState == PlayerPlaybackState.PLAYING) "Пауза" else "Воспроизвести",
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.VolumeDown,
                        contentDescription = "Тише",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp)
                    )

                    Slider(
                        value = volume,
                        onValueChange = onVolumeChange,
                        valueRange = 0f..1f,
                        colors = SliderDefaults.colors(
                            activeTrackColor = neonTeal,
                            inactiveTrackColor = Color.White.copy(alpha = 0.24f),
                            thumbColor = Color.White
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp)
                            .testTag("volume_slider")
                    )

                    Icon(
                        imageVector = Icons.Rounded.VolumeUp,
                        contentDescription = "Громче",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun WaveformVisualizer(isPlaying: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveform")

    val heights = listOf(
        infiniteTransition.animateFloat(
            initialValue = 12f,
            targetValue = if (isPlaying) 48f else 12f,
            animationSpec = infiniteRepeatable(
                animation = tween(450, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "h1"
        ),
        infiniteTransition.animateFloat(
            initialValue = 16f,
            targetValue = if (isPlaying) 60f else 16f,
            animationSpec = infiniteRepeatable(
                animation = tween(350, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "h2"
        ),
        infiniteTransition.animateFloat(
            initialValue = 8f,
            targetValue = if (isPlaying) 36f else 8f,
            animationSpec = infiniteRepeatable(
                animation = tween(550, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "h3"
        ),
        infiniteTransition.animateFloat(
            initialValue = 14f,
            targetValue = if (isPlaying) 52f else 14f,
            animationSpec = infiniteRepeatable(
                animation = tween(400, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "h4"
        ),
        infiniteTransition.animateFloat(
            initialValue = 10f,
            targetValue = if (isPlaying) 40f else 10f,
            animationSpec = infiniteRepeatable(
                animation = tween(480, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "h5"
        )
    )

    Row(
        modifier = Modifier
            .height(64.dp)
            .fillMaxWidth(0.5f),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val color = Color(0xFF0D9488)
        heights.forEach { height ->
            Box(
                modifier = Modifier
                    .size(6.dp, height.value.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(color)
            )
        }
    }
}

@Composable
fun CategoryCard(
    category: Category,
    onCategoryClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = getCategoryColor(category.name)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .clickable { onCategoryClick() }
            .testTag("category_card_${category.name.replace(" ", "_")}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // === ОТОБРАЖЕНИЕ КАРТИНКИ КАТЕГОРИИ ===
            if (category.imageUrl.isNotEmpty()) {
                android.util.Log.d("CategoryCard", "🖼️ Загрузка картинки: ${category.imageUrl}")
                AsyncImage(
                    model = category.imageUrl,  // <-- УЖЕ ПОЛНЫЙ URL
                    contentDescription = category.name,
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop,
                    placeholder = painterResource(id = R.drawable.logo),
                    error = painterResource(id = R.drawable.logo),
                    onError = {
                        android.util.Log.e("CategoryCard", "❌ Ошибка загрузки: ${category.imageUrl}")
                    }
                )
                Spacer(modifier = Modifier.width(16.dp))
            } else {
                // Если нет картинки - показываем иконку
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Folder,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
            }

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = category.name,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${category.shows.size} подкастов",
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
fun getCategoryColor(categoryName: String): Color {
    return when {
        categoryName.contains("Итуруп") -> Color(0xFF1A237E)
        categoryName.contains("NativeSpeaker") -> Color(0xFF4A148C)
        categoryName.contains("OLD Gold") -> Color(0xFFE65100)
        categoryName.contains("Short School") -> Color(0xFF004D40)
        categoryName.contains("Нейроакустика") -> Color(0xFF0D47A1)
        categoryName.contains("АльбомCheck") -> Color(0xFFB71C1C)
        categoryName.contains("Psychology Notes") -> Color(0xFF2E7D32)
        categoryName.contains("Части Света") -> Color(0xFFE65100)
        categoryName.contains("infoПОВОД") -> Color(0xFF880E4F)
        categoryName.contains("Дедовы Пласты") -> Color(0xFF455A64)
        categoryName.contains("Без категории") -> Color(0xFF78909C)
        else -> Color(0xFF455A64)
    }
}