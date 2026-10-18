package com.animator222.ytdownloader.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.animator222.ytdownloader.MainActivity

object Notifications {
    const val CHANNEL_PROGRESS = "downloads"
    const val CHANNEL_DONE = "completed"

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_PROGRESS, "Active downloads", NotificationManager.IMPORTANCE_LOW)
                .apply { setShowBadge(false) }
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_DONE, "Finished downloads", NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    fun progressId(jobId: String) = jobId.hashCode()
    private fun doneId(jobId: String) = jobId.hashCode() + 1

    fun progress(
        context: Context,
        jobId: String,
        title: String,
        text: String,
        percent: Int,
        isLive: Boolean,
    ): Notification {
        val builder = NotificationCompat.Builder(context, CHANNEL_PROGRESS)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(openApp(context))
            .setProgress(100, percent.coerceIn(0, 100), isLive || percent < 0)
        if (isLive) {
            builder.addAction(0, "Stop & save", action(context, jobId, DownloadActionReceiver.ACTION_STOP))
        }
        builder.addAction(0, "Cancel", action(context, jobId, DownloadActionReceiver.ACTION_CANCEL))
        return builder.build()
    }

    fun done(context: Context, jobId: String, title: String, uri: Uri, mime: String) {
        val view = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        val pending = PendingIntent.getActivity(
            context, doneId(jobId), view,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        notify(
            context, doneId(jobId),
            NotificationCompat.Builder(context, CHANNEL_DONE)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("Saved to gallery")
                .setContentText(title)
                .setAutoCancel(true)
                .setContentIntent(pending)
                .build()
        )
    }

    fun failed(context: Context, jobId: String, title: String, reason: String) {
        notify(
            context, doneId(jobId),
            NotificationCompat.Builder(context, CHANNEL_DONE)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle("Download failed")
                .setContentText(title)
                .setStyle(NotificationCompat.BigTextStyle().bigText("$title\n$reason"))
                .setAutoCancel(true)
                .setContentIntent(openApp(context))
                .build()
        )
    }

    fun notify(context: Context, id: Int, notification: Notification) {
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (e: SecurityException) {
            // Notification permission denied; the download still runs and shows in the app.
        }
    }

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun action(context: Context, jobId: String, action: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            (jobId + action).hashCode(),
            Intent(context, DownloadActionReceiver::class.java)
                .setAction(action)
                .putExtra(DownloadActionReceiver.EXTRA_JOB_ID, jobId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
}
