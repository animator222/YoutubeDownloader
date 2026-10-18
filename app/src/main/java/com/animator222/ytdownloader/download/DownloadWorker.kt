package com.animator222.ytdownloader.download

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.animator222.ytdownloader.engine.Engine
import com.animator222.ytdownloader.engine.FfmpegRunner
import com.animator222.ytdownloader.engine.ProcessKiller
import com.animator222.ytdownloader.engine.YtInfo
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import java.io.File

/**
 * Downloads one video in the background as a foreground service, then publishes it to the
 * gallery. Runs yt-dlp with aria2c (16 connections) for speed and falls back to yt-dlp's own
 * chunked downloader if aria2c is rejected.
 */
class DownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private val jobId = id.toString()
    private val title = inputData.getString(KEY_TITLE) ?: "YouTube video"
    private val isLive = inputData.getBoolean(KEY_LIVE, false)
    private val isAudio = inputData.getBoolean(KEY_AUDIO, false)

    private val recentLines = ArrayDeque<String>()
    private var lastUpdate = 0L
    private var stage = 0
    private val totalStages = if (inputData.getString(KEY_FORMAT)?.contains('+') == true) 2 else 1

    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo("Starting…", -1)

    override suspend fun doWork(): Result {
        val url = inputData.getString(KEY_URL) ?: return Result.failure(errorData("Missing link"))
        val format = inputData.getString(KEY_FORMAT) ?: "bv*+ba/b"

        try {
            setForeground(foregroundInfo("Starting…", -1))
        } catch (e: Exception) {
            // Android 12+ may refuse if the app went to the background before we started;
            // the download still runs, just without the guaranteed foreground priority.
        }
        publish(-1f, "Preparing…")
        Engine.ensureReady(applicationContext)
        Engine.awaitIdle()

        val root = applicationContext.getExternalFilesDir("jobs") ?: File(applicationContext.filesDir, "jobs")
        val workDir = File(root, jobId).apply {
            deleteRecursively()
            mkdirs()
        }
        DownloadControl.register(jobId, workDir)
        try {
            var useAria2c = inputData.getBoolean(KEY_TURBO, true) && !isLive
            var error: Throwable? = null
            while (true) {
                error = try {
                    runYtDlp(url, format, workDir, useAria2c)
                    null
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    e
                }
                if (error == null || DownloadControl.isStopRequested(jobId) || !useAria2c) break
                // aria2c was refused (e.g. HTTP 403); retry with yt-dlp's own downloader.
                useAria2c = false
                stage = 0
                workDir.listFiles()?.forEach { it.deleteRecursively() }
                publish(-1f, "Retrying with standard downloader…")
            }

            val stopped = DownloadControl.isStopRequested(jobId)
            if (error != null && !stopped) {
                val reason = YtInfo.friendlyError(Exception(lastError() ?: error.message))
                Notifications.failed(applicationContext, jobId, title, reason)
                return Result.failure(errorData(reason))
            }

            var output = findOutput(workDir)
                ?: return Result.failure(errorData(if (stopped) "Nothing was recorded" else "Downloaded file not found"))
                    .also { Notifications.failed(applicationContext, jobId, title, "Downloaded file not found") }

            if (isLive) {
                publish(-1f, "Finishing recording…")
                val mp4 = File(workDir, "out/${output.nameWithoutExtension}.mp4").apply { parentFile?.mkdirs() }
                if (runInterruptible(Dispatchers.IO) { FfmpegRunner.remuxToMp4(applicationContext, output, mp4) }) {
                    output = mp4
                }
            }

            publish(100f, "Saving to gallery…")
            val saved = MediaSaver.save(applicationContext, output, isAudio)
            Notifications.done(applicationContext, jobId, title, saved.uri, saved.mime)
            return Result.success(
                workDataOf(KEY_OUT_URI to saved.uri.toString(), KEY_OUT_MIME to saved.mime, KEY_OUT_NAME to saved.name)
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val reason = e.message ?: e.javaClass.simpleName
            Notifications.failed(applicationContext, jobId, title, reason)
            return Result.failure(errorData(reason))
        } finally {
            DownloadControl.unregister(jobId)
            // Make sure no aria2c/ffmpeg child outlives the job (e.g. after a cancel).
            ProcessKiller.signalMatching(workDir.absolutePath, ProcessKiller.SIGKILL)
            YoutubeDL.destroyProcessById(jobId)
            workDir.deleteRecursively()
        }
    }

    private suspend fun runYtDlp(url: String, format: String, workDir: File, useAria2c: Boolean) {
        val request = YoutubeDLRequest(url).apply {
            addOption("-f", format)
            addOption("-o", File(workDir, "%(title).100B [%(id)s].%(ext)s").absolutePath)
            addOption("--no-playlist")
            addOption("--no-mtime")
            addOption("--newline")
            addOption("--retries", 10)
            addOption("--fragment-retries", 10)
            // Download HLS/DASH fragments in parallel.
            addOption("--concurrent-fragments", 8)
            when {
                isLive -> {
                    // MPEG-TS survives being stopped mid-stream; it's remuxed to MP4 afterwards.
                    addOption("--hls-use-mpegts")
                    addOption("--no-part")
                }
                useAria2c -> addOption("--downloader", "libaria2c.so")
                // Chunked requests avoid YouTube's per-connection throttling.
                else -> addOption("--http-chunk-size", "10M")
            }
            if (!isAudio && !isLive) {
                addOption("--merge-output-format", "mp4")
                addOption("--remux-video", "mp4")
            }
        }
        runInterruptible(Dispatchers.IO) {
            YoutubeDL.getInstance().execute(request, jobId, true) { progress, eta, line ->
                onOutput(progress, eta, line)
            }
        }
    }

    private fun onOutput(progress: Float, etaSeconds: Long, line: String) {
        synchronized(recentLines) {
            if (line.isNotBlank()) {
                recentLines.addLast(line)
                if (recentLines.size > 30) recentLines.removeFirst()
            }
        }
        when {
            line.startsWith("[download] Destination:") -> stage++
            line.startsWith("[Merger]") -> return publish(-1f, "Merging video and audio…", force = true)
            line.startsWith("[VideoRemuxer]") || line.startsWith("[FixupM") ->
                return publish(-1f, "Finalizing…", force = true)
        }

        val now = System.currentTimeMillis()
        if (now - lastUpdate < 700) return
        lastUpdate = now

        if (isLive) {
            val m = liveRegex.find(line) ?: return
            val size = m.groupValues[1].replace("KiB", " KB").replace("kB", " KB").replace("MiB", " MB")
            val time = m.groupValues[2].substringBefore('.')
            publish(-1f, "● Recording $time · $size")
            return
        }

        val speed = speedRegex.find(line)?.groupValues?.get(1)
            ?: aria2SpeedRegex.find(line)?.groupValues?.get(1)?.plus("/s")
        val parts = mutableListOf<String>()
        if (progress >= 0) parts += "${progress.toInt()}%"
        if (speed != null) parts += speed.replace("iB", "B")
        if (etaSeconds > 0) parts += "ETA ${formatEta(etaSeconds)}"
        if (totalStages > 1 && stage > 0) {
            parts += if (stage <= 1) "video" else "audio"
        }
        if (parts.isEmpty()) return
        publish(progress, parts.joinToString(" · "))
    }

    private fun publish(progress: Float, status: String, force: Boolean = false) {
        if (force) lastUpdate = System.currentTimeMillis()
        setProgressAsync(workDataOf(KEY_PROGRESS to progress, KEY_STATUS to status))
        Notifications.notify(
            applicationContext,
            Notifications.progressId(jobId),
            Notifications.progress(applicationContext, jobId, title, status, progress.toInt(), isLive)
        )
    }

    private fun foregroundInfo(text: String, percent: Int): ForegroundInfo {
        val notification = Notifications.progress(applicationContext, jobId, title, text, percent, isLive)
        val id = Notifications.progressId(jobId)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(id, notification)
        }
    }

    private fun lastError(): String? = synchronized(recentLines) {
        recentLines.lastOrNull { it.contains("ERROR") }
    }

    private fun findOutput(dir: File): File? {
        val media = dir.walkTopDown()
            .filter { it.isFile && it.extension.lowercase() in mediaExtensions }
            .toList()
        // Prefer the final file over intermediate "name.f137.mp4" streams.
        return media.filterNot { intermediateRegex.containsMatchIn(it.name) }.maxByOrNull { it.length() }
            ?: media.maxByOrNull { it.length() }
    }

    private fun errorData(message: String) = workDataOf(KEY_ERROR to message)

    private fun formatEta(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    companion object {
        const val TAG = "download"
        const val TAG_LIVE = "live"
        const val TAG_AUDIO = "audio"
        const val TAG_TITLE_PREFIX = "title:"
        const val TAG_CREATED_PREFIX = "created:"

        const val KEY_URL = "url"
        const val KEY_FORMAT = "format"
        const val KEY_TITLE = "title"
        const val KEY_LIVE = "live"
        const val KEY_AUDIO = "audio"
        const val KEY_TURBO = "turbo"

        const val KEY_PROGRESS = "progress"
        const val KEY_STATUS = "status"
        const val KEY_OUT_URI = "out_uri"
        const val KEY_OUT_MIME = "out_mime"
        const val KEY_OUT_NAME = "out_name"
        const val KEY_ERROR = "error"

        private val mediaExtensions = setOf("mp4", "mkv", "webm", "m4a", "mp3", "opus", "ogg", "aac", "ts", "mov", "3gp")
        private val intermediateRegex = Regex("""\.f[0-9][\w-]*\.\w+$""")
        private val speedRegex = Regex("""at\s+([\d.]+\s*[KMG]?i?B/s)""")
        private val aria2SpeedRegex = Regex("""DL:([\d.]+[KMG]?i?B)""")
        private val liveRegex = Regex("""size=\s*(\S+)\s+time=(\S+)""")
    }
}
