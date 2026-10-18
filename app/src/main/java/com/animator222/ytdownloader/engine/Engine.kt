package com.animator222.ytdownloader.engine

import android.content.Context
import com.yausername.aria2c.Aria2c
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger

/** Owns the bundled yt-dlp / python / ffmpeg / aria2c runtime. */
object Engine {
    enum class UpdateResult { UPDATED, UP_TO_DATE, SKIPPED }

    private const val PREFS = "engine"
    private const val KEY_LAST_UPDATE = "last_update"
    private const val KEY_VERSION = "ytdlp_version"

    // YouTube breaks old yt-dlp versions quickly (HTTP 403, missing formats), so stay current.
    private const val UPDATE_INTERVAL_MS = 12L * 60 * 60 * 1000
    private const val UPDATE_TIMEOUT_MS = 60_000L
    private const val LATEST_URL = "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp"

    // Where youtubedl-android runs yt-dlp from.
    private const val YTDLP_PATH = "youtubedl-android/yt-dlp/yt-dlp"

    private val initMutex = Mutex()
    private val updateMutex = Mutex()

    @Volatile
    private var ready = false

    /** yt-dlp processes currently running; the binary is never replaced underneath them. */
    private val activeRuns = AtomicInteger(0)

    suspend fun ensureReady(context: Context) {
        if (ready) return
        initMutex.withLock {
            if (ready) return
            withContext(Dispatchers.IO) {
                val app = context.applicationContext
                YoutubeDL.init(app)
                FFmpeg.init(app)
                Aria2c.init(app)
            }
            ready = true
        }
    }

    /**
     * Call before using yt-dlp: starts the runtime and, when the last check is older than
     * [UPDATE_INTERVAL_MS], updates yt-dlp first. Update failures (e.g. offline) are ignored so
     * the installed version is still used.
     */
    suspend fun prepare(context: Context) {
        ensureReady(context)
        try {
            withTimeoutOrNull(UPDATE_TIMEOUT_MS) { update(context, force = false) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Keep using the current yt-dlp.
        }
    }

    /** Runs [block] (which uses yt-dlp) while preventing a concurrent update of the binary. */
    suspend fun <T> use(block: suspend () -> T): T {
        updateMutex.withLock { activeRuns.incrementAndGet() }
        try {
            return block()
        } finally {
            activeRuns.decrementAndGet()
        }
    }

    /** Downloads the latest yt-dlp release when it differs from the installed one. */
    suspend fun update(context: Context, force: Boolean): UpdateResult {
        ensureReady(context)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return updateMutex.withLock {
            val now = System.currentTimeMillis()
            if (!force && now - prefs.getLong(KEY_LAST_UPDATE, 0) < UPDATE_INTERVAL_MS) {
                return@withLock UpdateResult.SKIPPED
            }
            if (activeRuns.get() > 0) {
                if (force) throw IllegalStateException("Wait for downloads to finish before updating.")
                return@withLock UpdateResult.SKIPPED
            }
            withContext(Dispatchers.IO) {
                val target = File(context.noBackupFilesDir, YTDLP_PATH)
                val download = File(target.parentFile, "yt-dlp.download")
                val backup = File(target.parentFile, "yt-dlp.previous")
                val before = installedVersion(context)

                fetch(LATEST_URL, download)
                target.parentFile?.mkdirs()
                backup.delete()
                target.renameTo(backup)
                if (!download.renameTo(target)) {
                    backup.renameTo(target)
                    throw IOException("Couldn't install the yt-dlp update")
                }
                // Make sure the new build actually runs before keeping it.
                val after = runCatching { readVersion() }.getOrNull()
                if (after == null) {
                    target.delete()
                    backup.renameTo(target)
                    throw IOException("The downloaded yt-dlp doesn't run")
                }
                backup.delete()
                prefs.edit()
                    .putLong(KEY_LAST_UPDATE, now)
                    .putString(KEY_VERSION, after)
                    .apply()
                if (after == before) UpdateResult.UP_TO_DATE else UpdateResult.UPDATED
            }
        }
    }

    /** Version of the yt-dlp that will actually run. */
    suspend fun version(context: Context): String? {
        ensureReady(context)
        return withContext(Dispatchers.IO) { installedVersion(context) }
    }

    private fun installedVersion(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(KEY_VERSION, null)?.let { return it }
        return runCatching { readVersion() }.getOrNull()?.also {
            prefs.edit().putString(KEY_VERSION, it).apply()
        }
    }

    private fun readVersion(): String? {
        val response = YoutubeDL.getInstance()
            .execute(YoutubeDLRequest(emptyList<String>()).addOption("--version"), null, false, null)
        return response.out.trim().lines().lastOrNull()?.takeIf { it.isNotBlank() }
    }

    private fun fetch(url: String, file: File) {
        file.parentFile?.mkdirs()
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "YTDownloader-Android")
        }
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("yt-dlp download failed: HTTP ${connection.responseCode}")
            }
            connection.inputStream.use { input -> file.outputStream().use { input.copyTo(it) } }
        } finally {
            connection.disconnect()
        }
        // The release asset is a python zipapp starting with a "#!" line and a few MB long.
        val header = ByteArray(2)
        val read = file.inputStream().use { it.read(header) }
        if (file.length() < 1_000_000 || read != 2 || header[0] != '#'.code.toByte() || header[1] != '!'.code.toByte()) {
            file.delete()
            throw IOException("Downloaded yt-dlp looks invalid")
        }
    }
}
