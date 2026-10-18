package com.animator222.ytdownloader.download

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import kotlin.coroutines.resume

/** Publishes finished files to shared storage so they appear in the Gallery / Music apps. */
object MediaSaver {
    const val FOLDER = "YT Downloader"

    data class Saved(val uri: Uri, val name: String, val mime: String)

    fun mimeFor(file: File, isAudio: Boolean): String = when (file.extension.lowercase()) {
        "mp4" -> if (isAudio) "audio/mp4" else "video/mp4"
        "m4a" -> "audio/mp4"
        "webm" -> if (isAudio) "audio/webm" else "video/webm"
        "mkv" -> "video/x-matroska"
        "mov" -> "video/quicktime"
        "3gp" -> "video/3gpp"
        "ts" -> "video/mp2t"
        "mp3" -> "audio/mpeg"
        "opus", "ogg" -> "audio/ogg"
        "aac" -> "audio/aac"
        else -> if (isAudio) "audio/*" else "video/*"
    }

    suspend fun save(context: Context, file: File, isAudio: Boolean): Saved = withContext(Dispatchers.IO) {
        val mime = mimeFor(file, isAudio)
        val baseDir = if (isAudio) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_MOVIES
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val collection = if (isAudio) {
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            }
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "$baseDir/$FOLDER")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(collection, values) ?: throw IOException("Couldn't create a gallery entry")
            try {
                resolver.openOutputStream(uri)?.use { out ->
                    file.inputStream().use { it.copyTo(out, 1 shl 20) }
                } ?: throw IOException("Couldn't open gallery entry")
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
            Saved(uri, file.name, mime)
        } else {
            @Suppress("DEPRECATION")
            val dir = File(Environment.getExternalStoragePublicDirectory(baseDir), FOLDER).apply { mkdirs() }
            val target = uniqueFile(dir, file.name)
            if (!file.renameTo(target)) file.copyTo(target, overwrite = true)
            val uri = scan(context, target, mime) ?: Uri.fromFile(target)
            Saved(uri, target.name, mime)
        }
    }

    private fun uniqueFile(dir: File, name: String): File {
        var candidate = File(dir, name)
        var i = 1
        while (candidate.exists()) {
            candidate = File(dir, "${name.substringBeforeLast('.')} ($i).${name.substringAfterLast('.')}")
            i++
        }
        return candidate
    }

    private suspend fun scan(context: Context, file: File, mime: String): Uri? =
        suspendCancellableCoroutine { cont ->
            MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf(mime)) { _, uri ->
                if (cont.isActive) cont.resume(uri)
            }
        }
}
