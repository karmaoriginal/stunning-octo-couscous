package app.kromiv.di

import android.content.Context
import app.kromiv.core.data.db.KromivDatabase
import app.kromiv.core.data.library.LibraryRepository
import app.kromiv.core.data.media.AndroidMediaInspector
import app.kromiv.core.data.network.CatalogRepository
import app.kromiv.core.data.network.ResumableDownloadManager
import app.kromiv.core.data.prefs.SettingsRepository
import app.kromiv.core.media.enhance.EnhanceScheduler
import app.kromiv.core.media.image.KromivImageLoaderFactory
import app.kromiv.core.media.wallpaper.StaticWallpaperApplier
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.util.concurrent.TimeUnit

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    private val pagesFallbackInterceptor = Interceptor { chain ->
        val request = chain.request()
        val response = chain.proceed(request)
        val url = request.url
        if (response.code == 404 && url.host == "karmaoriginal.github.io" &&
            url.encodedPath.startsWith("/stunning-octo-couscous/")
        ) {
            val relPath = url.encodedPath.removePrefix("/stunning-octo-couscous/")
            val branches = listOf("main", "arena/618d1c14-stunning-octo-couscous")
            var currentResp: Response = response
            for (branch in branches) {
                currentResp.close()
                val rawUrl = "https://raw.githubusercontent.com/karmaoriginal/stunning-octo-couscous/$branch/docs/$relPath"
                val fallbackReq = request.newBuilder().url(rawUrl).build()
                currentResp = chain.proceed(fallbackReq)
                if (currentResp.isSuccessful) break
            }
            currentResp
        } else {
            response
        }
    }

    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(pagesFallbackInterceptor)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    val database: KromivDatabase = KromivDatabase.getInstance(appContext)
    val settingsRepository = SettingsRepository(appContext)
    val mediaInspector = AndroidMediaInspector(appContext)

    val catalogRepository = CatalogRepository(
        context = appContext,
        okHttpClient = okHttpClient,
        wallpaperDao = database.wallpaperDao(),
        favoriteDao = database.favoriteDao()
    )

    val downloadManager = ResumableDownloadManager(
        context = appContext,
        okHttpClient = okHttpClient,
        wallpaperDao = database.wallpaperDao(),
        mediaInspector = mediaInspector
    )

    val libraryRepository = LibraryRepository(
        context = appContext,
        wallpaperDao = database.wallpaperDao(),
        favoriteDao = database.favoriteDao(),
        mediaInspector = mediaInspector
    )

    val imageLoaderFactory = KromivImageLoaderFactory(appContext, okHttpClient)

    @Volatile
    private var cachedImageLoader: ImageLoader? = null

    fun getImageLoader(maxCacheMb: Int = 256): ImageLoader {
        return cachedImageLoader ?: synchronized(this) {
            cachedImageLoader ?: imageLoaderFactory.build(maxCacheMb).also { cachedImageLoader = it }
        }
    }

    @OptIn(DelicateCoilApi::class)
    fun rebuildImageLoader(maxCacheMb: Int) {
        synchronized(this) {
            cachedImageLoader?.let { imageLoaderFactory.clearDiskCache(it) }
            val fresh = imageLoaderFactory.build(maxCacheMb)
            cachedImageLoader = fresh
            SingletonImageLoader.setUnsafe(fresh)
        }
    }

    val staticWallpaperApplier: StaticWallpaperApplier
        get() = StaticWallpaperApplier(appContext, getImageLoader())

    val enhanceScheduler = EnhanceScheduler(appContext, database.wallpaperDao())
}
