package app.kromiv.core.data.network

import android.content.Context
import app.kromiv.core.data.db.FavoriteDao
import app.kromiv.core.data.db.FavoriteEntity
import app.kromiv.core.data.db.WallpaperDao
import app.kromiv.core.model.CatalogEntry
import app.kromiv.core.model.CatalogManifest
import app.kromiv.core.model.CatalogSort
import app.kromiv.core.model.EnhanceStatus
import app.kromiv.core.model.MediaTypeFilter
import app.kromiv.core.model.WallpaperCategory
import app.kromiv.core.model.WallpaperItem
import app.kromiv.core.model.WallpaperMediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

object CatalogUrls {
    const val PAGES_CATALOG_URL = "https://karmaoriginal.github.io/stunning-octo-couscous/catalog.json"
    const val RAW_MAIN_CATALOG_URL = "https://raw.githubusercontent.com/karmaoriginal/stunning-octo-couscous/main/docs/catalog.json"
    const val RAW_BRANCH_CATALOG_URL = "https://raw.githubusercontent.com/karmaoriginal/stunning-octo-couscous/arena/618d1c14-stunning-octo-couscous/docs/catalog.json"
}

class CatalogRepository(
    private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val wallpaperDao: WallpaperDao,
    private val favoriteDao: FavoriteDao
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val cacheFile = File(context.filesDir, "catalog_cache.json")
    private val manifestEntriesFlow = MutableStateFlow<List<CatalogEntry>>(emptyList())

    val catalogEntries: Flow<List<CatalogEntry>> = manifestEntriesFlow

    fun observeCatalogWallpapers(): Flow<List<WallpaperItem>> = combine(
        manifestEntriesFlow,
        favoriteDao.observeFavoriteIds(),
        wallpaperDao.observeAll()
    ) { entries, favoriteIds, localEntities ->
        val favSet = favoriteIds.toHashSet()
        val localById = localEntities.associateBy { it.id }
        entries.map { entry ->
            val local = localById[entry.id]
            entry.toWallpaperItem(
                isFavorite = favSet.contains(entry.id),
                downloadedLocalUri = local?.localPath,
                enhancedLocalUri = local?.enhancedPath,
                enhanceStatus = local?.let {
                    runCatching { EnhanceStatus.valueOf(it.enhanceStatus) }.getOrDefault(EnhanceStatus.NONE)
                } ?: EnhanceStatus.NONE,
                enhanceProgress = local?.enhanceProgress ?: 0f
            )
        }
    }

    suspend fun loadInitialAndRefresh(customUrl: String? = null): Result<Int> = withContext(Dispatchers.IO) {
        if (manifestEntriesFlow.value.isEmpty()) {
            loadLocalOrAssetCatalog()?.let { manifestEntriesFlow.value = it.items }
        }
        val candidates = buildList {
            if (!customUrl.isNullOrBlank()) add(customUrl)
            add(CatalogUrls.PAGES_CATALOG_URL)
            add(CatalogUrls.RAW_MAIN_CATALOG_URL)
            add(CatalogUrls.RAW_BRANCH_CATALOG_URL)
        }
        var lastError: Throwable? = null
        for (url in candidates) {
            val result = runCatching {
                val req = Request.Builder().url(url).build()
                okHttpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) error("HTTP ${resp.code}")
                    val body = resp.body?.string()?.takeIf { it.isNotBlank() } ?: error("Empty catalog")
                    val parsed = json.decodeFromString<CatalogManifest>(body)
                    cacheFile.writeText(body)
                    parsed
                }
            }
            if (result.isSuccess) {
                val parsed = result.getOrThrow()
                manifestEntriesFlow.value = parsed.items
                return@withContext Result.success(parsed.items.size)
            } else {
                lastError = result.exceptionOrNull()
            }
        }
        if (manifestEntriesFlow.value.isNotEmpty()) {
            Result.success(manifestEntriesFlow.value.size)
        } else {
            Result.failure(lastError ?: IllegalStateException("Catalog unavailable"))
        }
    }

    private fun loadLocalOrAssetCatalog(): CatalogManifest? {
        if (cacheFile.exists()) {
            runCatching {
                return json.decodeFromString<CatalogManifest>(cacheFile.readText())
            }
        }
        return runCatching {
            val raw = context.assets.open("catalog_fallback.json").bufferedReader().use { it.readText() }
            json.decodeFromString<CatalogManifest>(raw)
        }.getOrNull()
    }

    suspend fun toggleFavorite(id: String) {
        if (favoriteDao.isFavorite(id)) {
            favoriteDao.remove(id)
        } else {
            favoriteDao.add(FavoriteEntity(wallpaperId = id))
        }
    }

    fun filterAndSort(
        items: List<WallpaperItem>,
        query: String,
        category: WallpaperCategory,
        mediaFilter: MediaTypeFilter,
        sort: CatalogSort
    ): List<WallpaperItem> {
        val q = query.trim().lowercase()
        val filtered = items.filter { item ->
            val categoryOk = category == WallpaperCategory.ALL || item.category == category
            val mediaOk = when (mediaFilter) {
                MediaTypeFilter.ALL -> true
                MediaTypeFilter.PHOTOS -> item.mediaType != WallpaperMediaType.VIDEO
                MediaTypeFilter.VIDEOS -> item.mediaType == WallpaperMediaType.VIDEO
            }
            val sortFavOk = sort != CatalogSort.FAVORITES || item.isFavorite
            val queryOk = q.isEmpty() ||
                item.title.lowercase().contains(q) ||
                item.author.lowercase().contains(q) ||
                item.category.displayNameEs.lowercase().contains(q) ||
                item.tags.any { it.lowercase().contains(q) }
            categoryOk && mediaOk && sortFavOk && queryOk
        }
        return when (sort) {
            CatalogSort.POPULAR -> filtered.sortedByDescending { it.popularityScore }
            CatalogSort.RECENT -> filtered.sortedByDescending { it.createdAtEpochMs }
            CatalogSort.FAVORITES -> filtered.sortedByDescending { it.popularityScore }
        }
    }
}
