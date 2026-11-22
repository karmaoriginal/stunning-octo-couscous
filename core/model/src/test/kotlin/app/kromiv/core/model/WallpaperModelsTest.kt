package app.kromiv.core.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WallpaperModelsTest {

    @Test
    fun `format detection supports all requested image and video extensions`() {
        assertEquals(WallpaperFormat.AVIF, WallpaperFormat.fromExtensionOrMime("avif", null))
        assertEquals(WallpaperFormat.HEIC, WallpaperFormat.fromExtensionOrMime(null, "image/heic"))
        assertEquals(WallpaperFormat.BMP, WallpaperFormat.fromExtensionOrMime("bmp", null))
        assertEquals(WallpaperFormat.GIF, WallpaperFormat.fromExtensionOrMime("gif", "image/gif"))
        assertEquals(WallpaperFormat.MKV, WallpaperFormat.fromExtensionOrMime("mkv", null))
        assertEquals(WallpaperFormat.WEBM, WallpaperFormat.fromExtensionOrMime(null, "video/webm"))
        assertEquals(WallpaperFormat.MOV, WallpaperFormat.fromExtensionOrMime("mov", null))
        assertEquals(WallpaperFormat.THREE_GP, WallpaperFormat.fromExtensionOrMime("3gp", null))
    }

    @Test
    fun `enhance advisor recommends enhancement for low res or low bitrate videos only`() {
        assertTrue(
            EnhanceAdvisor.shouldRecommendEnhance(
                mediaType = WallpaperMediaType.VIDEO,
                width = 720,
                height = 1280,
                bitrateBps = 5_000_000L,
                codec = "h264"
            )
        )
        assertTrue(
            EnhanceAdvisor.shouldRecommendEnhance(
                mediaType = WallpaperMediaType.VIDEO,
                width = 1080,
                height = 1920,
                bitrateBps = 1_500_000L,
                codec = "h264"
            )
        )
        assertFalse(
            EnhanceAdvisor.shouldRecommendEnhance(
                mediaType = WallpaperMediaType.VIDEO,
                width = 1080,
                height = 2400,
                bitrateBps = 8_000_000L,
                codec = "hevc"
            )
        )
        assertFalse(
            EnhanceAdvisor.shouldRecommendEnhance(
                mediaType = WallpaperMediaType.IMAGE,
                width = 640,
                height = 480,
                bitrateBps = 0L,
                codec = null
            )
        )
    }

    @Test
    fun `compute target dimensions keeps aspect ratio and even values`() {
        val (w, h) = EnhanceAdvisor.computeTargetDimensions(541, 960, 1080)
        assertEquals(0, w % 2)
        assertEquals(0, h % 2)
        assertTrue(w >= 1080)
        assertTrue(h > w)
    }

    @Test
    fun `catalog manifest deserializes cleanly`() {
        val raw = """
            {
              "version": 1,
              "updatedAt": "2026-11-22",
              "items": [
                {
                  "id": "amoled-obsidian-01",
                  "title": "Obsidian Wave",
                  "category": "amoled",
                  "mediaType": "video",
                  "format": "mp4",
                  "thumbnailUrl": "https://example.com/t.webp",
                  "previewUrl": "https://example.com/p.mp4",
                  "downloadUrl": "https://example.com/f.mp4",
                  "width": 1080,
                  "height": 1920,
                  "durationMs": 6000,
                  "popularity": 99
                }
              ]
            }
        """.trimIndent()
        val manifest = Json { ignoreUnknownKeys = true }.decodeFromString<CatalogManifest>(raw)
        assertEquals(1, manifest.items.size)
        val item = manifest.items.first().toWallpaperItem(isFavorite = true)
        assertEquals(WallpaperCategory.AMOLED, item.category)
        assertEquals(WallpaperMediaType.VIDEO, item.mediaType)
        assertTrue(item.isFavorite)
    }
}
