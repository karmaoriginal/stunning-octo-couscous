@file:OptIn(ExperimentalSharedTransitionApi::class)

package app.kromiv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kromiv.R
import app.kromiv.core.media.wallpaper.LiveWallpaperLauncher
import app.kromiv.core.model.WallpaperItem
import app.kromiv.ui.screens.EnhanceScreen
import app.kromiv.ui.screens.ExploreScreen
import app.kromiv.ui.screens.LibraryScreen
import app.kromiv.ui.screens.PreviewScreen
import app.kromiv.ui.screens.SettingsScreen
import app.kromiv.ui.theme.KromivTheme

private enum class KromivTab(val label: String, val iconRes: Int) {
    EXPLORE("Explorar", R.drawable.ic_explore),
    LIBRARY("Biblioteca", R.drawable.ic_library),
    ENHANCE("Mejorar", R.drawable.ic_enhance),
    SETTINGS("Ajustes", R.drawable.ic_settings)
}

private data class PreviewSelection(
    val itemId: String,
    val sharedKeyPrefix: String,
    val fallbackItem: WallpaperItem
)

@Composable
fun KromivRoot(viewModel: KromivViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var activeTab by remember { mutableStateOf(KromivTab.EXPLORE) }
    var previewSelection by remember { mutableStateOf<PreviewSelection?>(null) }
    val exploreGridState = rememberLazyStaggeredGridState()
    val libraryGridState = rememberLazyStaggeredGridState()

    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.launchLivePicker.collect {
            LiveWallpaperLauncher.launchLiveWallpaperPicker(context)
        }
    }

    BackHandler(enabled = previewSelection != null) {
        previewSelection = null
    }

    // Keep previewed item synced with latest DB/download/favorite state
    val currentPreviewItem = previewSelection?.let { sel ->
        uiState.libraryItems.firstOrNull { it.id == sel.itemId }
            ?: uiState.storeItems.firstOrNull { it.id == sel.itemId }
            ?: sel.fallbackItem
    }

    KromivTheme(
        themeMode = uiState.settings.themeMode,
        amoledDark = uiState.settings.amoledDark,
        dynamicColor = uiState.settings.dynamicColor
    ) {
        SharedTransitionLayout(modifier = Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = currentPreviewItem,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "previewTransition"
            ) { previewItem ->
                if (previewItem != null && previewSelection != null) {
                    PreviewScreen(
                        item = previewItem,
                        sharedKeyPrefix = previewSelection!!.sharedKeyPrefix,
                        downloadProgress = uiState.downloadProgress[previewItem.id],
                        defaultMuted = uiState.settings.muteLiveByDefault,
                        isApplyingWallpaper = uiState.isApplyingWallpaper,
                        sharedTransitionScope = this@SharedTransitionLayout,
                        animatedVisibilityScope = this@AnimatedContent,
                        onBack = { previewSelection = null },
                        onToggleFavorite = { viewModel.toggleFavorite(previewItem.id) },
                        onDownloadOrPause = { viewModel.handleDownloadOrPause(previewItem) },
                        onEnhanceClick = {
                            previewSelection = null
                            activeTab = KromivTab.ENHANCE
                        },
                        onApplyStatic = { target, crop ->
                            viewModel.applyStaticWallpaper(previewItem, target, crop)
                        },
                        onApplyLive = { muted ->
                            viewModel.applyLiveWallpaper(previewItem, muted)
                        }
                    )
                } else {
                    Scaffold(
                        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                        bottomBar = {
                            NavigationBar {
                                KromivTab.entries.forEach { tab ->
                                    NavigationBarItem(
                                        selected = activeTab == tab,
                                        onClick = { activeTab = tab },
                                        icon = {
                                            Icon(
                                                painter = painterResource(tab.iconRes),
                                                contentDescription = tab.label,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        },
                                        label = { Text(tab.label) }
                                    )
                                }
                            }
                        }
                    ) { innerPadding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        ) {
                            when (activeTab) {
                                KromivTab.EXPLORE -> ExploreScreen(
                                    uiState = uiState,
                                    gridState = exploreGridState,
                                    sharedTransitionScope = this@SharedTransitionLayout,
                                    animatedVisibilityScope = this@AnimatedContent,
                                    onRefresh = viewModel::refreshCatalog,
                                    onSearchChange = viewModel::updateSearchQuery,
                                    onCategorySelect = viewModel::selectCategory,
                                    onSortSelect = viewModel::selectSort,
                                    onMediaFilterSelect = viewModel::selectMediaFilter,
                                    onItemClick = { item ->
                                        previewSelection = PreviewSelection(item.id, "store", item)
                                    },
                                    onToggleFavorite = viewModel::toggleFavorite,
                                    onDownloadClick = viewModel::handleDownloadOrPause
                                )
                                KromivTab.LIBRARY -> LibraryScreen(
                                    uiState = uiState,
                                    gridState = libraryGridState,
                                    sharedTransitionScope = this@SharedTransitionLayout,
                                    animatedVisibilityScope = this@AnimatedContent,
                                    onSelectFilter = viewModel::selectLibraryFilter,
                                    onImportUris = viewModel::importUris,
                                    onClearCache = viewModel::clearAllCache,
                                    onItemClick = { item ->
                                        previewSelection = PreviewSelection(item.id, "lib", item)
                                    },
                                    onToggleFavorite = viewModel::toggleFavorite,
                                    onEnhanceClick = { activeTab = KromivTab.ENHANCE },
                                    onRename = viewModel::renameWallpaper,
                                    onDelete = viewModel::deleteWallpaper
                                )
                                KromivTab.ENHANCE -> EnhanceScreen(
                                    uiState = uiState,
                                    onEnqueueEnhance = viewModel::enqueueEnhance,
                                    onCancelEnhance = viewModel::cancelEnhance,
                                    onPreviewItem = { item ->
                                        previewSelection = PreviewSelection(item.id, "lib", item)
                                    }
                                )
                                KromivTab.SETTINGS -> SettingsScreen(
                                    uiState = uiState,
                                    onThemeModeChange = viewModel::setThemeMode,
                                    onAmoledChange = viewModel::setAmoledDark,
                                    onDynamicColorChange = viewModel::setDynamicColor,
                                    onFpsLimitChange = viewModel::setLiveFpsLimit,
                                    onMuteDefaultChange = viewModel::setMuteLiveByDefault,
                                    onPauseBatterySaverChange = viewModel::setPauseLiveOnBatterySaver,
                                    onWifiOnlyChange = viewModel::setWifiOnlyDownloads,
                                    onMaxCacheChange = viewModel::setMaxCacheMb,
                                    onAiUpscalerChange = viewModel::setAiUpscalerEnabled,
                                    onClearCache = viewModel::clearAllCache
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
