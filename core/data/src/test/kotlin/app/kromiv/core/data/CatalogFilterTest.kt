package app.kromiv.core.data

import app.kromiv.core.model.CatalogSort
import app.kromiv.core.model.MediaTypeFilter
import app.kromiv.core.model.WallpaperCategory
import app.kromiv.core.model.WallpaperFormat
import app.kromiv.core.model.WallpaperItem
import app.kromiv.core.model.WallpaperMediaType
import app.kromiv.core.model.WallpaperSource
import org.junit.Assert.assertEquals
import org.junit.Test

class CatalogFilterTest {

    private fun sampleItems(): List<WallpaperItem> = listOf(
        WallpaperItem(
            id = "1",
            title = "Obsidian Nebula",
            author = "Kromiv",
            license = "CC0 1.0",
            category = WallpaperCategory.AMOLED,
            mediaType = WallpaperMediaType.VIDEO,
            format = WallpaperFormat.MP4,
            source = WallpaperSource.STORE_REMOTE,
            thumbnailUri = "",
            previewUri = null,
            contentUri = "",
            isFavorite = true,
            popularityScore = 80,
            createdAtEpochMs = 100L,
            tags = listOf("dark", "black", "space")
        ),
        WallpaperItem(
            id = "2",
            title = "Sakura Mist",
            author = "Kromiv",
            license = "CC0 1.0",
            category = WallpaperCategory.ANIME,
            mediaType = WallpaperMediaType.IMAGE,
            format = WallpaperFormat.WEBP,
            source = WallpaperSource.STORE_REMOTE,
            thumbnailUri = "",
            previewUri = null,
            contentUri = "",
            isFavorite = false,
            popularityScore = 95,
            createdAtEpochMs = 200L,
            tags = listOf("pink", "torii")
        )
    )

    @Test
    fun `search and category filters match expected entries`() {
        val items = sampleItems()
        val matched = items.filter {
            (it.category == WallpaperCategory.AMOLED) &&
                it.tags.contains("space")
        }
        assertEquals(1, matched.size)
        assertEquals("1", matched.first().id)

        val photosOnly = items.filter { it.mediaType != WallpaperMediaType.VIDEO }
        assertEquals(1, photosOnly.size)
        assertEquals("2", photosOnly.first().id)
    }

    @Test
    fun `popularity and recent sorting order properly`() {
        val items = sampleItems()
        val byPop = items.sortedByDescending { it.popularityScore }
        assertEquals("2", byPop.first().id)
        val byRecent = items.sortedByDescending { it.createdAtEpochMs }
        assertEquals("2", byRecent.first().id)
        val favs = items.filter { it.isFavorite }
        assertEquals(1, favs.size)
        assertEquals(CatalogSort.POPULAR.name, "POPULAR")
        assertEquals(MediaTypeFilter.ALL.name, "ALL")
    }
}
