package com.example.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import ru.discoveryfm.player.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Экран загрузки при старте приложения.
 * Показывает логотип по центру с лёгким вращением,
 * прогресс-бар Material 3 и процент загрузки — всё в фирменном стиле.
 */
@Composable
fun LoadingScreen(
    progress: Int,
    modifier: Modifier = Modifier,
) {
    // Бесконечное плавное вращение логотипа
    val rotation by rememberInfiniteTransition(label = "logo_rotation").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    // Принудительно держим прогресс в 0..100
    val safeProgress = progress.coerceIn(0, 100)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // === ЛОГОТИП (вращается) ===
            Image(
                painter = painterResource(R.drawable.logo),
                contentDescription = "Логотип Discovery FM",
                modifier = Modifier
                    .size(140.dp)
                    .clip(CircleShape)
                    .rotate(rotation)
            )

            Spacer(modifier = Modifier.height(28.dp))

            // === Название ===
            Text(
                text = "ДИСКОВЕРИ FM",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Радио и АУДИОframes",
                fontSize = 13.sp,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(36.dp))

            // === Прогресс-бар ===
            LinearProgressIndicator(
                progress = { safeProgress / 100f },
                modifier = Modifier
                    .width(220.dp)
                    .height(6.dp)
                    .progressSemantics(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            // === Процент загрузки ===
            Text(
                text = "$safeProgress%",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Загрузка АУДИОframes…",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun LoadingScreenPreview() {
    LoadingScreen(progress = 42)
}