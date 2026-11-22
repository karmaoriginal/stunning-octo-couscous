package app.kromiv.core.media.enhance

import app.kromiv.core.model.EnhanceAdvisor
import app.kromiv.core.model.EnhanceOptions

enum class HevcEncoderMode {
    MEDIACODEC_HARDWARE,
    KVAZAAR_SOFTWARE
}

object FfmpegCommandBuilder {

    fun buildEnhanceArguments(
        inputPath: String,
        outputPath: String,
        srcWidth: Int,
        srcHeight: Int,
        options: EnhanceOptions,
        encoderMode: HevcEncoderMode
    ): Array<String> {
        val (targetW, targetH) = EnhanceAdvisor.computeTargetDimensions(
            srcWidth = srcWidth,
            srcHeight = srcHeight,
            targetShortEdge = options.preset.shortSidePx
        )

        val filters = buildList {
            add("scale=w=$targetW:h=$targetH:flags=lanczos")
            if (options.denoiseEnabled) {
                add("hqdn3d=1.5:1.5:6:6")
            }
            if (options.sharpenEnabled) {
                add("unsharp=5:5:0.65:5:5:0.0")
            }
            val fps = options.targetFps.coerceIn(24, 60)
            add("fps=$fps")
            add("format=yuv420p")
        }.joinToString(",")

        val args = mutableListOf(
            "-y",
            "-i", inputPath,
            "-vf", filters
        )

        val bitrateStr = "${options.preset.targetBitrateKbps}k"
        when (encoderMode) {
            HevcEncoderMode.MEDIACODEC_HARDWARE -> {
                args.addAll(
                    listOf(
                        "-c:v", "hevc_mediacodec",
                        "-b:v", bitrateStr
                    )
                )
            }
            HevcEncoderMode.KVAZAAR_SOFTWARE -> {
                args.addAll(
                    listOf(
                        "-c:v", "libkvazaar",
                        "-b:v", bitrateStr
                    )
                )
            }
        }

        if (options.stripAudio) {
            args.add("-an")
        } else {
            args.addAll(listOf("-c:a", "aac", "-b:a", "96k"))
        }

        args.addAll(
            listOf(
                "-tag:v", "hvc1",
                "-movflags", "+faststart",
                outputPath
            )
        )

        return args.toTypedArray()
    }
}
