package com.animator222.ytdownloader.engine

import android.os.Process
import java.io.File

/**
 * yt-dlp spawns aria2c / ffmpeg as child processes. Killing only the python process leaves
 * them running, so we find every process of ours whose command line mentions [marker]
 * (the job's private download folder) and signal it directly.
 */
object ProcessKiller {
    const val SIGINT = 2
    const val SIGKILL = 9

    fun signalMatching(marker: String, signal: Int): Int {
        val self = Process.myPid()
        var count = 0
        File("/proc").listFiles()?.forEach { dir ->
            val pid = dir.name.toIntOrNull() ?: return@forEach
            if (pid == self) return@forEach
            val cmdline = try {
                File(dir, "cmdline").readBytes().toString(Charsets.UTF_8)
            } catch (e: Exception) {
                return@forEach
            }
            if (cmdline.contains(marker)) {
                Process.sendSignal(pid, signal)
                count++
            }
        }
        return count
    }

    fun anyMatching(marker: String): Boolean {
        val self = Process.myPid()
        return File("/proc").listFiles()?.any { dir ->
            val pid = dir.name.toIntOrNull() ?: return@any false
            pid != self && runCatching {
                File(dir, "cmdline").readBytes().toString(Charsets.UTF_8).contains(marker)
            }.getOrDefault(false)
        } ?: false
    }
}
