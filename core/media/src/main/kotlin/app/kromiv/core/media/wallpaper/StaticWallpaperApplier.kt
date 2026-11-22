package app.kromiv.core.media.wallpaper

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.DisplayMetrics
import android.view.WindowManager
import app.kromiv.core.model.WallpaperTarget
import coil3.ImageLoader
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

data class NormalizedCropRect(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 1f,
    val bottom: Float = 1f
)

class StaticWallpaperApplier(
    private val context: Context,
    private val imageLoader: ImageLoader
) {
    suspend fun applyWallpaper(
        sourceUri: String,
        target: WallpaperTarget,
        cropRect: NormalizedCropRect? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val (screenW, screenH) = getScreenDimensions()
            val data: Any = if (sourceUri.startsWith("/")) File(sourceUri) else Uri.parse(sourceUri)

            val request = ImageRequest.Builder(context)
                .data(data)
                .size(maxOf(screenW, 1080) * 2, maxOf(screenH, 1920) * 2)
                .allowHardware(false)
                .build()

            val decodedBitmap = when (val result = imageLoader.execute(request)) {
                is SuccessResult -> result.image.toBitmap()
                is ErrorResult -> throw result.throwable
            }

            val croppedBitmap = if (cropRect != null) {
                cropNormalized(decodedBitmap, cropRect)
            } else {
                centerCropToRatio(decodedBitmap, screenW, screenH)
            }

            try {
                val wallpaperManager = WallpaperManager.getInstance(context)
                val flags = when (target) {
                    WallpaperTarget.HOME -> WallpaperManager.FLAG_SYSTEM
                    WallpaperTarget.LOCK -> WallpaperManager.FLAG_LOCK
                    WallpaperTarget.BOTH -> WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
                }
                wallpaperManager.setBitmap(croppedBitmap, null, true, flags)
            } finally {
                if (croppedBitmap !== decodedBitmap) {
                    croppedBitmap.recycle()
                }
                decodedBitmap.recycle()
            }
        }
    }

    private fun cropNormalized(src: Bitmap, crop: NormalizedCropRect): Bitmap {
        val x = (crop.left.coerceIn(0f, 0.98f) * src.width).roundToInt().coerceIn(0, src.width - 1)
        val y = (crop.top.coerceIn(0f, 0.98f) * src.height).roundToInt().coerceIn(0, src.height - 1)
        val w = ((crop.right - crop.left).coerceIn(0.02f, 1f) * src.width).roundToInt()
            .coerceIn(1, src.width - x)
        val h = ((crop.bottom - crop.top).coerceIn(0.02f, 1f) * src.height).roundToInt()
            .coerceIn(1, src.height - y)
        if (x == 0 && y == 0 && w == src.width && h == src.height) return src
        return Bitmap.createBitmap(src, x, y, w, h)
    }

    private fun centerCropToRatio(src: Bitmap, screenW: Int, screenH: Int): Bitmap {
        if (screenW <= 0 || screenH <= 0 || src.width <= 0 || src.height <= 0) return src
        val targetRatio = screenW.toFloat() / screenH.toFloat()
        val srcRatio = src.width.toFloat() / src.height.toFloat()
        if (kotlin.math.abs(targetRatio - srcRatio) < 0.02f) return src
        return if (srcRatio > targetRatio) {
            val newW = (src.height * targetRatio).roundToInt().coerceIn(1, src.width)
            val x = ((src.width - newW) / 2f).roundToInt().coerceAtLeast(0)
            Bitmap.createBitmap(src, x, 0, newW, src.height)
        } else {
            val newH = (src.width / targetRatio).roundToInt().coerceIn(1, src.height)
            val y = ((src.height - newH) / 2f).roundToInt().coerceAtLeast(0)
            Bitmap.createBitmap(src, 0, y, src.width, newH)
        }
    }

    @Suppress("DEPRECATION")
    private fun getScreenDimensions(): Pair<Int, Int> {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            ?: return 1080 to 2400
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            val bounds = wm.currentWindowMetrics.bounds
            bounds.width() to bounds.height()
        } else {
            val metrics = DisplayMetrics()
            wm.defaultDisplay?.getRealMetrics(metrics)
            metrics.widthPixels.coerceAtLeast(1080) to metrics.heightPixels.coerceAtLeast(1920)
        }
    }
}
