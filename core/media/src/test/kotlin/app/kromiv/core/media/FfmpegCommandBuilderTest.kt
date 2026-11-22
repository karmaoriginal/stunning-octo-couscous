package app.kromiv.core.media

import app.kromiv.core.media.enhance.FfmpegCommandBuilder
import app.kromiv.core.media.enhance.HevcEncoderMode
import app.kromiv.core.model.EnhanceOptions
import app.kromiv.core.model.EnhancePreset
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FfmpegCommandBuilderTest {

    @Test
    fun `hardware hevc command includes lanczos, hqdn3d, unsharp and faststart`() {
        val args = FfmpegCommandBuilder.buildEnhanceArguments(
            inputPath = "/data/in.mp4",
            outputPath = "/data/out.mp4",
            srcWidth = 720,
            srcHeight = 1280,
            options = EnhanceOptions(
                preset = EnhancePreset.FHD_1080P,
                targetFps = 60,
                denoiseEnabled = true,
                sharpenEnabled = true,
                stripAudio = true
            ),
            encoderMode = HevcEncoderMode.MEDIACODEC_HARDWARE
        ).toList()

        val vf = args[args.indexOf("-vf") + 1]
        assertTrue(vf.contains("scale=w=1080:h=1920:flags=lanczos"))
        assertTrue(vf.contains("hqdn3d="))
        assertTrue(vf.contains("unsharp="))
        assertTrue(vf.contains("fps=60"))
        assertTrue(args.contains("hevc_mediacodec"))
        assertTrue(args.contains("-an"))
        assertTrue(args.contains("+faststart"))
    }

    @Test
    fun `software fallback uses libkvazaar and preserves audio when stripAudio is false`() {
        val args = FfmpegCommandBuilder.buildEnhanceArguments(
            inputPath = "/data/in.mkv",
            outputPath = "/data/out.mp4",
            srcWidth = 720,
            srcHeight = 1280,
            options = EnhanceOptions(
                preset = EnhancePreset.QHD_1440P,
                targetFps = 30,
                denoiseEnabled = false,
                sharpenEnabled = false,
                stripAudio = false
            ),
            encoderMode = HevcEncoderMode.KVAZAAR_SOFTWARE
        ).toList()

        val vf = args[args.indexOf("-vf") + 1]
        assertTrue(vf.contains("scale=w=1440:h=2560:flags=lanczos"))
        assertFalse(vf.contains("hqdn3d="))
        assertFalse(vf.contains("unsharp="))
        assertTrue(args.contains("libkvazaar"))
        assertTrue(args.contains("aac"))
        assertFalse(args.contains("-an"))
    }
}
