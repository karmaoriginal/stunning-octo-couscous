package app.kromiv.core.media.enhance

import app.kromiv.core.model.EnhanceOptions
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegSession
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

class FfmpegVideoEnhancer(
    private val aiUpscaler: AiUpscaler = NoOpAiUpscaler()
) {
    suspend fun enhanceVideo(
        inputFile: File,
        outputFile: File,
        srcWidth: Int,
        srcHeight: Int,
        durationMs: Long,
        options: EnhanceOptions,
        onProgress: (Float) -> Unit
    ): Result<File> {
        if (!inputFile.exists()) {
            return Result.failure(IllegalArgumentException("El archivo de origen no existe"))
        }

        if (options.useAiUpscaler && aiUpscaler.isAvailable) {
            val aiResult = aiUpscaler.enhanceVideo(
                inputFile = inputFile,
                outputFile = outputFile,
                targetWidth = options.preset.shortSidePx,
                targetHeight = (options.preset.shortSidePx * 16) / 9,
                onProgress = onProgress
            )
            if (aiResult.isSuccess) return aiResult
        }

        // First attempt: fast hardware H.265 MediaCodec encoder
        val hwArgs = FfmpegCommandBuilder.buildEnhanceArguments(
            inputPath = inputFile.absolutePath,
            outputPath = outputFile.absolutePath,
            srcWidth = srcWidth,
            srcHeight = srcHeight,
            options = options,
            encoderMode = HevcEncoderMode.MEDIACODEC_HARDWARE
        )
        val hwResult = runFfmpegSession(hwArgs, durationMs, onProgress)
        if (hwResult.isSuccess && outputFile.exists() && outputFile.length() > 0L) {
            return Result.success(outputFile)
        }
        if (hwResult.exceptionOrNull() is EnhanceCancelledException) {
            outputFile.delete()
            return Result.failure(hwResult.exceptionOrNull()!!)
        }

        // Fallback: LGPL software H.265 encoder (libkvazaar) bundled in ffmpeg-kit-video
        outputFile.delete()
        val swArgs = FfmpegCommandBuilder.buildEnhanceArguments(
            inputPath = inputFile.absolutePath,
            outputPath = outputFile.absolutePath,
            srcWidth = srcWidth,
            srcHeight = srcHeight,
            options = options,
            encoderMode = HevcEncoderMode.KVAZAAR_SOFTWARE
        )
        val swResult = runFfmpegSession(swArgs, durationMs, onProgress)
        return if (swResult.isSuccess && outputFile.exists() && outputFile.length() > 0L) {
            Result.success(outputFile)
        } else {
            outputFile.delete()
            Result.failure(swResult.exceptionOrNull() ?: IllegalStateException("Error al transcodificar video"))
        }
    }

    private suspend fun runFfmpegSession(
        args: Array<String>,
        durationMs: Long,
        onProgress: (Float) -> Unit
    ): Result<Unit> = suspendCancellableCoroutine { cont ->
        var sessionRef: FFmpegSession? = null
        sessionRef = FFmpegKit.executeWithArgumentsAsync(
            args,
            { session ->
                if (!cont.isActive) return@executeWithArgumentsAsync
                val rc = session.returnCode
                when {
                    ReturnCode.isSuccess(rc) -> {
                        onProgress(1f)
                        cont.resume(Result.success(Unit))
                    }
                    ReturnCode.isCancel(rc) -> {
                        cont.resume(Result.failure(EnhanceCancelledException()))
                    }
                    else -> {
                        val failTrace = session.failStackTrace ?: session.output?.takeLast(300) ?: "FFmpeg code $rc"
                        cont.resume(Result.failure(IllegalStateException(failTrace)))
                    }
                }
            },
            { /* log callback unused in production to avoid memory retention */ },
            { statistics ->
                if (durationMs > 0L && statistics.time > 0.0) {
                    val fraction = (statistics.time / durationMs.toDouble()).toFloat().coerceIn(0f, 0.99f)
                    onProgress(fraction)
                }
            }
        )
        cont.invokeOnCancellation {
            sessionRef?.let { FFmpegKit.cancel(it.sessionId) }
        }
    }
}

class EnhanceCancelledException : Exception("Mejora cancelada por el usuario")
