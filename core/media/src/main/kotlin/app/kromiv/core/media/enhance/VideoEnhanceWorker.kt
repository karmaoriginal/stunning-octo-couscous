package app.kromiv.core.media.enhance

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.kromiv.core.data.db.KromivDatabase
import app.kromiv.core.data.media.AndroidMediaInspector
import app.kromiv.core.media.R
import app.kromiv.core.model.EnhanceOptions
import app.kromiv.core.model.EnhancePreset
import app.kromiv.core.model.EnhanceStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

class VideoEnhanceWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    private val notificationManager =
        appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override suspend fun doWork(): Result {
        val wallpaperId = inputData.getString(KEY_WALLPAPER_ID) ?: return Result.failure()
        val presetName = inputData.getString(KEY_PRESET) ?: EnhancePreset.FHD_1080P.name
        val targetFps = inputData.getInt(KEY_TARGET_FPS, 60)
        val denoise = inputData.getBoolean(KEY_DENOISE, true)
        val sharpen = inputData.getBoolean(KEY_SHARPEN, true)
        val stripAudio = inputData.getBoolean(KEY_STRIP_AUDIO, true)
        val useAi = inputData.getBoolean(KEY_USE_AI, false)

        val preset = runCatching { EnhancePreset.valueOf(presetName) }.getOrDefault(EnhancePreset.FHD_1080P)
        val options = EnhanceOptions(
            preset = preset,
            targetFps = targetFps,
            denoiseEnabled = denoise,
            sharpenEnabled = sharpen,
            stripAudio = stripAudio,
            useAiUpscaler = useAi
        )

        val db = KromivDatabase.getInstance(applicationContext)
        val dao = db.wallpaperDao()
        val inspector = AndroidMediaInspector(applicationContext)

        try {
            val entity = dao.getById(wallpaperId) ?: return Result.failure()
            val inputFile = File(entity.localPath)
            if (!inputFile.exists()) {
                dao.updateEnhanceProgress(wallpaperId, EnhanceStatus.FAILED.name, 0f)
                return Result.failure()
            }

            ensureChannel()
            setForeground(createForegroundInfo(wallpaperId, entity.title, 0))
            dao.updateEnhanceProgress(wallpaperId, EnhanceStatus.RUNNING.name, 0.01f)

            val enhancedDir = File(applicationContext.filesDir, "wallpapers/enhanced").apply { mkdirs() }
            val tempOut = File(applicationContext.cacheDir, "enhance_tmp_${wallpaperId}.mp4")
            val finalOut = File(enhancedDir, "${wallpaperId}_hevc.mp4")

            val enhancer = FfmpegVideoEnhancer()
            var lastReportedPct = -1

            val result = enhancer.enhanceVideo(
                inputFile = inputFile,
                outputFile = tempOut,
                srcWidth = entity.width,
                srcHeight = entity.height,
                durationMs = entity.durationMs,
                options = options
            ) { fraction ->
                val pct = (fraction * 100f).roundToInt().coerceIn(0, 99)
                if (pct != lastReportedPct) {
                    lastReportedPct = pct
                    setProgressAsync(workDataOf(KEY_PROGRESS to fraction))
                    CoroutineScope(Dispatchers.IO).launch {
                        dao.updateEnhanceProgress(wallpaperId, EnhanceStatus.RUNNING.name, fraction)
                        notificationManager.notify(
                            notificationIdFor(wallpaperId),
                            buildNotification(entity.title, pct)
                        )
                    }
                }
            }

            return if (result.isSuccess) {
                if (finalOut.exists()) finalOut.delete()
                if (!tempOut.renameTo(finalOut)) {
                    tempOut.copyTo(finalOut, overwrite = true)
                    tempOut.delete()
                }
                val probed = inspector.probeLocalFile(finalOut, entity.title)
                dao.markEnhanced(
                    id = wallpaperId,
                    enhancedPath = finalOut.absolutePath,
                    enhancedSizeBytes = finalOut.length(),
                    newWidth = if (probed.width > 0) probed.width else entity.width,
                    newHeight = if (probed.height > 0) probed.height else entity.height,
                    newCodec = probed.codec ?: "hevc",
                    status = EnhanceStatus.COMPLETED.name
                )
                Result.success(workDataOf(KEY_OUTPUT_PATH to finalOut.absolutePath))
            } else {
                tempOut.delete()
                val err = result.exceptionOrNull()
                val status = if (err is EnhanceCancelledException || isStopped) {
                    EnhanceStatus.CANCELLED
                } else {
                    EnhanceStatus.FAILED
                }
                dao.updateEnhanceProgress(wallpaperId, status.name, 0f)
                Result.failure()
            }
        } catch (_: CancellationException) {
            dao.updateEnhanceProgress(wallpaperId, EnhanceStatus.CANCELLED.name, 0f)
            return Result.failure()
        } catch (_: Throwable) {
            dao.updateEnhanceProgress(wallpaperId, EnhanceStatus.FAILED.name, 0f)
            return Result.failure()
        }
    }

    private fun ensureChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            applicationContext.getString(R.string.enhance_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = applicationContext.getString(R.string.enhance_channel_desc)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun buildNotification(title: String, percent: Int) =
        NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_kromiv_notif)
            .setContentTitle("Mejorando \"$title\"")
            .setContentText(if (percent > 0) "H.265 Lanczos · $percent%" else "Preparando transcodificación…")
            .setProgress(100, percent, percent == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Cancelar",
                WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)
            )
            .build()

    private fun createForegroundInfo(wallpaperId: String, title: String, percent: Int): ForegroundInfo {
        val notification = buildNotification(title, percent)
        val notifId = notificationIdFor(wallpaperId)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(notifId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notifId, notification)
        }
    }

    private fun notificationIdFor(id: String): Int = 4000 + (id.hashCode() and 0x0FFF)

    companion object {
        const val CHANNEL_ID = "kromiv_enhance_channel"
        const val KEY_WALLPAPER_ID = "wallpaper_id"
        const val KEY_PRESET = "preset"
        const val KEY_TARGET_FPS = "target_fps"
        const val KEY_DENOISE = "denoise"
        const val KEY_SHARPEN = "sharpen"
        const val KEY_STRIP_AUDIO = "strip_audio"
        const val KEY_USE_AI = "use_ai"
        const val KEY_PROGRESS = "progress"
        const val KEY_OUTPUT_PATH = "output_path"

        fun uniqueWorkName(wallpaperId: String): String = "kromiv_enhance_$wallpaperId"
    }
}
