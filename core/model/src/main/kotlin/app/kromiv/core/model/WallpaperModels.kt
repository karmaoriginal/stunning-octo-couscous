package app.kromiv.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class WallpaperMediaType {
    @SerialName("image")
    IMAGE,

    @SerialName("animated_image")
    ANIMATED_IMAGE,

    @SerialName("video")
    VIDEO;

    val isAnimated: Boolean
        get() = this == ANIMATED_IMAGE || this == VIDEO
}

@Serializable
enum class WallpaperFormat(val extension: String, val mimeType: String) {
    @SerialName("jpg") JPG("jpg", "image/jpeg"),
    @SerialName("png") PNG("png", "image/png"),
    @SerialName("webp") WEBP("webp", "image/webp"),
    @SerialName("heic") HEIC("heic", "image/heic"),
    @SerialName("avif") AVIF("avif", "image/avif"),
    @SerialName("gif") GIF("gif", "image/gif"),
    @SerialName("bmp") BMP("bmp", "image/bmp"),
    @SerialName("mp4") MP4("mp4", "video/mp4"),
    @SerialName("mkv") MKV("mkv", "video/x-matroska"),
    @SerialName("webm") WEBM("webm", "video/webm"),
    @SerialName("mov") MOV("mov", "video/quicktime"),
    @SerialName("3gp") THREE_GP("3gp", "video/3gpp"),
    @SerialName("unknown") UNKNOWN("bin", "application/octet-stream");

    val isVideo: Boolean
        get() = this == MP4 || this == MKV || this == WEBM || this == MOV || this == THREE_GP

    companion object {
        fun fromExtensionOrMime(extension: String?, mimeType: String?): WallpaperFormat {
            val ext = extension?.lowercase()?.trimStart('.')
            val mime = mimeType?.lowercase()
            return when {
                ext == "jpg" || ext == "jpeg" || mime == "image/jpeg" || mime == "image/jpg" -> JPG
                ext == "png" || mime == "image/png" -> PNG
                ext == "webp" || mime == "image/webp" -> WEBP
                ext == "heic" || ext == "heif" || mime == "image/heic" || mime == "image/heif" -> HEIC
                ext == "avif" || mime == "image/avif" -> AVIF
                ext == "gif" || mime == "image/gif" -> GIF
                ext == "bmp" || mime == "image/bmp" || mime == "image/x-ms-bmp" -> BMP
                ext == "mp4" || ext == "m4v" || mime == "video/mp4" -> MP4
                ext == "mkv" || mime == "video/x-matroska" -> MKV
                ext == "webm" || mime == "video/webm" -> WEBM
                ext == "mov" || mime == "video/quicktime" -> MOV
                ext == "3gp" || ext == "3gpp" || mime == "video/3gpp" -> THREE_GP
                else -> UNKNOWN
            }
        }
    }
}

@Serializable
enum class WallpaperCategory(val slug: String, val displayNameEs: String) {
    @SerialName("all") ALL("all", "Todos"),
    @SerialName("amoled") AMOLED("amoled", "Oscuro / AMOLED"),
    @SerialName("abstract") ABSTRACT("abstract", "Abstracto"),
    @SerialName("space") SPACE("space", "Espacio"),
    @SerialName("nature") NATURE("nature", "Naturaleza"),
    @SerialName("water") WATER("water", "Agua"),
    @SerialName("colors") COLORS("colors", "Colores"),
    @SerialName("minimal") MINIMAL("minimal", "Minimalista"),
    @SerialName("landscapes") LANDSCAPES("landscapes", "Paisajes"),
    @SerialName("anime") ANIME("anime", "Anime");

    companion object {
        val storeCategories: List<WallpaperCategory> = entries.toList()

        fun fromSlug(slug: String): WallpaperCategory =
            entries.firstOrNull { it.slug.equals(slug, ignoreCase = true) } ?: ABSTRACT
    }
}

enum class WallpaperSource {
    LOCAL_IMPORT,
    STORE_DOWNLOAD,
    STORE_REMOTE
}

enum class WallpaperTarget {
    HOME,
    LOCK,
    BOTH
}

enum class EnhancePreset(val label: String, val shortSidePx: Int, val targetBitrateKbps: Int) {
    FHD_1080P("1080p Full HD (Equilibrado)", 1080, 6500),
    QHD_1440P("1440p Quad HD (Máxima nitidez)", 1440, 10500)
}

enum class EnhanceStatus {
    NONE,
    RECOMMENDED,
    QUEUED,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class EnhanceOptions(
    val preset: EnhancePreset = EnhancePreset.FHD_1080P,
    val targetFps: Int = 60,
    val denoiseEnabled: Boolean = true,
    val sharpenEnabled: Boolean = true,
    val stripAudio: Boolean = true,
    val useAiUpscaler: Boolean = false
)

enum class CatalogSort {
    POPULAR,
    RECENT,
    FAVORITES
}

enum class MediaTypeFilter {
    ALL,
    PHOTOS,
    VIDEOS
}

enum class DownloadStatus {
    IDLE,
    QUEUED,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED
}

data class DownloadProgress(
    val id: String,
    val bytesRead: Long,
    val totalBytes: Long,
    val status: DownloadStatus,
    val errorMessage: String? = null
) {
    val fraction: Float
        get() = if (totalBytes > 0L) (bytesRead.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
}

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val amoledDark: Boolean = true,
    val dynamicColor: Boolean = true,
    val liveFpsLimit: Int = 60,
    val muteLiveByDefault: Boolean = true,
    val pauseLiveOnBatterySaver: Boolean = true,
    val wifiOnlyDownloads: Boolean = false,
    val maxCacheMb: Int = 256,
    val aiUpscalerEnabled: Boolean = false,
    val activeLiveWallpaperUri: String? = null,
    val activeLiveMediaType: WallpaperMediaType = WallpaperMediaType.VIDEO,
    val activeLiveMuted: Boolean = true,
    val activeLiveFps: Int = 60,
    val catalogUrlOverride: String? = null
)

data class StorageStats(
    val importsBytes: Long = 0L,
    val downloadsBytes: Long = 0L,
    val enhancedBytes: Long = 0L,
    val partialDownloadsBytes: Long = 0L,
    val imageCacheBytes: Long = 0L,
    val itemCount: Int = 0
) {
    val totalManagedBytes: Long
        get() = importsBytes + downloadsBytes + enhancedBytes + partialDownloadsBytes + imageCacheBytes
}

data class WallpaperItem(
    val id: String,
    val title: String,
    val author: String,
    val license: String,
    val category: WallpaperCategory,
    val mediaType: WallpaperMediaType,
    val format: WallpaperFormat,
    val source: WallpaperSource,
    val thumbnailUri: String,
    val previewUri: String?,
    val contentUri: String,
    val enhancedUri: String? = null,
    val width: Int = 0,
    val height: Int = 0,
    val durationMs: Long = 0L,
    val bitrateBps: Long = 0L,
    val fps: Float = 0f,
    val codec: String? = null,
    val fileSizeBytes: Long = 0L,
    val enhancedSizeBytes: Long = 0L,
    val sha256: String? = null,
    val isFavorite: Boolean = false,
    val isDownloaded: Boolean = false,
    val popularityScore: Int = 0,
    val createdAtEpochMs: Long = 0L,
    val enhanceStatus: EnhanceStatus = EnhanceStatus.NONE,
    val enhanceProgress: Float = 0f,
    val tags: List<String> = emptyList()
) {
    val activePlayableUri: String
        get() = enhancedUri ?: contentUri

    val aspectRatio: Float
        get() = if (width > 0 && height > 0) {
            (width.toFloat() / height.toFloat()).coerceIn(0.45f, 1.5f)
        } else {
            0.5625f
        }

    val isVideo: Boolean
        get() = mediaType == WallpaperMediaType.VIDEO
}

@Serializable
data class CatalogManifest(
    val version: Int = 1,
    val updatedAt: String = "",
    val baseAssetsUrl: String = "",
    val items: List<CatalogEntry> = emptyList()
)

@Serializable
data class CatalogEntry(
    val id: String,
    val title: String,
    val author: String = "Kromiv Originals",
    val license: String = "CC0 1.0",
    val category: String,
    val mediaType: WallpaperMediaType,
    val format: WallpaperFormat,
    val thumbnailUrl: String,
    val previewUrl: String? = null,
    val downloadUrl: String,
    val width: Int,
    val height: Int,
    val durationMs: Long = 0L,
    val bitrateBps: Long = 0L,
    val fps: Float = 0f,
    val fileSizeBytes: Long = 0L,
    val sha256: String? = null,
    val popularity: Int = 0,
    val createdAtEpochMs: Long = 0L,
    val tags: List<String> = emptyList()
) {
    fun toWallpaperItem(
        isFavorite: Boolean = false,
        downloadedLocalUri: String? = null,
        enhancedLocalUri: String? = null,
        enhanceStatus: EnhanceStatus = EnhanceStatus.NONE,
        enhanceProgress: Float = 0f
    ): WallpaperItem = WallpaperItem(
        id = id,
        title = title,
        author = author,
        license = license,
        category = WallpaperCategory.fromSlug(category),
        mediaType = mediaType,
        format = format,
        source = if (downloadedLocalUri != null) WallpaperSource.STORE_DOWNLOAD else WallpaperSource.STORE_REMOTE,
        thumbnailUri = thumbnailUrl,
        previewUri = previewUrl,
        contentUri = downloadedLocalUri ?: downloadUrl,
        enhancedUri = enhancedLocalUri,
        width = width,
        height = height,
        durationMs = durationMs,
        bitrateBps = bitrateBps,
        fps = fps,
        fileSizeBytes = fileSizeBytes,
        sha256 = sha256,
        isFavorite = isFavorite,
        isDownloaded = downloadedLocalUri != null,
        popularityScore = popularity,
        createdAtEpochMs = createdAtEpochMs,
        enhanceStatus = enhanceStatus,
        enhanceProgress = enhanceProgress,
        tags = tags
    )
}

object EnhanceAdvisor {
    private const val MIN_OPTIMAL_SHORT_EDGE = 1080
    private const val MIN_OPTIMAL_BITRATE_BPS = 2_800_000L

    fun shouldRecommendEnhance(
        mediaType: WallpaperMediaType,
        width: Int,
        height: Int,
        bitrateBps: Long,
        codec: String?
    ): Boolean {
        if (mediaType != WallpaperMediaType.VIDEO) return false
        val shortEdge = minOf(width, height)
        if (shortEdge in 1 until MIN_OPTIMAL_SHORT_EDGE) return true
        if (bitrateBps in 1 until MIN_OPTIMAL_BITRATE_BPS) return true
        val normalizedCodec = codec?.lowercase().orEmpty()
        if (normalizedCodec.contains("mpeg4") ||
            normalizedCodec.contains("h263") ||
            normalizedCodec.contains("vp8")
        ) {
            return true
        }
        return false
    }

    fun computeTargetDimensions(
        srcWidth: Int,
        srcHeight: Int,
        targetShortEdge: Int
    ): Pair<Int, Int> {
        if (srcWidth <= 0 || srcHeight <= 0) {
            return targetShortEdge to (targetShortEdge * 16 / 9)
        }
        val isPortrait = srcHeight >= srcWidth
        val scale = if (isPortrait) {
            targetShortEdge.toDouble() / srcWidth.toDouble()
        } else {
            targetShortEdge.toDouble() / srcHeight.toDouble()
        }
        val effectiveScale = if (scale < 1.0) 1.0 else scale.coerceAtMost(2.5)
        val rawW = (srcWidth * effectiveScale).toInt()
        val rawH = (srcHeight * effectiveScale).toInt()
        val evenW = (rawW + 1) and 1.inv()
        val evenH = (rawH + 1) and 1.inv()
        return evenW.coerceAtLeast(2) to evenH.coerceAtLeast(2)
    }
}
