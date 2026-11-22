package app.kromiv.core.media.image

import android.content.Context
import android.os.Build
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.github.awxkee.avifcoil.decoder.HeifDecoder
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath
import java.io.File

class KromivImageLoaderFactory(
    private val context: Context,
    private val okHttpClient: OkHttpClient
) {
    private val diskCacheDir = File(context.cacheDir, "image_cache")

    fun build(maxDiskCacheMb: Int = 256): ImageLoader {
        val maxBytes = maxDiskCacheMb.coerceIn(64, 2048).toLong() * 1024L * 1024L
        return ImageLoader.Builder(context)
            .components {
                add(HeifDecoder.Factory())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    add(AnimatedImageDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
                add(OkHttpNetworkFetcherFactory(callFactory = { okHttpClient }))
            }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.15)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(diskCacheDir.toOkioPath())
                    .maxSizeBytes(maxBytes)
                    .build()
            }
            .crossfade(true)
            .build()
    }

    fun getDiskCacheSizeBytes(): Long {
        if (!diskCacheDir.exists()) return 0L
        return diskCacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    fun clearDiskCache(imageLoader: ImageLoader) {
        imageLoader.memoryCache?.clear()
        imageLoader.diskCache?.clear()
        runCatching { diskCacheDir.deleteRecursively() }
    }
}
