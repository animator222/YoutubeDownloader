package com.animator222.ytdownloader.engine

enum class VideoKind { VIDEO, SHORT, LIVE }

enum class AudioState {
    /** The stream already contains sound. */
    INCLUDED,

    /** Video-only stream; the best audio track is downloaded too and merged in. */
    MERGED,

    /** Sound only. */
    AUDIO_ONLY,
}

data class QualityOption(
    /** yt-dlp format selector passed with -f. */
    val selector: String,
    val title: String,
    val subtitle: String,
    val audio: AudioState,
    val sizeBytes: Long?,
) {
    val isAudioOnly get() = audio == AudioState.AUDIO_ONLY
}

data class VideoDetails(
    val id: String,
    val title: String,
    val uploader: String?,
    val thumbnail: String?,
    val durationSec: Long,
    val kind: VideoKind,
    val webpageUrl: String,
    val options: List<QualityOption>,
)
