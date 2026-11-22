@file:OptIn(ExperimentalSharedTransitionApi::class)

package app.kromiv.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kromiv.R
import app.kromiv.core.model.DownloadProgress
import app.kromiv.core.model.DownloadStatus
import app.kromiv.core.model.EnhanceStatus
import app.kromiv.core.model.WallpaperItem
import app.kromiv.core.model.WallpaperMediaType

@Composable
fun WallpaperCard(
    item: WallpaperItem,
    downloadProgress: DownloadProgress?,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    sharedKeyPrefix: String,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDownloadAction: (() -> Unit)? = null,
    onEnhanceClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val favScale by animateFloatAsState(
        targetValue = if (item.isFavorite) 1.15f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "favScale"
    )

    with(sharedTransitionScope) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .bounceClick(onClick = onClick)
                .sharedBounds(
                    sharedContentState = rememberSharedContentState(key = "$sharedKeyPrefix-${item.id}"),
                    animatedVisibilityScope = animatedVisibilityScope
                ),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 2.dp
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(item.aspectRatio)
            ) {
                KromivThumbnailImage(
                    uri = item.thumbnailUri,
                    contentDescription = item.title,
                    modifier = Modifier.fillMaxSize()
                )

                // Top badges row: LIVE / Animated / Format + Favorite button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (item.mediaType == WallpaperMediaType.VIDEO) {
                            BadgePill(
                                text = "LIVE",
                                containerColor = Color(0xCC0D9488),
                                contentColor = Color.White
                            )
                        } else if (item.mediaType == WallpaperMediaType.ANIMATED_IMAGE) {
                            BadgePill(
                                text = item.format.extension.uppercase(),
                                containerColor = Color(0xCC7C3AED),
                                contentColor = Color.White
                            )
                        }
                        if (item.enhanceStatus == EnhanceStatus.RECOMMENDED && onEnhanceClick != null) {
                            BadgePill(
                                text = "Mejorar",
                                containerColor = Color(0xD9F59E0B),
                                contentColor = Color.Black,
                                modifier = Modifier.clickable { onEnhanceClick() }
                            )
                        } else if (item.enhanceStatus == EnhanceStatus.COMPLETED) {
                            BadgePill(
                                text = "HEVC HD",
                                containerColor = Color(0xCC2563EB),
                                contentColor = Color.White
                            )
                        }
                    }

                    IconButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color(0x88000000))
                            .graphicsLayer {
                                scaleX = favScale
                                scaleY = favScale
                            }
                    ) {
                        Icon(
                            painter = painterResource(
                                if (item.isFavorite) R.drawable.ic_heart_filled else R.drawable.ic_heart
                            ),
                            contentDescription = "Favorito",
                            tint = if (item.isFavorite) Color(0xFFFF4D7D) else Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Bottom scrim + title + download/enhance progress
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color(0xCC000000))
                            )
                        )
                        .padding(horizontal = 10.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.title,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val dimLabel = if (item.width > 0 && item.height > 0) {
                                "${item.width}×${item.height} · ${item.format.extension.uppercase()}"
                            } else {
                                item.format.extension.uppercase()
                            }
                            Text(
                                text = dimLabel,
                                color = Color(0xFFD0D5E3),
                                fontSize = 10.sp,
                                maxLines = 1
                            )
                        }

                        if (onDownloadAction != null && !item.isDownloaded) {
                            val isDownloading = downloadProgress?.status == DownloadStatus.DOWNLOADING
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x99161A26))
                                    .clickable { onDownloadAction() },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isDownloading) {
                                    CircularProgressIndicator(
                                        progress = { downloadProgress?.fraction ?: 0f },
                                        modifier = Modifier.size(22.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                } else {
                                    Icon(
                                        painter = painterResource(
                                            if (downloadProgress?.status == DownloadStatus.PAUSED) {
                                                R.drawable.ic_play
                                            } else {
                                                R.drawable.ic_download
                                            }
                                        ),
                                        contentDescription = "Descargar",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (item.enhanceStatus == EnhanceStatus.RUNNING || item.enhanceStatus == EnhanceStatus.QUEUED) {
                        LinearProgressIndicator(
                            progress = { item.enhanceProgress.coerceIn(0.05f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp)
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BadgePill(
    text: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(containerColor)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            color = contentColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
