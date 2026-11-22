package app.kromiv

import app.kromiv.core.model.CatalogManifest
import app.kromiv.core.model.CatalogSort
import app.kromiv.core.model.MediaTypeFilter
import app.kromiv.core.model.WallpaperCategory
import app.kromiv.ui.FilterState
import app.kromiv.ui.LibraryFilter
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CatalogFallbackAndFilterStateTest {

    @Test
    fun `default filter state starts on popular all categories`() {
        val state = FilterState()
        assertEquals("", state.query)
        assertEquals(WallpaperCategory.ALL, state.category)
        assertEquals(MediaTypeFilter.ALL, state.mediaFilter)
        assertEquals(CatalogSort.POPULAR, state.sort)
        assertEquals(LibraryFilter.ALL, state.libraryFilter)
    }

    @Test
    fun `bundled catalog_fallback json parses cleanly with all 9 store categories`() {
        val candidates = listOf(
            File("src/main/assets/catalog_fallback.json"),
            File("app/src/main/assets/catalog_fallback.json")
        )
        val file = candidates.firstOrNull { it.exists() }
            ?: error("catalog_fallback.json not found from ${File(".").absolutePath}")

        val json = Json { ignoreUnknownKeys = true }
        val manifest = json.decodeFromString<CatalogManifest>(file.readText())
        assertEquals(18, manifest.items.size)

        val categoriesPresent = manifest.items.map { WallpaperCategory.fromSlug(it.category) }.toSet()
        val expected = WallpaperCategory.entries.filter { it != WallpaperCategory.ALL }.toSet()
        assertEquals(expected, categoriesPresent)

        manifest.items.forEach { entry ->
            assertTrue("Expected CC0 license for ${entry.id}", entry.license.contains("CC0"))
            assertTrue("Expected https downloadUrl for ${entry.id}", entry.downloadUrl.startsWith("https://"))
            assertTrue("Expected https thumbnailUrl for ${entry.id}", entry.thumbnailUrl.startsWith("https://"))
        }
    }
}
