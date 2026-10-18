package com.animator222.ytdownloader.download

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Handles the "Cancel" / "Stop & save" buttons on the download notification. */
class DownloadActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val jobId = intent.getStringExtra(EXTRA_JOB_ID) ?: return
        when (intent.action) {
            ACTION_CANCEL -> DownloadControl.cancel(context, jobId)
            ACTION_STOP -> DownloadControl.stopAndSave(context, jobId)
        }
    }

    companion object {
        const val ACTION_CANCEL = "com.animator222.ytdownloader.CANCEL"
        const val ACTION_STOP = "com.animator222.ytdownloader.STOP"
        const val EXTRA_JOB_ID = "job_id"
    }
}
