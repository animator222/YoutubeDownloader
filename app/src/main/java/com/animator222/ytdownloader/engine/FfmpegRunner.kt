package com.animator222.ytdownloader.engine

import android.content.Context
import java.io.File

/** Runs the ffmpeg binary bundled by youtubedl-android for small jobs such as remuxing. */
object FfmpegRunner {
    private const val PACKAGES = "youtubedl-android/packages"

    /** Lossless container change (e.g. a recorded live MPEG-TS stream to a gallery friendly MP4). */
    fun remuxToMp4(context: Context, input: File, output: File): Boolean {
        val ffmpeg = File(context.applicationInfo.nativeLibraryDir, "libffmpeg.so")
        val packages = File(context.noBackupFilesDir, PACKAGES)
        val process = ProcessBuilder(
            ffmpeg.absolutePath, "-y", "-hide_banner", "-loglevel", "error",
            "-i", input.absolutePath,
            "-map", "0:v?", "-map", "0:a?",
            "-c", "copy", "-bsf:a", "aac_adtstoasc",
            "-movflags", "+faststart",
            output.absolutePath
        ).redirectErrorStream(true).apply {
            environment()["LD_LIBRARY_PATH"] = listOf("python", "ffmpeg", "aria2c")
                .joinToString(":") { File(packages, "$it/usr/lib").absolutePath }
        }.start()
        process.inputStream.use { it.readBytes() }
        return process.waitFor() == 0 && output.length() > 0
    }
}
