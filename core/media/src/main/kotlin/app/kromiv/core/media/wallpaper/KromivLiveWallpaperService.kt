package app.kromiv.core.media.wallpaper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Movie
import android.graphics.drawable.AnimatedImageDrawable
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.service.wallpaper.WallpaperService
import android.view.Surface
import android.view.SurfaceHolder
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import app.kromiv.core.data.prefs.SettingsRepository
import app.kromiv.core.model.AppSettings
import app.kromiv.core.model.WallpaperMediaType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import kotlin.math.max

class KromivLiveWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = KromivEngine()

    inner class KromivEngine : Engine() {
        private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        private val settingsRepo by lazy { SettingsRepository(applicationContext) }
        private val powerManager by lazy { getSystemService(Context.POWER_SERVICE) as PowerManager }

        private var exoPlayer: ExoPlayer? = null
        private var trackSelector: DefaultTrackSelector? = null

        private var animatedDrawable: AnimatedImageDrawable? = null
        @Suppress("DEPRECATION")
        private var legacyMovie: Movie? = null
        private var movieStartMs: Long = 0L
        private var gifRenderJob: Job? = null

        private var currentSettings = AppSettings()
        private var loadedUri: String? = null
        private var loadedMediaType: WallpaperMediaType = WallpaperMediaType.VIDEO

        private var surfaceReady = false
        private var visibleToUser = false
        private var screenInteractive = true
        private var batterySaverActive = false

        private val powerReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_OFF -> {
                        screenInteractive = false
                        syncPlaybackState()
                    }
                    Intent.ACTION_SCREEN_ON -> {
                        screenInteractive = true
                        syncPlaybackState()
                    }
                    PowerManager.ACTION_POWER_SAVE_MODE_CHANGED -> {
                        batterySaverActive = powerManager.isPowerSaveMode
                        syncPlaybackState()
                    }
                }
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            screenInteractive = powerManager.isInteractive
            batterySaverActive = powerManager.isPowerSaveMode

            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(powerReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(powerReceiver, filter)
            }

            engineScope.launch {
                settingsRepo.settings.collectLatest { settings ->
                    currentSettings = settings
                    applySettingsUpdate(settings)
                }
            }
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            surfaceReady = true
            configureSurfaceFrameRate(holder.surface, currentSettings.activeLiveFps)
            loadOrAttachMedia()
            syncPlaybackState()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            configureSurfaceFrameRate(holder.surface, currentSettings.activeLiveFps)
            if (loadedMediaType == WallpaperMediaType.ANIMATED_IMAGE) {
                animatedDrawable?.setBounds(0, 0, width, height)
            }
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            visibleToUser = visible
            syncPlaybackState()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            surfaceReady = false
            stopGifLoop()
            exoPlayer?.clearVideoSurface()
            exoPlayer?.pause()
            super.onSurfaceDestroyed(holder)
        }

        override fun onDestroy() {
            runCatching { unregisterReceiver(powerReceiver) }
            stopGifLoop()
            releaseAnimatedResources()
            releaseExoPlayer()
            engineScope.cancel()
            super.onDestroy()
        }

        private fun shouldPlay(): Boolean {
            val powerBlocked = currentSettings.pauseLiveOnBatterySaver && batterySaverActive
            return surfaceReady &&
                visibleToUser &&
                screenInteractive &&
                !powerBlocked &&
                !currentSettings.activeLiveWallpaperUri.isNullOrBlank()
        }

        private fun applySettingsUpdate(settings: AppSettings) {
            val targetUri = settings.activeLiveWallpaperUri
            val uriChanged = targetUri != loadedUri || settings.activeLiveMediaType != loadedMediaType
            if (uriChanged) {
                loadedUri = targetUri
                loadedMediaType = settings.activeLiveMediaType
                loadOrAttachMedia()
            } else {
                exoPlayer?.volume = if (settings.activeLiveMuted) 0f else 1f
                updateTrackSelectorFps(settings.activeLiveFps)
                if (surfaceReady) {
                    configureSurfaceFrameRate(surfaceHolder.surface, settings.activeLiveFps)
                }
            }
            syncPlaybackState()
        }

        private fun loadOrAttachMedia() {
            val uriStr = currentSettings.activeLiveWallpaperUri ?: return
            if (!surfaceReady) return

            if (currentSettings.activeLiveMediaType == WallpaperMediaType.ANIMATED_IMAGE) {
                releaseExoPlayer()
                loadAnimatedImage(uriStr)
            } else {
                stopGifLoop()
                releaseAnimatedResources()
                prepareExoPlayer(uriStr)
            }
        }

        @OptIn(UnstableApi::class)
        private fun prepareExoPlayer(uriStr: String) {
            val existing = exoPlayer
            val player = if (existing == null) {
                val selector = DefaultTrackSelector(applicationContext).apply {
                    parameters = buildUponParameters()
                        .setMaxVideoFrameRate(currentSettings.activeLiveFps)
                        .build()
                }
                trackSelector = selector
                val renderersFactory = DefaultRenderersFactory(applicationContext).apply {
                    setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
                }
                ExoPlayer.Builder(applicationContext, renderersFactory)
                    .setTrackSelector(selector)
                    .build().apply {
                        repeatMode = Player.REPEAT_MODE_ALL
                        videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
                    }
            } else {
                existing
            }
            exoPlayer = player

            val mediaUri = if (uriStr.startsWith("/")) Uri.fromFile(File(uriStr)) else Uri.parse(uriStr)
            player.setVideoSurface(surfaceHolder.surface)
            player.volume = if (currentSettings.activeLiveMuted) 0f else 1f
            player.setMediaItem(MediaItem.fromUri(mediaUri))
            player.prepare()
            player.playWhenReady = shouldPlay()
        }

        @OptIn(UnstableApi::class)
        private fun updateTrackSelectorFps(fps: Int) {
            val selector = trackSelector ?: return
            selector.parameters = selector.buildUponParameters()
                .setMaxVideoFrameRate(fps)
                .build()
        }

        private fun configureSurfaceFrameRate(surface: Surface?, fps: Int) {
            if (surface == null || !surface.isValid) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                runCatching {
                    surface.setFrameRate(
                        fps.toFloat(),
                        Surface.FRAME_RATE_COMPATIBILITY_DEFAULT
                    )
                }
            }
        }

        private fun syncPlaybackState() {
            val play = shouldPlay()
            if (loadedMediaType == WallpaperMediaType.ANIMATED_IMAGE) {
                if (play) startGifLoop() else stopGifLoop()
            } else {
                exoPlayer?.let { player ->
                    player.playWhenReady = play
                    if (!play) {
                        player.pause()
                    } else if (player.playbackState == Player.STATE_IDLE) {
                        player.prepare()
                        player.play()
                    } else {
                        player.play()
                    }
                }
            }
        }

        @Suppress("DEPRECATION")
        private fun loadAnimatedImage(uriStr: String) {
            releaseAnimatedResources()
            val file = if (uriStr.startsWith("/")) File(uriStr) else null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                runCatching {
                    val source = if (file != null) {
                        ImageDecoder.createSource(file)
                    } else {
                        ImageDecoder.createSource(contentResolver, Uri.parse(uriStr))
                    }
                    val drawable = ImageDecoder.decodeDrawable(source)
                    if (drawable is AnimatedImageDrawable) {
                        val bounds = surfaceHolder.surfaceFrame
                        drawable.setBounds(0, 0, max(1, bounds.width()), max(1, bounds.height()))
                        drawable.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
                        animatedDrawable = drawable
                    }
                }
            } else if (file != null && file.exists()) {
                runCatching {
                    FileInputStream(file).use { input ->
                        legacyMovie = Movie.decodeStream(input)
                        movieStartMs = 0L
                    }
                }
            }
        }

        private fun startGifLoop() {
            if (gifRenderJob?.isActive == true) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                animatedDrawable?.start()
            }
            gifRenderJob = engineScope.launch {
                val frameDelayMs = if (currentSettings.activeLiveFps <= 30) 33L else 16L
                while (isActive && shouldPlay()) {
                    drawAnimatedFrame()
                    delay(frameDelayMs)
                }
            }
        }

        private fun stopGifLoop() {
            gifRenderJob?.cancel()
            gifRenderJob = null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                animatedDrawable?.stop()
            }
        }

        @Suppress("DEPRECATION")
        private fun drawAnimatedFrame() {
            val holder = surfaceHolder
            if (!holder.surface.isValid) return
            var canvas: Canvas? = null
            try {
                canvas = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    holder.lockHardwareCanvas()
                } else {
                    holder.lockCanvas()
                }
                if (canvas == null) return
                canvas.drawColor(Color.BLACK)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val drawable = animatedDrawable
                    if (drawable != null) {
                        drawable.setBounds(0, 0, canvas.width, canvas.height)
                        drawable.draw(canvas)
                    }
                } else {
                    val movie = legacyMovie
                    if (movie != null && movie.width() > 0 && movie.height() > 0) {
                        val now = System.currentTimeMillis()
                        if (movieStartMs == 0L) movieStartMs = now
                        val duration = max(movie.duration(), 1000)
                        movie.setTime(((now - movieStartMs) % duration).toInt())
                        val scale = max(
                            canvas.width.toFloat() / movie.width().toFloat(),
                            canvas.height.toFloat() / movie.height().toFloat()
                        )
                        val dx = (canvas.width - movie.width() * scale) / 2f
                        val dy = (canvas.height - movie.height() * scale) / 2f
                        canvas.save()
                        canvas.translate(dx, dy)
                        canvas.scale(scale, scale)
                        movie.draw(canvas, 0f, 0f)
                        canvas.restore()
                    }
                }
            } catch (_: Throwable) {
            } finally {
                if (canvas != null) {
                    runCatching { holder.unlockCanvasAndPost(canvas) }
                }
            }
        }

        private fun releaseAnimatedResources() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                animatedDrawable?.stop()
                animatedDrawable = null
            }
            legacyMovie = null
        }

        private fun releaseExoPlayer() {
            exoPlayer?.let { player ->
                runCatching { player.clearVideoSurface() }
                runCatching { player.stop() }
                runCatching { player.release() }
            }
            exoPlayer = null
            trackSelector = null
        }
    }
}
