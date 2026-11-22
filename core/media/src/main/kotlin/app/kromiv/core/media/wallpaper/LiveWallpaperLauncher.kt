package app.kromiv.core.media.wallpaper

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent

object LiveWallpaperLauncher {
    fun launchLiveWallpaperPicker(context: Context): Boolean {
        val component = ComponentName(context, KromivLiveWallpaperService::class.java)
        val directIntent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
            putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(directIntent)
            true
        }.getOrElse {
            val fallbackIntent = Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching {
                context.startActivity(fallbackIntent)
                true
            }.getOrDefault(false)
        }
    }
}
