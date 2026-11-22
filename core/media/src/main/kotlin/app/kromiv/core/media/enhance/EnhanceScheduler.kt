package app.kromiv.core.media.enhance

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import app.kromiv.core.data.db.WallpaperDao
import app.kromiv.core.model.EnhanceOptions
import app.kromiv.core.model.EnhanceStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class EnhanceScheduler(
    private val context: Context,
    private val wallpaperDao: WallpaperDao
) {
    private val workManager = WorkManager.getInstance(context)

    suspend fun enqueueEnhance(wallpaperId: String, options: EnhanceOptions): Unit = withContext(Dispatchers.IO) {
        wallpaperDao.updateEnhanceProgress(wallpaperId, EnhanceStatus.QUEUED.name, 0f)

        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .setRequiresStorageNotLow(true)
            .build()

        val request = OneTimeWorkRequestBuilder<VideoEnhanceWorker>()
            .setConstraints(constraints)
            .setInputData(
                workDataOf(
                    VideoEnhanceWorker.KEY_WALLPAPER_ID to wallpaperId,
                    VideoEnhanceWorker.KEY_PRESET to options.preset.name,
                    VideoEnhanceWorker.KEY_TARGET_FPS to options.targetFps,
                    VideoEnhanceWorker.KEY_DENOISE to options.denoiseEnabled,
                    VideoEnhanceWorker.KEY_SHARPEN to options.sharpenEnabled,
                    VideoEnhanceWorker.KEY_STRIP_AUDIO to options.stripAudio,
                    VideoEnhanceWorker.KEY_USE_AI to options.useAiUpscaler
                )
            )
            .addTag("kromiv_enhance")
            .build()

        workManager.enqueueUniqueWork(
            VideoEnhanceWorker.uniqueWorkName(wallpaperId),
            ExistingWorkPolicy.KEEP,
            request
        )
        Unit
    }

    suspend fun cancelEnhance(wallpaperId: String): Unit = withContext(Dispatchers.IO) {
        workManager.cancelUniqueWork(VideoEnhanceWorker.uniqueWorkName(wallpaperId))
        wallpaperDao.updateEnhanceProgress(wallpaperId, EnhanceStatus.CANCELLED.name, 0f)
        Unit
    }
}
