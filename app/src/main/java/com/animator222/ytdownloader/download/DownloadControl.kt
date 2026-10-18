package com.animator222.ytdownloader.download

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.work.WorkManager
import com.animator222.ytdownloader.engine.ProcessKiller
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Lets the UI and notification buttons reach running download jobs. */
object DownloadControl {
    private const val STOP_GRACE_MS = 20_000L

    private val workDirs = ConcurrentHashMap<String, File>()
    private val stopRequested = ConcurrentHashMap.newKeySet<String>()

    fun register(jobId: String, workDir: File) {
        workDirs[jobId] = workDir
    }

    fun unregister(jobId: String) {
        workDirs.remove(jobId)
        stopRequested.remove(jobId)
    }

    fun isStopRequested(jobId: String) = jobId in stopRequested

    /** Abort and throw away whatever was downloaded. */
    fun cancel(context: Context, jobId: String) {
        WorkManager.getInstance(context).cancelWorkById(UUID.fromString(jobId))
        workDirs[jobId]?.let { ProcessKiller.signalMatching(it.absolutePath, ProcessKiller.SIGKILL) }
    }

    /**
     * Stop a live recording but keep what has been captured. SIGINT makes yt-dlp and ffmpeg
     * finish the file cleanly; anything still alive after a grace period is killed.
     */
    fun stopAndSave(context: Context, jobId: String) {
        val dir = workDirs[jobId] ?: return cancel(context, jobId)
        stopRequested += jobId
        ProcessKiller.signalMatching(dir.absolutePath, ProcessKiller.SIGINT)
        Handler(Looper.getMainLooper()).postDelayed({
            if (workDirs[jobId] == dir) {
                Thread { ProcessKiller.signalMatching(dir.absolutePath, ProcessKiller.SIGKILL) }.start()
            }
        }, STOP_GRACE_MS)
    }
}
