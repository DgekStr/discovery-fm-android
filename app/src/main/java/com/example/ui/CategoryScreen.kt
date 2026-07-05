package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Category
import com.example.model.Show
import com.example.player.PlayerPlaybackState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryScreen(
    category: Category,
    playerState: PlayerPlaybackState,
    podcastIsPlaying: Boolean,
    podcastIsLoading: Boolean,
    currentPodcastTitle: String?,
    currentPosition: Int = 0,
    duration: Int = 0,
    onPlayPauseToggle: (Show) -> Unit,
    onPodcastClick: (Show) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = category.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Rounded.ArrowBack,
                            contentDescription = "Назад"
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
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlaylistPlay,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "${category.shows.size} подкастов",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            items(
                items = category.shows,
                key = { show -> show.title }
            ) { show ->
                PodcastItemCard(
                    show = show,
                    isPlaying = podcastIsPlaying && currentPodcastTitle == show.title,
                    isLoading = podcastIsLoading,
                    onPlayPauseToggle = { onPlayPauseToggle(show) },
                    onClick = {
                        onPodcastClick(show)
                    }
                )
            }
        }
    }
}

@Composable
fun PodcastItemCard(
    show: Show,
    isPlaying: Boolean,
    isLoading: Boolean,
    onPlayPauseToggle: () -> Unit,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isPlaying)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
            else
                MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isPlaying) 4.dp else 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Иконка подкаста
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isPlaying)
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                        else
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isLoading && isPlaying) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Podcasts else Icons.Rounded.Podcasts,
                        contentDescription = null,
                        tint = if (isPlaying)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Информация о подкасте
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = show.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isPlaying)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (show.description.isNotEmpty()) {
                    Text(
                        text = show.description,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Статус воспроизведения
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isPlaying) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = "Играет",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Воспроизводится",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    } else if (isLoading) {
                        Text(
                            text = "Загрузка...",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                            fontWeight = FontWeight.Medium
                        )
                    } else {
                        Text(
                            text = "▶ Нажмите, чтобы открыть",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}