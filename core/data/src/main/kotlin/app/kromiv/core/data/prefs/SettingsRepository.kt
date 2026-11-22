package app.kromiv.core.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.kromiv.core.model.AppSettings
import app.kromiv.core.model.ThemeMode
import app.kromiv.core.model.WallpaperMediaType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.kromivDataStore: DataStore<Preferences> by preferencesDataStore(name = "kromiv_settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val AMOLED_DARK = booleanPreferencesKey("amoled_dark")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val LIVE_FPS_LIMIT = intPreferencesKey("live_fps_limit")
        val MUTE_LIVE_BY_DEFAULT = booleanPreferencesKey("mute_live_by_default")
        val PAUSE_ON_BATTERY_SAVER = booleanPreferencesKey("pause_on_battery_saver")
        val WIFI_ONLY_DOWNLOADS = booleanPreferencesKey("wifi_only_downloads")
        val MAX_CACHE_MB = intPreferencesKey("max_cache_mb")
        val AI_UPSCALER_ENABLED = booleanPreferencesKey("ai_upscaler_enabled")
        val ACTIVE_LIVE_URI = stringPreferencesKey("active_live_uri")
        val ACTIVE_LIVE_MEDIA_TYPE = stringPreferencesKey("active_live_media_type")
        val ACTIVE_LIVE_MUTED = booleanPreferencesKey("active_live_muted")
        val ACTIVE_LIVE_FPS = intPreferencesKey("active_live_fps")
        val CATALOG_URL_OVERRIDE = stringPreferencesKey("catalog_url_override")
    }

    val settings: Flow<AppSettings> = context.kromivDataStore.data.map { prefs ->
        AppSettings(
            themeMode = prefs[Keys.THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.DARK,
            amoledDark = prefs[Keys.AMOLED_DARK] ?: true,
            dynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: true,
            liveFpsLimit = (prefs[Keys.LIVE_FPS_LIMIT] ?: 60).coerceIn(30, 60),
            muteLiveByDefault = prefs[Keys.MUTE_LIVE_BY_DEFAULT] ?: true,
            pauseLiveOnBatterySaver = prefs[Keys.PAUSE_ON_BATTERY_SAVER] ?: true,
            wifiOnlyDownloads = prefs[Keys.WIFI_ONLY_DOWNLOADS] ?: false,
            maxCacheMb = prefs[Keys.MAX_CACHE_MB] ?: 256,
            aiUpscalerEnabled = prefs[Keys.AI_UPSCALER_ENABLED] ?: false,
            activeLiveWallpaperUri = prefs[Keys.ACTIVE_LIVE_URI],
            activeLiveMediaType = prefs[Keys.ACTIVE_LIVE_MEDIA_TYPE]
                ?.let { runCatching { WallpaperMediaType.valueOf(it) }.getOrNull() }
                ?: WallpaperMediaType.VIDEO,
            activeLiveMuted = prefs[Keys.ACTIVE_LIVE_MUTED] ?: true,
            activeLiveFps = prefs[Keys.ACTIVE_LIVE_FPS] ?: 60,
            catalogUrlOverride = prefs[Keys.CATALOG_URL_OVERRIDE]?.takeIf { it.isNotBlank() }
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.kromivDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setAmoledDark(enabled: Boolean) {
        context.kromivDataStore.edit { it[Keys.AMOLED_DARK] = enabled }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.kromivDataStore.edit { it[Keys.DYNAMIC_COLOR] = enabled }
    }

    suspend fun setLiveFpsLimit(fps: Int) {
        context.kromivDataStore.edit {
            val normalized = if (fps <= 30) 30 else 60
            it[Keys.LIVE_FPS_LIMIT] = normalized
            it[Keys.ACTIVE_LIVE_FPS] = normalized
        }
    }

    suspend fun setMuteLiveByDefault(muted: Boolean) {
        context.kromivDataStore.edit {
            it[Keys.MUTE_LIVE_BY_DEFAULT] = muted
            it[Keys.ACTIVE_LIVE_MUTED] = muted
        }
    }

    suspend fun setPauseOnBatterySaver(enabled: Boolean) {
        context.kromivDataStore.edit { it[Keys.PAUSE_ON_BATTERY_SAVER] = enabled }
    }

    suspend fun setWifiOnlyDownloads(enabled: Boolean) {
        context.kromivDataStore.edit { it[Keys.WIFI_ONLY_DOWNLOADS] = enabled }
    }

    suspend fun setMaxCacheMb(mb: Int) {
        context.kromivDataStore.edit { it[Keys.MAX_CACHE_MB] = mb.coerceIn(64, 2048) }
    }

    suspend fun setAiUpscalerEnabled(enabled: Boolean) {
        context.kromivDataStore.edit { it[Keys.AI_UPSCALER_ENABLED] = enabled }
    }

    suspend fun setActiveLiveWallpaper(
        uri: String,
        mediaType: WallpaperMediaType,
        muted: Boolean,
        fps: Int
    ) {
        context.kromivDataStore.edit {
            it[Keys.ACTIVE_LIVE_URI] = uri
            it[Keys.ACTIVE_LIVE_MEDIA_TYPE] = mediaType.name
            it[Keys.ACTIVE_LIVE_MUTED] = muted
            it[Keys.ACTIVE_LIVE_FPS] = if (fps <= 30) 30 else 60
        }
    }
}
