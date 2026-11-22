package app.kromiv.core.media.enhance

import java.io.File

/**
 * Clean extension point for an optional lightweight on-device AI video super-resolution model
 * (e.g., TFLite / NCNN / ONNX Runtime FSRCNN or RealESRGAN-x2-lite).
 *
 * Disabled by default to preserve minimal APK size and zero background battery drain.
 */
interface AiUpscaler {
    val id: String
    val displayName: String
    val isAvailable: Boolean

    suspend fun enhanceVideo(
        inputFile: File,
        outputFile: File,
        targetWidth: Int,
        targetHeight: Int,
        onProgress: (Float) -> Unit
    ): Result<File>
}

class NoOpAiUpscaler : AiUpscaler {
    override val id: String = "noop"
    override val displayName: String = "Escalador IA (Modelo ligero no incluido)"
    override val isAvailable: Boolean = false

    override suspend fun enhanceVideo(
        inputFile: File,
        outputFile: File,
        targetWidth: Int,
        targetHeight: Int,
        onProgress: (Float) -> Unit
    ): Result<File> = Result.failure(
        IllegalStateException("El modelo ligero de IA opcional no está instalado; usando filtro Lanczos + hqdn3d + unsharp.")
    )
}
