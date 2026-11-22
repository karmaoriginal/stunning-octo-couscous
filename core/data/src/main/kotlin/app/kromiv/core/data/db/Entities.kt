package app.kromiv.core.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import app.kromiv.core.model.EnhanceStatus
import app.kromiv.core.model.WallpaperCategory
import app.kromiv.core.model.WallpaperFormat
import app.kromiv.core.model.WallpaperItem
import app.kromiv.core.model.WallpaperMediaType
import app.kromiv.core.model.WallpaperSource

@Entity(tableName = "wallpapers")
data class WallpaperEntity(
    @PrimaryKey val id: String,
    val title: String,
    val author: String,
    val license: String,
    val category: String,
    val mediaType: String,
    val format: String,
    val source: String,
    val thumbnailUri: String,
    val previewUri: String?,
    val localPath: String,
    val enhancedPath: String?,
    val width: Int,
    val height: Int,
    val durationMs: Long,
    val bitrateBps: Long,
    val fps: Float,
    val codec: String?,
    val fileSizeBytes: Long,
    val enhancedSizeBytes: Long,
    val sha256: String?,
    val createdAtEpochMs: Long,
    val enhanceStatus: String,
    val enhanceProgress: Float,
    val tagsCsv: String
) {
    fun toDomain(isFavorite: Boolean): WallpaperItem = WallpaperItem(
        id = id,
        title = title,
        author = author,
        license = license,
        category = WallpaperCategory.fromSlug(category),
        mediaType = runCatching { WallpaperMediaType.valueOf(mediaType) }.getOrDefault(WallpaperMediaType.IMAGE),
        format = runCatching { WallpaperFormat.valueOf(format) }.getOrDefault(WallpaperFormat.UNKNOWN),
        source = runCatching { WallpaperSource.valueOf(source) }.getOrDefault(WallpaperSource.LOCAL_IMPORT),
        thumbnailUri = thumbnailUri,
        previewUri = previewUri,
        contentUri = localPath,
        enhancedUri = enhancedPath,
        width = width,
        height = height,
        durationMs = durationMs,
        bitrateBps = bitrateBps,
        fps = fps,
        codec = codec,
        fileSizeBytes = fileSizeBytes,
        enhancedSizeBytes = enhancedSizeBytes,
        sha256 = sha256,
        isFavorite = isFavorite,
        isDownloaded = true,
        createdAtEpochMs = createdAtEpochMs,
        enhanceStatus = runCatching { EnhanceStatus.valueOf(enhanceStatus) }.getOrDefault(EnhanceStatus.NONE),
        enhanceProgress = enhanceProgress,
        tags = if (tagsCsv.isBlank()) emptyList() else tagsCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    )
}

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val wallpaperId: String,
    val addedAtEpochMs: Long = System.currentTimeMillis()
)
