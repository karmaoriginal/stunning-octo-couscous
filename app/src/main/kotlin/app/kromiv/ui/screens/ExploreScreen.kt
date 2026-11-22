@file:OptIn(ExperimentalSharedTransitionApi::class)

package app.kromiv.ui.screens

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kromiv.R
import app.kromiv.core.model.CatalogSort
import app.kromiv.core.model.MediaTypeFilter
import app.kromiv.core.model.WallpaperCategory
import app.kromiv.core.model.WallpaperItem
import app.kromiv.core.model.WallpaperMediaType
import app.kromiv.ui.KromivUiState
import app.kromiv.ui.components.BadgePill
import app.kromiv.ui.components.KromivThumbnailImage
import app.kromiv.ui.components.ShimmerBox
import app.kromiv.ui.components.WallpaperCard
import app.kromiv.ui.components.bounceClick
import kotlin.math.absoluteValue

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ExploreScreen(
    uiState: KromivUiState,
    gridState: androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onRefresh: () -> Unit,
    onSearchChange: (String) -> Unit,
    onCategorySelect: (WallpaperCategory) -> Unit,
    onSortSelect: (CatalogSort) -> Unit,
    onMediaFilterSelect: (MediaTypeFilter) -> Unit,
    onItemClick: (WallpaperItem) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onDownloadClick: (WallpaperItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val filters = uiState.filterState

    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Adaptive(164.dp),
        state = gridState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalItemSpacing = 12.dp
    ) {
        item(span = StaggeredGridItemSpan.FullLine, key = "header") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Kromiv",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Catálogo libre CC0 · Estáticos y Live 60 fps",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onRefresh,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_refresh),
                            contentDescription = "Actualizar catálogo",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Search bar
                OutlinedTextField(
                    value = filters.query,
                    onValueChange = onSearchChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Buscar fondos, etiquetas, categorías…") },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_search),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    trailingIcon = {
                        if (filters.query.isNotEmpty()) {
                            IconButton(onClick = { onSearchChange("") }) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_close),
                                    contentDescription = "Limpiar búsqueda",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(999.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.Transparent
                    )
                )
            }
        }

        // Featured parallax hero pager when not actively searching
        if (filters.query.isBlank() && filters.category == WallpaperCategory.ALL && uiState.featuredItems.isNotEmpty()) {
            item(span = StaggeredGridItemSpan.FullLine, key = "featured_pager") {
                val pagerState = rememberPagerState(pageCount = { uiState.featuredItems.size })
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Destacados",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    HorizontalPager(
                        state = pagerState,
                        contentPadding = PaddingValues(end = 36.dp),
                        pageSpacing = 12.dp
                    ) { page ->
                        val item = uiState.featuredItems[page]
                        val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
                            .absoluteValue.coerceIn(0f, 1f)
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(190.dp)
                                .bounceClick { onItemClick(item) }
                                .graphicsLayer {
                                    val scale = 1f - (pageOffset * 0.05f)
                                    scaleX = scale
                                    scaleY = scale
                                },
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                KromivThumbnailImage(
                                    uri = item.thumbnailUri,
                                    contentDescription = item.title,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer {
                                            translationX = pageOffset * 28f
                                            scaleX = 1.08f
                                            scaleY = 1.08f
                                        }
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(Color.Transparent, Color(0xD9000000))
                                            )
                                        )
                                )
                                Row(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Bottom
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            BadgePill(
                                                text = item.category.displayNameEs,
                                                containerColor = Color(0xAAFFFFFF),
                                                contentColor = Color.Black
                                            )
                                            if (item.mediaType == WallpaperMediaType.VIDEO) {
                                                BadgePill(
                                                    text = "LIVE MP4",
                                                    containerColor = Color(0xCC0D9488),
                                                    contentColor = Color.White
                                                )
                                            }
                                        }
                                        Spacer(Modifier.height(6.dp))
                                        Text(
                                            text = item.title,
                                            color = Color.White,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    BadgePill(
                                        text = item.license,
                                        containerColor = Color(0x66000000),
                                        contentColor = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Category + Sort + Type chips
        item(span = StaggeredGridItemSpan.FullLine, key = "filters_bar") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(WallpaperCategory.storeCategories, key = { it.slug }) { category ->
                        FilterChip(
                            selected = filters.category == category,
                            onClick = { onCategorySelect(category) },
                            label = { Text(category.displayNameEs) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val sortItems = listOf(
                        CatalogSort.POPULAR to "Populares",
                        CatalogSort.RECENT to "Recientes",
                        CatalogSort.FAVORITES to "Favoritos"
                    )
                    items(sortItems, key = { it.first.name }) { (sort, label) ->
                        FilterChip(
                            selected = filters.sort == sort,
                            onClick = { onSortSelect(sort) },
                            label = { Text(label) }
                        )
                    }

                    item(key = "divider_spacer") {
                        Spacer(Modifier.width(4.dp))
                    }

                    val mediaItems = listOf(
                        MediaTypeFilter.ALL to "Todo",
                        MediaTypeFilter.PHOTOS to "Fotos",
                        MediaTypeFilter.VIDEOS to "Videos"
                    )
                    items(mediaItems, key = { it.first.name }) { (media, label) ->
                        FilterChip(
                            selected = filters.mediaFilter == media,
                            onClick = { onMediaFilterSelect(media) },
                            label = { Text(label) }
                        )
                    }
                }
            }
        }

        if (uiState.isCatalogLoading && uiState.storeItems.isEmpty()) {
            items(6, key = { "shimmer_$it" }) { idx ->
                ShimmerBox(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(if (idx % 2 == 0) 0.56f else 0.72f)
                        .clip(RoundedCornerShape(20.dp))
                )
            }
        } else if (uiState.storeItems.isEmpty()) {
            item(span = StaggeredGridItemSpan.FullLine, key = "empty_store") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No hay fondos que coincidan con tu filtro",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(
                items = uiState.storeItems,
                key = { it.id }
            ) { item ->
                WallpaperCard(
                    item = item,
                    downloadProgress = uiState.downloadProgress[item.id],
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                    sharedKeyPrefix = "store",
                    onClick = { onItemClick(item) },
                    onToggleFavorite = { onToggleFavorite(item.id) },
                    onDownloadAction = { onDownloadClick(item) }
                )
            }
        }
    }
}
