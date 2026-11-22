package app.kromiv.core.data.library

import android.content.Context
import android.net.Uri
import app.kromiv.core.data.db.FavoriteDao
import app.kromiv.core.data.db.WallpaperDao
import app.kromiv.core.data.db.WallpaperEntity
import app.kromiv.core.data.media.AndroidMediaInspector
import app.kromiv.core.model.EnhanceAdvisor
import app.kromiv.core.model.EnhanceStatus
import app.kromiv.core.model.StorageStats
import app.kromiv.core.model.WallpaperCategory
import app.kromiv.core.model.WallpaperFormat
import app.kromiv.core.model.WallpaperItem
import app.kromiv.core.model.WallpaperSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class LibraryRepository(
    private val context: Context,
    private val wallpaperDao: WallpaperDao,
    private val favoriteDao: FavoriteDao,
    private val mediaInspector: AndroidMediaInspector
) {
    val importsDir = File(context.filesDir, "wallpapers/imports").apply { mkdirs() }
    val storeDir = File(context.filesDir, "wallpapers/store").apply { mkdirs() }
    val enhancedDir = File(context.filesDir, "wallpapers/enhanced").apply { mkdirs() }
    val partDir = File(context.filesDir, "downloads_part").apply { mkdirs() }

    fun observeLibraryWallpapers(): Flow<List<WallpaperItem>> = combine(
        wallpaperDao.observeAll(),
        favoriteDao.observeFavoriteIds()
    ) { entities, favorites ->
        val favSet = favorites.toHashSet()
        entities.map { it.toDomain(isFavorite = favSet.contains(it.id)) }
    }

    fun observeWallpaperById(id: String): Flow<WallpaperItem?> = combine(
        wallpaperDao.observeById(id),
        favoriteDao.observeFavoriteIds()
    ) { entity, favorites ->
        entity?.toDomain(isFavorite = favorites.contains(entity.id))
    }

    suspend fun importFromUri(uri: Uri): Result<WallpaperItem> = withContext(Dispatchers.IO) {
        runCatching {
            val (displayName, _) = mediaInspector.queryDisplayNameAndSize(uri)
            val detectedFormat = mediaInspector.detectFormat(uri, displayName)
            val tempFile = File(context.cacheDir, "import_tmp_${UUID.randomUUID()}.${detectedFormat.extension}")
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output, 16 * 1024)
                    }
                } ?: error("No se pudo leer el archivo seleccionado")

                val sha256 = AndroidMediaInspector.computeSha256(tempFile)
                val existing = wallpaperDao.getBySha256(sha256)
                if (existing != null && File(existing.localPath).exists()) {
                    tempFile.delete()
                    val isFav = favoriteDao.isFavorite(existing.id)
                    return@runCatching existing.toDomain(isFav)
                }

                val finalFormat = if (detectedFormat != WallpaperFormat.UNKNOWN) {
                    detectedFormat
                } else {
                    mediaInspector.probeLocalFile(tempFile, displayName).format
                }
                val id = "local_${sha256.take(16)}"
                val destFile = File(importsDir, "$id.${finalFormat.extension}")
                if (destFile.exists()) destFile.delete()
                if (!tempFile.renameTo(destFile)) {
                    tempFile.copyTo(destFile, overwrite = true)
                    tempFile.delete()
                }

                val probe = mediaInspector.probeLocalFile(destFile, displayName, finalFormat)
                val recommendEnhance = EnhanceAdvisor.shouldRecommendEnhance(
                    mediaType = probe.mediaType,
                    width = probe.width,
                    height = probe.height,
                    bitrateBps = probe.bitrateBps,
                    codec = probe.codec
                )
                val entity = WallpaperEntity(
                    id = id,
                    title = probe.title,
                    author = "Importado",
                    license = "Personal",
                    category = WallpaperCategory.ABSTRACT.slug,
                    mediaType = probe.mediaType.name,
                    format = probe.format.name,
                    source = WallpaperSource.LOCAL_IMPORT.name,
                    thumbnailUri = destFile.absolutePath,
                    previewUri = destFile.absolutePath,
                    localPath = destFile.absolutePath,
                    enhancedPath = null,
                    width = probe.width,
                    height = probe.height,
                    durationMs = probe.durationMs,
                    bitrateBps = probe.bitrateBps,
                    fps = probe.fps,
                    codec = probe.codec,
                    fileSizeBytes = destFile.length(),
                    enhancedSizeBytes = 0L,
                    sha256 = sha256,
                    createdAtEpochMs = System.currentTimeMillis(),
                    enhanceStatus = if (recommendEnhance) EnhanceStatus.RECOMMENDED.name else EnhanceStatus.NONE.name,
                    enhanceProgress = 0f,
                    tagsCsv = "local,${probe.format.extension}"
                )
                wallpaperDao.upsert(entity)
                entity.toDomain(isFavorite = false)
            } finally {
                if (tempFile.exists()) tempFile.delete()
            }
        }
    }

    suspend fun rename(id: String, newTitle: String) = withContext(Dispatchers.IO) {
        val sanitized = newTitle.trim()
        if (sanitized.isNotEmpty()) {
            wallpaperDao.updateTitle(id, sanitized)
        }
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        val entity = wallpaperDao.getById(id) ?: return@withContext
        runCatching { File(entity.localPath).takeIf { it.exists() }?.delete() }
        entity.enhancedPath?.let { path ->
            runCatching { File(path).takeIf { it.exists() }?.delete() }
        }
        wallpaperDao.deleteById(id)
        favoriteDao.remove(id)
    }

    suspend fun computeStorageStats(imageCacheBytes: Long): StorageStats = withContext(Dispatchers.IO) {
        val importsBytes = dirSize(importsDir)
        val downloadsBytes = dirSize(storeDir)
        val enhancedBytes = dirSize(enhancedDir)
        val partialBytes = dirSize(partDir)
        val count = wallpaperDao.count()
        StorageStats(
            importsBytes = importsBytes,
            downloadsBytes = downloadsBytes,
            enhancedBytes = enhancedBytes,
            partialDownloadsBytes = partialBytes,
            imageCacheBytes = imageCacheBytes,
            itemCount = count
        )
    }

    suspend fun clearPartialAndTempCache() = withContext(Dispatchers.IO) {
        partDir.listFiles()?.forEach { it.delete() }
        context.cacheDir.listFiles()?.forEach { file ->
            if (file.name.startsWith("import_tmp_") || file.name.startsWith("enhance_tmp_")) {
                file.deleteRecursively()
            }
        }
    }

    private fun dirSize(dir: File): Long {
        if (!dir.exists()) return 0L
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }
}
