package com.animator222.ytdownloader.engine

import android.content.Context
import com.yausername.aria2c.Aria2c
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Owns the bundled yt-dlp / python / ffmpeg / aria2c runtime. */
object Engine {
    private const val PREFS = "engine"
    private const val KEY_LAST_UPDATE = "last_update"
    private const val UPDATE_INTERVAL_MS = 24L * 60 * 60 * 1000

    private val initMutex = Mutex()
    private val updateMutex = Mutex()

    @Volatile
    private var ready = false

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

    /** Suspends while yt-dlp is replacing itself so nothing runs a half-written binary. */
    suspend fun awaitIdle() {
        updateMutex.withLock { }
    }

    /**
     * YouTube changes often, so keep yt-dlp current. Checks at most once a day unless [force].
     * Returns the update status, or null when the check was skipped.
     */
    suspend fun update(context: Context, force: Boolean): YoutubeDL.UpdateStatus? {
        ensureReady(context)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (!force && now - prefs.getLong(KEY_LAST_UPDATE, 0) < UPDATE_INTERVAL_MS) return null
        return updateMutex.withLock {
            withContext(Dispatchers.IO) {
                val status = YoutubeDL.updateYoutubeDL(context.applicationContext)
                prefs.edit().putLong(KEY_LAST_UPDATE, now).apply()
                status
            }
        }
    }

    fun version(context: Context): String? =
        YoutubeDL.versionName(context) ?: YoutubeDL.version(context)
}
