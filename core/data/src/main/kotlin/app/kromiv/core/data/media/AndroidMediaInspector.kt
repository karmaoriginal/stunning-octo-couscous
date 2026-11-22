package app.kromiv.core.data.media

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface
import app.kromiv.core.model.WallpaperFormat
import app.kromiv.core.model.WallpaperMediaType
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

data class MediaProbeResult(
    val title: String,
    val format: WallpaperFormat,
    val mediaType: WallpaperMediaType,
    val width: Int,
    val height: Int,
    val durationMs: Long,
    val bitrateBps: Long,
    val fps: Float,
    val codec: String?,
    val fileSizeBytes: Long
)

class AndroidMediaInspector(private val context: Context) {

    fun queryDisplayNameAndSize(uri: Uri): Pair<String, Long> {
        var name = uri.lastPathSegment?.substringAfterLast('/') ?: "Wallpaper"
        var size = 0L
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx >= 0 && !cursor.isNull(nameIdx)) {
                        name = cursor.getString(nameIdx)
                    }
                    if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                        size = cursor.getLong(sizeIdx)
                    }
                }
            }
        return name to size
    }

    fun detectFormat(uri: Uri, fileName: String): WallpaperFormat {
        val mime = context.contentResolver.getType(uri)
        val ext = fileName.substringAfterLast('.', "")
        return WallpaperFormat.fromExtensionOrMime(ext, mime)
    }

    fun probeLocalFile(file: File, originalName: String, formatHint: WallpaperFormat? = null): MediaProbeResult {
        val ext = file.extension.ifBlank { originalName.substringAfterLast('.', "") }
        val format = formatHint?.takeIf { it != WallpaperFormat.UNKNOWN }
            ?: WallpaperFormat.fromExtensionOrMime(ext, null)
        val title = originalName.substringBeforeLast('.').ifBlank { "Wallpaper" }
        val fileSize = file.length()

        if (format.isVideo) {
            return probeVideo(file, title, format, fileSize)
        }

        val isAnimated = when (format) {
            WallpaperFormat.GIF -> isAnimatedGif(file)
            WallpaperFormat.WEBP -> isAnimatedWebp(file)
            else -> false
        }
        val mediaType = if (isAnimated) WallpaperMediaType.ANIMATED_IMAGE else WallpaperMediaType.IMAGE
        val (w, h) = probeImageDimensions(file)
        return MediaProbeResult(
            title = title,
            format = format,
            mediaType = mediaType,
            width = w,
            height = h,
            durationMs = 0L,
            bitrateBps = 0L,
            fps = 0f,
            codec = format.extension,
            fileSizeBytes = fileSize
        )
    }

    private fun probeImageDimensions(file: File): Pair<Int, Int> {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, opts)
        var width = opts.outWidth.coerceAtLeast(0)
        var height = opts.outHeight.coerceAtLeast(0)

        runCatching {
            val exif = ExifInterface(file)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            if (orientation == ExifInterface.ORIENTATION_ROTATE_90 ||
                orientation == ExifInterface.ORIENTATION_ROTATE_270 ||
                orientation == ExifInterface.ORIENTATION_TRANSPOSE ||
                orientation == ExifInterface.ORIENTATION_TRANSVERSE
            ) {
                val tmp = width
                width = height
                height = tmp
            }
        }
        return width to height
    }

    private fun probeVideo(
        file: File,
        title: String,
        format: WallpaperFormat,
        fileSize: Long
    ): MediaProbeResult {
        var width = 0
        var height = 0
        var durationMs = 0L
        var bitrateBps = 0L
        var fps = 30f
        var codec: String? = null

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            val rawW = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val rawH = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (rotation == 90 || rotation == 270) {
                width = rawH
                height = rawW
            } else {
                width = rawW
                height = rawH
            }
            durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            bitrateBps = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull() ?: 0L
            val captureRate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.toFloatOrNull()
            val frameCount = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)?.toFloatOrNull()
            fps = when {
                captureRate != null && captureRate > 0f -> captureRate
                frameCount != null && durationMs > 0L -> (frameCount / (durationMs / 1000f)).coerceIn(1f, 120f)
                else -> 30f
            }
        } catch (_: Throwable) {
        } finally {
            runCatching { retriever.release() }
        }

        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            for (i in 0 until extractor.trackCount) {
                val trackFormat = extractor.getTrackFormat(i)
                val mime = trackFormat.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/")) {
                    codec = mime.removePrefix("video/")
                    if (fps <= 0f && trackFormat.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                        fps = trackFormat.getInteger(MediaFormat.KEY_FRAME_RATE).toFloat()
                    }
                    break
                }
            }
        } catch (_: Throwable) {
        } finally {
            runCatching { extractor.release() }
        }

        if (bitrateBps <= 0L && durationMs > 0L && fileSize > 0L) {
            bitrateBps = (fileSize * 8L * 1000L) / durationMs
        }

        return MediaProbeResult(
            title = title,
            format = format,
            mediaType = WallpaperMediaType.VIDEO,
            width = width,
            height = height,
            durationMs = durationMs,
            bitrateBps = bitrateBps,
            fps = fps,
            codec = codec,
            fileSizeBytes = fileSize
        )
    }

    private fun isAnimatedGif(file: File): Boolean = runCatching {
        FileInputStream(file).use { input ->
            val buf = ByteArray(minOf(file.length().toInt(), 32 * 1024))
            val read = input.read(buf)
            if (read <= 0) return@use false
            var graphicControlCount = 0
            for (i in 0 until read - 2) {
                if (buf[i] == 0x21.toByte() && buf[i + 1] == 0xF9.toByte() && buf[i + 2] == 0x04.toByte()) {
                    graphicControlCount++
                    if (graphicControlCount >= 2) return@use true
                }
            }
            false
        }
    }.getOrDefault(false)

    private fun isAnimatedWebp(file: File): Boolean = runCatching {
        FileInputStream(file).use { input ->
            val buf = ByteArray(minOf(file.length().toInt(), 256))
            val read = input.read(buf)
            if (read < 20) return@use false
            val header = String(buf, 0, read, Charsets.US_ASCII)
            header.contains("ANIM")
        }
    }.getOrDefault(false)

    companion object {
        fun computeSha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            FileInputStream(file).use { input ->
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    digest.update(buffer, 0, read)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
