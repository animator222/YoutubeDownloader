package com.animator222.ytdownloader.download

import android.content.Context
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.animator222.ytdownloader.engine.QualityOption
import com.animator222.ytdownloader.engine.VideoDetails
import com.animator222.ytdownloader.engine.VideoKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

data class DownloadItem(
    val id: UUID,
    val title: String,
    val state: WorkInfo.State,
    val progress: Float,
    val status: String?,
    val isLive: Boolean,
    val isAudio: Boolean,
    val uri: String?,
    val mime: String?,
    val error: String?,
    val createdAt: Long,
) {
    val isActive get() = !state.isFinished
}

object DownloadQueue {
    fun enqueue(context: Context, video: VideoDetails, option: QualityOption, turbo: Boolean): UUID {
        val isLive = video.kind == VideoKind.LIVE
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(
                workDataOf(
                    DownloadWorker.KEY_URL to video.webpageUrl,
                    DownloadWorker.KEY_FORMAT to option.selector,
                    DownloadWorker.KEY_TITLE to video.title,
                    DownloadWorker.KEY_LIVE to isLive,
                    DownloadWorker.KEY_AUDIO to option.isAudioOnly,
                    DownloadWorker.KEY_TURBO to turbo,
                )
            )
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .addTag(DownloadWorker.TAG)
            .addTag(DownloadWorker.TAG_TITLE_PREFIX + "${video.title} · ${option.title}")
            .addTag(DownloadWorker.TAG_CREATED_PREFIX + System.currentTimeMillis())
            .apply {
                if (isLive) addTag(DownloadWorker.TAG_LIVE)
                if (option.isAudioOnly) addTag(DownloadWorker.TAG_AUDIO)
            }
            .build()
        WorkManager.getInstance(context).enqueue(request)
        return request.id
    }

    fun observe(context: Context): Flow<List<DownloadItem>> =
        WorkManager.getInstance(context).getWorkInfosByTagFlow(DownloadWorker.TAG).map { infos ->
            infos.map(::toItem).sortedByDescending { it.createdAt }
        }

    suspend fun hasActive(context: Context): Boolean =
        WorkManager.getInstance(context).getWorkInfosByTagFlow(DownloadWorker.TAG).first()
            .any { !it.state.isFinished }

    fun clearFinished(context: Context) {
        WorkManager.getInstance(context).pruneWork()
    }

    private fun toItem(info: WorkInfo): DownloadItem {
        fun tag(prefix: String) = info.tags.firstOrNull { it.startsWith(prefix) }?.removePrefix(prefix)
        return DownloadItem(
            id = info.id,
            title = tag(DownloadWorker.TAG_TITLE_PREFIX) ?: "Download",
            state = info.state,
            progress = info.progress.getFloat(DownloadWorker.KEY_PROGRESS, -1f),
            status = info.progress.getString(DownloadWorker.KEY_STATUS),
            isLive = DownloadWorker.TAG_LIVE in info.tags,
            isAudio = DownloadWorker.TAG_AUDIO in info.tags,
            uri = info.outputData.getString(DownloadWorker.KEY_OUT_URI),
            mime = info.outputData.getString(DownloadWorker.KEY_OUT_MIME),
            error = info.outputData.getString(DownloadWorker.KEY_ERROR),
            createdAt = tag(DownloadWorker.TAG_CREATED_PREFIX)?.toLongOrNull() ?: 0L,
        )
    }
}
