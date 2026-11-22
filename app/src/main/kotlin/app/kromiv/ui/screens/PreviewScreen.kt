@file:OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3Api::class)

package app.kromiv.ui.screens

import android.net.Uri
import android.text.format.Formatter
import androidx.annotation.OptIn as Media3OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import app.kromiv.R
import app.kromiv.core.media.wallpaper.NormalizedCropRect
import app.kromiv.core.model.DownloadProgress
import app.kromiv.core.model.DownloadStatus
import app.kromiv.core.model.WallpaperItem
import app.kromiv.core.model.WallpaperMediaType
import app.kromiv.core.model.WallpaperSource
import app.kromiv.core.model.WallpaperTarget
import app.kromiv.ui.components.BadgePill
import app.kromiv.ui.components.KromivThumbnailImage
import java.io.File
import kotlin.math.roundToInt

private enum class PreviewMockupMode(val label: String) {
    NONE("Limpio"),
    LOCK("Bloqueo"),
    HOME("Inicio")
}

@Composable
fun PreviewScreen(
    item: WallpaperItem,
    sharedKeyPrefix: String,
    downloadProgress: DownloadProgress?,
    defaultMuted: Boolean,
    isApplyingWallpaper: Boolean,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDownloadOrPause: () -> Unit,
    onEnhanceClick: () -> Unit,
    onApplyStatic: (WallpaperTarget, NormalizedCropRect?) -> Unit,
    onApplyLive: (Boolean) -> Unit
) {
    val context = LocalContext.current
    var mockupMode by remember { mutableStateOf(PreviewMockupMode.NONE) }
    var showApplySheet by remember { mutableStateOf(false) }
    var showInfoSheet by remember { mutableStateOf(false) }

    var isMuted by remember(item.id) { mutableStateOf(defaultMuted) }
    var isVideoPlaying by remember(item.id) { mutableStateOf(true) }

    // Static zoom/pan crop + parallax state
    var zoomScale by remember(item.id) { mutableFloatStateOf(1f) }
    var panOffset by remember(item.id) { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize(1080, 1920)) }
    var parallaxRaw by remember { mutableStateOf(Offset.Zero) }
    val parallaxAnimated by animateOffsetAsState(
        targetValue = parallaxRaw,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "parallaxOffset"
    )

    val isDownloading = downloadProgress?.status == DownloadStatus.DOWNLOADING
    val playableVideoUri = when {
        item.mediaType != WallpaperMediaType.VIDEO -> null
        item.isDownloaded -> item.activePlayableUri
        !item.previewUri.isNullOrBlank() -> item.previewUri
        else -> item.contentUri
    }

    with(sharedTransitionScope) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .sharedBounds(
                    sharedContentState = rememberSharedContentState(key = "$sharedKeyPrefix-${item.id}"),
                    animatedVisibilityScope = animatedVisibilityScope
                )
                .onSizeChanged { containerSize = it }
        ) {
            if (item.mediaType == WallpaperMediaType.VIDEO && playableVideoUri != null) {
                VideoPreviewSurface(
                    uriString = playableVideoUri,
                    isMuted = isMuted,
                    isPlaying = isVideoPlaying,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragEnd = { parallaxRaw = Offset.Zero },
                                onDragCancel = { parallaxRaw = Offset.Zero }
                            ) { _, dragAmount ->
                                parallaxRaw = Offset(
                                    (parallaxRaw.x + dragAmount.x * 0.2f).coerceIn(-30f, 30f),
                                    (parallaxRaw.y + dragAmount.y * 0.2f).coerceIn(-30f, 30f)
                                )
                            }
                        }
                        .graphicsLayer {
                            translationX = parallaxAnimated.x
                            translationY = parallaxAnimated.y
                            scaleX = 1.04f
                            scaleY = 1.04f
                        }
                )
            } else {
                val imageUri = if (item.isDownloaded || item.mediaType == WallpaperMediaType.ANIMATED_IMAGE) {
                    item.activePlayableUri
                } else {
                    item.thumbnailUri
                }
                KromivThumbnailImage(
                    uri = imageUri,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(item.id) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val newScale = (zoomScale * zoom).coerceIn(1f, 3f)
                                val maxX = (containerSize.width * (newScale - 1f)) / 2f
                                val maxY = (containerSize.height * (newScale - 1f)) / 2f
                                zoomScale = newScale
                                if (newScale > 1.01f) {
                                    panOffset = Offset(
                                        (panOffset.x + pan.x).coerceIn(-maxX, maxX),
                                        (panOffset.y + pan.y).coerceIn(-maxY, maxY)
                                    )
                                } else {
                                    panOffset = Offset.Zero
                                    parallaxRaw = Offset(
                                        (parallaxRaw.x + pan.x * 0.22f).coerceIn(-28f, 28f),
                                        (parallaxRaw.y + pan.y * 0.22f).coerceIn(-28f, 28f)
                                    )
                                }
                            }
                        }
                        .graphicsLayer {
                            scaleX = zoomScale * 1.03f
                            scaleY = zoomScale * 1.03f
                            translationX = if (zoomScale > 1.01f) panOffset.x else parallaxAnimated.x
                            translationY = if (zoomScale > 1.01f) panOffset.y else parallaxAnimated.y
                        }
                )
            }

            // Live Mockup Overlay ("Probar antes de aplicar")
            AnimatedVisibility(
                visible = mockupMode != PreviewMockupMode.NONE,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                PhoneMockupOverlay(mode = mockupMode)
            }

            // Top bar controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xBB000000), Color.Transparent)
                        )
                    )
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color(0x77000000))
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_back),
                        contentDescription = "Volver",
                        tint = Color.White
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Mockup mode toggle button ("Probar antes de aplicar")
                    FilledTonalButton(
                        onClick = {
                            mockupMode = when (mockupMode) {
                                PreviewMockupMode.NONE -> PreviewMockupMode.LOCK
                                PreviewMockupMode.LOCK -> PreviewMockupMode.HOME
                                PreviewMockupMode.HOME -> PreviewMockupMode.NONE
                            }
                        },
                        shape = RoundedCornerShape(999.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_phone_preview),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (mockupMode == PreviewMockupMode.NONE) {
                                "Probar"
                            } else {
                                "Vista: ${mockupMode.label}"
                            },
                            fontSize = 12.sp
                        )
                    }

                    IconButton(
                        onClick = { showInfoSheet = true },
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color(0x77000000))
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_info),
                            contentDescription = "Información",
                            tint = Color.White
                        )
                    }
                }
            }

            // Bottom floating control deck
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color(0xE0000000))
                        )
                    )
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.title,
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${item.category.displayNameEs} · ${item.width}×${item.height} · ${item.format.extension.uppercase()}",
                            color = Color(0xFFD1D5DB),
                            fontSize = 12.sp
                        )
                    }

                    if (item.mediaType == WallpaperMediaType.VIDEO) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            IconButton(
                                onClick = { isMuted = !isMuted },
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x66FFFFFF))
                            ) {
                                Icon(
                                    painter = painterResource(
                                        if (isMuted) R.drawable.ic_volume_off else R.drawable.ic_volume_on
                                    ),
                                    contentDescription = "Audio",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(
                                onClick = { isVideoPlaying = !isVideoPlaying },
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x66FFFFFF))
                            ) {
                                Icon(
                                    painter = painterResource(
                                        if (isVideoPlaying) R.drawable.ic_pause else R.drawable.ic_play
                                    ),
                                    contentDescription = "Pausar/Reproducir",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    } else if (zoomScale > 1.02f) {
                        OutlinedButton(onClick = {
                            zoomScale = 1f
                            panOffset = Offset.Zero
                        }) {
                            Text("Restablecer recorte", color = Color.White, fontSize = 12.sp)
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color(0x33FFFFFF))
                    ) {
                        Icon(
                            painter = painterResource(
                                if (item.isFavorite) R.drawable.ic_heart_filled else R.drawable.ic_heart
                            ),
                            contentDescription = "Favorito",
                            tint = if (item.isFavorite) Color(0xFFFF4D7D) else Color.White
                        )
                    }

                    if (item.isVideo && item.isDownloaded) {
                        OutlinedButton(
                            onClick = onEnhanceClick,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.height(48.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_enhance),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Mejorar", color = Color.White)
                        }
                    }

                    Button(
                        onClick = {
                            if (!item.isDownloaded && item.source == WallpaperSource.STORE_REMOTE) {
                                onDownloadOrPause()
                            } else {
                                showApplySheet = true
                            }
                        },
                        enabled = !isApplyingWallpaper,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        if (isApplyingWallpaper) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Aplicando…")
                        } else if (!item.isDownloaded && item.source == WallpaperSource.STORE_REMOTE) {
                            if (isDownloading) {
                                val pct = ((downloadProgress?.fraction ?: 0f) * 100f).roundToInt()
                                Text("Pausar descarga ($pct%)")
                            } else {
                                Icon(
                                    painter = painterResource(R.drawable.ic_download),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    if (downloadProgress?.status == DownloadStatus.PAUSED) {
                                        "Reanudar descarga"
                                    } else {
                                        "Descargar (${Formatter.formatShortFileSize(context, item.fileSizeBytes)})"
                                    }
                                )
                            }
                        } else {
                            Icon(
                                painter = painterResource(R.drawable.ic_wallpaper),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Aplicar fondo")
                        }
                    }
                }
            }
        }
    }

    if (showApplySheet) {
        ModalBottomSheet(onDismissRequest = { showApplySheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Aplicar \"${item.title}\"",
                    style = MaterialTheme.typography.titleLarge
                )

                val cropRect = computeNormalizedCropRect(zoomScale, panOffset, containerSize)

                if (item.mediaType == WallpaperMediaType.VIDEO) {
                    Text(
                        text = "Se abrirá el selector de fondo animado con ExoPlayer optimizado para batería (${if (isMuted) "silenciado" else "con audio"}).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = {
                            showApplySheet = false
                            onApplyLive(isMuted)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Establecer como Live Wallpaper")
                    }
                } else {
                    if (item.mediaType == WallpaperMediaType.ANIMATED_IMAGE) {
                        Button(
                            onClick = {
                                showApplySheet = false
                                onApplyLive(true)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Aplicar como fondo animado (${item.format.extension.uppercase()})")
                        }
                    }
                    Button(
                        onClick = {
                            showApplySheet = false
                            onApplyStatic(WallpaperTarget.HOME, cropRect)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Pantalla de inicio")
                    }
                    FilledTonalButton(
                        onClick = {
                            showApplySheet = false
                            onApplyStatic(WallpaperTarget.LOCK, cropRect)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Pantalla de bloqueo")
                    }
                    OutlinedButton(
                        onClick = {
                            showApplySheet = false
                            onApplyStatic(WallpaperTarget.BOTH, cropRect)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Inicio y bloqueo")
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    if (showInfoSheet) {
        ModalBottomSheet(onDismissRequest = { showInfoSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = item.title, style = MaterialTheme.typography.titleLarge)
                Text("Autor: ${item.author} · Licencia: ${item.license}")
                Text("Categoría: ${item.category.displayNameEs} · Formato: ${item.format.extension.uppercase()}")
                Text("Resolución: ${item.width}×${item.height}")
                if (item.fileSizeBytes > 0L) {
                    Text("Tamaño: ${Formatter.formatShortFileSize(context, item.fileSizeBytes)}")
                }
                if (item.enhancedSizeBytes > 0L) {
                    Text("Versión mejorada H.265: ${Formatter.formatShortFileSize(context, item.enhancedSizeBytes)}")
                }
                if (item.tags.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        item.tags.take(4).forEach { tag ->
                            BadgePill(
                                text = "#$tag",
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Media3OptIn(UnstableApi::class)
@Composable
private fun VideoPreviewSurface(
    uriString: String,
    isMuted: Boolean,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val mediaUri = remember(uriString) {
        if (uriString.startsWith("/")) Uri.fromFile(File(uriString)) else Uri.parse(uriString)
    }

    val exoPlayer = remember(uriString) {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ALL
            setMediaItem(MediaItem.fromUri(mediaUri))
            prepare()
            playWhenReady = true
        }
    }

    LaunchedEffect(isMuted) {
        exoPlayer.volume = if (isMuted) 0f else 1f
    }

    LaunchedEffect(isPlaying) {
        exoPlayer.playWhenReady = isPlaying
        if (isPlaying) exoPlayer.play() else exoPlayer.pause()
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.stop()
            exoPlayer.release()
        }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                player = exoPlayer
            }
        },
        update = { view ->
            if (view.player !== exoPlayer) {
                view.player = exoPlayer
            }
        },
        modifier = modifier
    )
}

@Composable
private fun PhoneMockupOverlay(mode: PreviewMockupMode) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 68.dp)
    ) {
        if (mode == PreviewMockupMode.LOCK) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "09:41",
                    color = Color.White,
                    fontSize = 72.sp,
                    fontWeight = FontWeight.Light,
                    letterSpacing = (-2).sp
                )
                Text(
                    text = "Domingo, 22 de noviembre",
                    color = Color(0xFFE5E7EB),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(22.dp))
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0x77111420)
                ) {
                    Text(
                        text = "Vista previa de pantalla de bloqueo",
                        color = Color.White,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 80.dp)
                    .size(54.dp)
                    .border(2.dp, Color(0xAAFFFFFF), CircleShape)
            )
        } else if (mode == PreviewMockupMode.HOME) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0x660F172A),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Dom, 22 nov", color = Color(0xFFD1D5DB), fontSize = 12.sp)
                            Text("24° Despejado", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Text("Kromiv", color = Color(0xFFA5B4FC), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    repeat(2) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            repeat(4) { idx ->
                                MockAppIcon(label = "App ${it * 4 + idx + 1}")
                            }
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(26.dp),
                    color = Color(0x55000000),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 66.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        repeat(4) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x88FFFFFF))
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MockAppIcon(label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(Color(0x99FFFFFF))
        )
        Spacer(Modifier.height(4.dp))
        Text(text = label, color = Color.White, fontSize = 10.sp)
    }
}

private fun computeNormalizedCropRect(
    scale: Float,
    offset: Offset,
    containerSize: IntSize
): NormalizedCropRect? {
    if (scale <= 1.02f && offset == Offset.Zero) return null
    val w = containerSize.width.toFloat().coerceAtLeast(1f)
    val h = containerSize.height.toFloat().coerceAtLeast(1f)
    val visibleW = 1f / scale
    val visibleH = 1f / scale
    val centerX = (0.5f - (offset.x / (w * scale))).coerceIn(visibleW / 2f, 1f - visibleW / 2f)
    val centerY = (0.5f - (offset.y / (h * scale))).coerceIn(visibleH / 2f, 1f - visibleH / 2f)
    return NormalizedCropRect(
        left = (centerX - visibleW / 2f).coerceIn(0f, 1f),
        top = (centerY - visibleH / 2f).coerceIn(0f, 1f),
        right = (centerX + visibleW / 2f).coerceIn(0f, 1f),
        bottom = (centerY + visibleH / 2f).coerceIn(0f, 1f)
    )
}
