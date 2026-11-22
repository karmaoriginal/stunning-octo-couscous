package app.kromiv.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.kromiv.core.media.wallpaper.NormalizedCropRect
import app.kromiv.core.model.AppSettings
import app.kromiv.core.model.CatalogEntry
import app.kromiv.core.model.CatalogSort
import app.kromiv.core.model.DownloadProgress
import app.kromiv.core.model.DownloadStatus
import app.kromiv.core.model.EnhanceOptions
import app.kromiv.core.model.EnhanceStatus
import app.kromiv.core.model.MediaTypeFilter
import app.kromiv.core.model.StorageStats
import app.kromiv.core.model.ThemeMode
import app.kromiv.core.model.WallpaperCategory
import app.kromiv.core.model.WallpaperItem
import app.kromiv.core.model.WallpaperMediaType
import app.kromiv.core.model.WallpaperSource
import app.kromiv.core.model.WallpaperTarget
import app.kromiv.di.AppContainer
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class LibraryFilter(val label: String) {
    ALL("Todos"),
    IMPORTED("Importados"),
    DOWNLOADED("Descargas"),
    FAVORITES("Favoritos")
}

data class FilterState(
    val query: String = "",
    val category: WallpaperCategory = WallpaperCategory.ALL,
    val mediaFilter: MediaTypeFilter = MediaTypeFilter.ALL,
    val sort: CatalogSort = CatalogSort.POPULAR,
    val libraryFilter: LibraryFilter = LibraryFilter.ALL
)

data class KromivUiState(
    val storeItems: List<WallpaperItem> = emptyList(),
    val featuredItems: List<WallpaperItem> = emptyList(),
    val libraryItems: List<WallpaperItem> = emptyList(),
    val enhanceCandidates: List<WallpaperItem> = emptyList(),
    val filterState: FilterState = FilterState(),
    val downloadProgress: Map<String, DownloadProgress> = emptyMap(),
    val settings: AppSettings = AppSettings(),
    val storageStats: StorageStats = StorageStats(),
    val isCatalogLoading: Boolean = true,
    val isImporting: Boolean = false,
    val isApplyingWallpaper: Boolean = false
)

class KromivViewModel(private val container: AppContainer) : ViewModel() {

    private val filterState = MutableStateFlow(FilterState())
    private val catalogEntriesMap = MutableStateFlow<Map<String, CatalogEntry>>(emptyMap())
    private val isCatalogLoading = MutableStateFlow(true)
    private val isImporting = MutableStateFlow(false)
    private val isApplying = MutableStateFlow(false)
    private val storageStats = MutableStateFlow(StorageStats())

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val _launchLivePicker = MutableSharedFlow<Unit>(extraBufferCapacity = 2)
    val launchLivePicker: SharedFlow<Unit> = _launchLivePicker.asSharedFlow()

    private val statusFlags = combine(
        isCatalogLoading,
        isImporting,
        isApplying,
        storageStats
    ) { catLoad, imp, app, stats ->
        Tuple4(catLoad, imp, app, stats)
    }

    val uiState: StateFlow<KromivUiState> = combine(
        container.catalogRepository.observeCatalogWallpapers(),
        container.libraryRepository.observeLibraryWallpapers(),
        filterState,
        container.downloadManager.progress,
        container.settingsRepository.settings,
        statusFlags
    ) { flows ->
        @Suppress("UNCHECKED_CAST")
        val catalogItems = flows[0] as List<WallpaperItem>
        @Suppress("UNCHECKED_CAST")
        val libraryAll = flows[1] as List<WallpaperItem>
        val filters = flows[2] as FilterState
        @Suppress("UNCHECKED_CAST")
        val downloads = flows[3] as Map<String, DownloadProgress>
        val settings = flows[4] as AppSettings
        @Suppress("UNCHECKED_CAST")
        val flags = flows[5] as Tuple4<Boolean, Boolean, Boolean, StorageStats>

        val filteredStore = container.catalogRepository.filterAndSort(
            items = catalogItems,
            query = filters.query,
            category = filters.category,
            mediaFilter = filters.mediaFilter,
            sort = filters.sort
        )

        val featured = catalogItems
            .sortedByDescending { it.popularityScore }
            .take(5)

        val filteredLibrary = when (filters.libraryFilter) {
            LibraryFilter.ALL -> libraryAll
            LibraryFilter.IMPORTED -> libraryAll.filter { it.source == WallpaperSource.LOCAL_IMPORT }
            LibraryFilter.DOWNLOADED -> libraryAll.filter { it.source == WallpaperSource.STORE_DOWNLOAD }
            LibraryFilter.FAVORITES -> libraryAll.filter { it.isFavorite }
        }

        val enhanceCandidates = libraryAll
            .filter { it.mediaType == WallpaperMediaType.VIDEO }
            .sortedWith(
                compareBy<WallpaperItem>(
                    { it.enhanceStatus != EnhanceStatus.RUNNING && it.enhanceStatus != EnhanceStatus.QUEUED },
                    { it.enhanceStatus != EnhanceStatus.RECOMMENDED },
                    { -it.createdAtEpochMs }
                )
            )

        KromivUiState(
            storeItems = filteredStore,
            featuredItems = featured,
            libraryItems = filteredLibrary,
            enhanceCandidates = enhanceCandidates,
            filterState = filters,
            downloadProgress = downloads,
            settings = settings,
            storageStats = flags.fourth,
            isCatalogLoading = flags.first,
            isImporting = flags.second,
            isApplyingWallpaper = flags.third
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = KromivUiState()
    )

    init {
        viewModelScope.launch {
            container.catalogRepository.catalogEntries.collect { entries ->
                catalogEntriesMap.value = entries.associateBy { it.id }
            }
        }
        refreshCatalog()
        refreshStorageStats()
    }

    fun refreshCatalog() {
        viewModelScope.launch {
            isCatalogLoading.value = true
            val overrideUrl = container.settingsRepository.settings.first().catalogUrlOverride
            val res = container.catalogRepository.loadInitialAndRefresh(overrideUrl)
            isCatalogLoading.value = false
            if (res.isFailure) {
                _messages.tryEmit("Usando catálogo local sin conexión")
            }
        }
    }

    fun refreshStorageStats() {
        viewModelScope.launch {
            val imgCacheBytes = container.imageLoaderFactory.getDiskCacheSizeBytes()
            storageStats.value = container.libraryRepository.computeStorageStats(imgCacheBytes)
        }
    }

    fun updateSearchQuery(query: String) {
        filterState.update { it.copy(query = query) }
    }

    fun selectCategory(category: WallpaperCategory) {
        filterState.update { it.copy(category = category) }
    }

    fun selectMediaFilter(mediaFilter: MediaTypeFilter) {
        filterState.update { it.copy(mediaFilter = mediaFilter) }
    }

    fun selectSort(sort: CatalogSort) {
        filterState.update { it.copy(sort = sort) }
    }

    fun selectLibraryFilter(libraryFilter: LibraryFilter) {
        filterState.update { it.copy(libraryFilter = libraryFilter) }
    }

    fun toggleFavorite(id: String) {
        viewModelScope.launch {
            container.catalogRepository.toggleFavorite(id)
        }
    }

    fun handleDownloadOrPause(item: WallpaperItem) {
        val entry = catalogEntriesMap.value[item.id] ?: return
        val current = uiState.value.downloadProgress[item.id]
        if (current?.status == DownloadStatus.DOWNLOADING) {
            container.downloadManager.pause(item.id)
        } else {
            container.downloadManager.startOrResume(
                entry = entry,
                wifiOnly = uiState.value.settings.wifiOnlyDownloads
            )
        }
    }

    fun importUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            isImporting.value = true
            var importedCount = 0
            var recommendCount = 0
            for (uri in uris) {
                val res = container.libraryRepository.importFromUri(uri)
                if (res.isSuccess) {
                    importedCount++
                    if (res.getOrNull()?.enhanceStatus == EnhanceStatus.RECOMMENDED) {
                        recommendCount++
                    }
                }
            }
            isImporting.value = false
            refreshStorageStats()
            val msg = when {
                importedCount == 0 -> "No se pudo importar ningún archivo compatible"
                recommendCount > 0 -> "Importado ($importedCount). Video optimizable: puedes mejorarlo en Biblioteca o Mejorar"
                else -> "Importados $importedCount fondo(s) a tu biblioteca"
            }
            _messages.tryEmit(msg)
        }
    }

    fun renameWallpaper(id: String, newTitle: String) {
        viewModelScope.launch {
            container.libraryRepository.rename(id, newTitle)
            _messages.tryEmit("Nombre actualizado")
        }
    }

    fun deleteWallpaper(id: String) {
        viewModelScope.launch {
            container.libraryRepository.delete(id)
            refreshStorageStats()
            _messages.tryEmit("Fondo eliminado")
        }
    }

    fun enqueueEnhance(wallpaperId: String, options: EnhanceOptions) {
        viewModelScope.launch {
            container.enhanceScheduler.enqueueEnhance(wallpaperId, options)
            _messages.tryEmit("Mejora H.265 iniciada en segundo plano")
        }
    }

    fun cancelEnhance(wallpaperId: String) {
        viewModelScope.launch {
            container.enhanceScheduler.cancelEnhance(wallpaperId)
            _messages.tryEmit("Mejora cancelada")
        }
    }

    fun applyStaticWallpaper(
        item: WallpaperItem,
        target: WallpaperTarget,
        cropRect: NormalizedCropRect?
    ) {
        viewModelScope.launch {
            isApplying.value = true
            val res = container.staticWallpaperApplier.applyWallpaper(
                sourceUri = item.activePlayableUri,
                target = target,
                cropRect = cropRect
            )
            isApplying.value = false
            if (res.isSuccess) {
                val label = when (target) {
                    WallpaperTarget.HOME -> "pantalla de inicio"
                    WallpaperTarget.LOCK -> "pantalla de bloqueo"
                    WallpaperTarget.BOTH -> "inicio y bloqueo"
                }
                _messages.tryEmit("Fondo aplicado en $label")
            } else {
                _messages.tryEmit("Error al aplicar: ${res.exceptionOrNull()?.message ?: "desconocido"}")
            }
        }
    }

    fun applyLiveWallpaper(item: WallpaperItem, muted: Boolean) {
        viewModelScope.launch {
            if (!item.isDownloaded && item.source == WallpaperSource.STORE_REMOTE) {
                handleDownloadOrPause(item)
                _messages.tryEmit("Descargando el fondo animado en alta calidad…")
                return@launch
            }
            val fps = uiState.value.settings.liveFpsLimit
            container.settingsRepository.setActiveLiveWallpaper(
                uri = item.activePlayableUri,
                mediaType = item.mediaType,
                muted = muted,
                fps = fps
            )
            _launchLivePicker.tryEmit(Unit)
        }
    }

    fun clearAllCache() {
        viewModelScope.launch {
            container.libraryRepository.clearPartialAndTempCache()
            val maxMb = uiState.value.settings.maxCacheMb
            container.rebuildImageLoader(maxMb)
            refreshStorageStats()
            _messages.tryEmit("Caché limpiada correctamente")
        }
    }

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch {
        container.settingsRepository.setThemeMode(mode)
    }

    fun setAmoledDark(enabled: Boolean) = viewModelScope.launch {
        container.settingsRepository.setAmoledDark(enabled)
    }

    fun setDynamicColor(enabled: Boolean) = viewModelScope.launch {
        container.settingsRepository.setDynamicColor(enabled)
    }

    fun setLiveFpsLimit(fps: Int) = viewModelScope.launch {
        container.settingsRepository.setLiveFpsLimit(fps)
    }

    fun setMuteLiveByDefault(muted: Boolean) = viewModelScope.launch {
        container.settingsRepository.setMuteLiveByDefault(muted)
    }

    fun setPauseLiveOnBatterySaver(enabled: Boolean) = viewModelScope.launch {
        container.settingsRepository.setPauseOnBatterySaver(enabled)
    }

    fun setWifiOnlyDownloads(enabled: Boolean) = viewModelScope.launch {
        container.settingsRepository.setWifiOnlyDownloads(enabled)
    }

    fun setMaxCacheMb(mb: Int) = viewModelScope.launch {
        container.settingsRepository.setMaxCacheMb(mb)
        container.rebuildImageLoader(mb)
        refreshStorageStats()
    }

    fun setAiUpscalerEnabled(enabled: Boolean) = viewModelScope.launch {
        container.settingsRepository.setAiUpscalerEnabled(enabled)
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return KromivViewModel(container) as T
        }
    }
}

private data class Tuple4<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
