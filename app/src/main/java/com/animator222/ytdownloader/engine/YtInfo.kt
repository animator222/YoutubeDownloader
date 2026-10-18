package com.animator222.ytdownloader.engine

import android.content.Context
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import org.json.JSONObject
import kotlin.math.roundToInt

object YtInfo {
    private val urlRegex = Regex("""https?://\S+""")

    /** Pulls the first link out of shared text such as "Check this out https://youtu.be/xyz". */
    fun extractUrl(text: String): String? {
        val trimmed = text.trim()
        urlRegex.find(trimmed)?.let { return it.value.trimEnd('.', ',', ')', '"', '\'') }
        // Allow bare "youtu.be/..." or "www.youtube.com/..." links too.
        return if (trimmed.contains("youtu") && !trimmed.contains(' ')) "https://$trimmed" else null
    }

    suspend fun fetch(context: Context, url: String): VideoDetails {
        Engine.ensureReady(context)
        Engine.awaitIdle()
        val request = YoutubeDLRequest(url)
            .addOption("--dump-single-json")
            .addOption("--no-playlist")
            .addOption("--no-warnings")
        val response = runInterruptible(Dispatchers.IO) {
            YoutubeDL.getInstance().execute(request, null, false, null)
        }
        val out = response.out
        val start = out.indexOf('{')
        if (start < 0) throw YoutubeDLException("yt-dlp returned no video information")
        return parse(JSONObject(out.substring(start)), url)
    }

    fun friendlyError(e: Throwable): String {
        val raw = e.message.orEmpty()
        val line = raw.lines().lastOrNull { it.contains("ERROR") }
            ?: raw.lines().lastOrNull { it.isNotBlank() }
            ?: e.javaClass.simpleName
        return line.substringAfter("ERROR: ").let {
            when {
                it.contains("Sign in to confirm", true) ->
                    "YouTube asked for a sign-in check. Try again later or update the engine from the menu."
                it.contains("Private video", true) -> "This video is private."
                it.contains("not a valid URL", true) || it.contains("Unsupported URL", true) ->
                    "That doesn't look like a YouTube link."
                else -> it
            }
        }
    }

    private fun JSONObject.str(key: String): String? =
        if (has(key) && !isNull(key)) optString(key).takeIf { it.isNotEmpty() } else null

    private fun JSONObject.num(key: String): Double? =
        if (has(key) && !isNull(key)) optDouble(key).takeIf { !it.isNaN() } else null

    private data class Fmt(
        val id: String,
        val ext: String,
        val vcodec: String?,
        val acodec: String?,
        val width: Int,
        val height: Int,
        val fps: Int,
        val tbr: Double,
        val abr: Double,
        val size: Long?,
        val protocol: String,
        val hdr: Boolean,
    ) {
        val hasVideo get() = vcodec != null && vcodec != "none"

        // HLS (live) entries sometimes omit acodec even though they carry sound.
        val hasAudio
            get() = when (acodec) {
                null -> protocol.startsWith("m3u8")
                "none" -> false
                else -> true
            }

        /** Use the short side so vertical Shorts read as 1080p rather than 1920p. */
        val resolution
            get() = if (width > 0 && height > 0) minOf(width, height) else height

        val codecName
            get() = vcodec.orEmpty().let {
                when {
                    it.startsWith("avc") -> "H.264"
                    it.startsWith("vp9") || it.startsWith("vp09") -> "VP9"
                    it.startsWith("av01") -> "AV1"
                    it.startsWith("hev") || it.startsWith("hvc") -> "HEVC"
                    else -> it.substringBefore('.').uppercase()
                }
            }

        /** Prefer the codec that plays everywhere, but still allow 4K which is VP9/AV1 only. */
        val codecRank
            get() = when (codecName) {
                "H.264" -> 0
                "VP9" -> 1
                "HEVC" -> 2
                "AV1" -> 3
                else -> 4
            }
    }

    private fun parse(json: JSONObject, requestedUrl: String): VideoDetails {
        val liveStatus = json.str("live_status")
        if (liveStatus == "is_upcoming") {
            throw YoutubeDLException("ERROR: This live stream hasn't started yet.")
        }
        val isLive = json.optBoolean("is_live") || liveStatus == "is_live"
        val webpageUrl = json.str("webpage_url") ?: requestedUrl
        val duration = json.num("duration")?.toLong() ?: 0L

        val formats = mutableListOf<Fmt>()
        val array = json.optJSONArray("formats")
        if (array != null) {
            for (i in 0 until array.length()) {
                val f = array.optJSONObject(i) ?: continue
                val protocol = f.str("protocol").orEmpty()
                if (protocol == "mhtml") continue // storyboard thumbnails
                formats += Fmt(
                    id = f.str("format_id") ?: continue,
                    ext = f.str("ext").orEmpty(),
                    vcodec = f.str("vcodec"),
                    acodec = f.str("acodec"),
                    width = f.num("width")?.toInt() ?: 0,
                    height = f.num("height")?.toInt() ?: 0,
                    fps = f.num("fps")?.roundToInt() ?: 0,
                    tbr = f.num("tbr") ?: 0.0,
                    abr = f.num("abr") ?: 0.0,
                    size = (f.num("filesize") ?: f.num("filesize_approx"))?.toLong(),
                    protocol = protocol,
                    hdr = f.str("dynamic_range")?.startsWith("HDR") == true,
                )
            }
        }

        val bestAudio = formats.filter { !it.hasVideo && it.hasAudio }
            .sortedWith(compareByDescending<Fmt> { it.ext == "m4a" }.thenByDescending { it.abr })
            .firstOrNull()

        val options = mutableListOf<QualityOption>()
        options += QualityOption(
            selector = if (isLive) "b/bv*+ba" else "bv*+ba[ext=m4a]/bv*+ba/b",
            title = "Best available",
            subtitle = if (isLive) "Highest live quality" else "Highest resolution, best audio",
            audio = AudioState.INCLUDED,
            sizeBytes = null,
        )

        val videos = formats.filter { it.hasVideo && it.resolution > 0 }
            .groupBy { Triple(it.resolution, it.fps, it.hasAudio) }
            .map { (_, group) ->
                group.sortedWith(
                    compareBy<Fmt> { it.codecRank }
                        .thenBy { it.hdr }
                        .thenByDescending { it.tbr }
                ).first()
            }
            .sortedWith(
                compareByDescending<Fmt> { it.resolution }
                    .thenByDescending { it.fps }
                    .thenByDescending { it.hasAudio }
            )

        for (f in videos) {
            val merged = !f.hasAudio
            val size = when {
                f.size == null -> null
                merged -> f.size + (bestAudio?.size ?: 0)
                else -> f.size
            }
            val fpsLabel = if (f.fps > 30) "${f.fps}" else ""
            options += QualityOption(
                selector = if (merged) "${f.id}+bestaudio[ext=m4a]/${f.id}+bestaudio/${f.id}" else f.id,
                title = "${f.resolution}p$fpsLabel" + if (f.hdr) " HDR" else "",
                subtitle = listOfNotNull(
                    if (merged) "MP4" else f.ext.uppercase(),
                    f.codecName.takeIf { it.isNotEmpty() },
                    if (isLive) "Live" else null,
                ).joinToString(" · "),
                audio = if (merged) AudioState.MERGED else AudioState.INCLUDED,
                sizeBytes = size,
            )
        }

        if (!isLive) {
            formats.filter { !it.hasVideo && it.hasAudio }
                .groupBy { it.ext }
                .map { (_, group) -> group.maxBy { it.abr } }
                .sortedWith(compareByDescending<Fmt> { it.ext == "m4a" }.thenByDescending { it.abr })
                .forEach { f ->
                    options += QualityOption(
                        selector = f.id,
                        title = "Audio ${f.abr.roundToInt()} kbps",
                        subtitle = if (f.ext == "webm") "WEBM (Opus)" else f.ext.uppercase(),
                        audio = AudioState.AUDIO_ONLY,
                        sizeBytes = f.size,
                    )
                }
        }

        val isShort = !isLive && (
            requestedUrl.contains("/shorts/") || webpageUrl.contains("/shorts/") ||
                (duration in 1..180 && json.optInt("height") > json.optInt("width"))
            )

        return VideoDetails(
            id = json.str("id").orEmpty(),
            title = json.str("title") ?: "YouTube video",
            uploader = json.str("uploader") ?: json.str("channel"),
            thumbnail = json.str("thumbnail"),
            durationSec = duration,
            kind = when {
                isLive -> VideoKind.LIVE
                isShort -> VideoKind.SHORT
                else -> VideoKind.VIDEO
            },
            webpageUrl = webpageUrl,
            options = options,
        )
    }
}
