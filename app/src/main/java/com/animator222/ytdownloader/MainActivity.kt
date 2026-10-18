package com.animator222.ytdownloader

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.animator222.ytdownloader.download.DownloadItem
import com.animator222.ytdownloader.ui.AppTheme
import com.animator222.ytdownloader.ui.MainScreen
import com.animator222.ytdownloader.ui.MainViewModel

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleShare(intent)
        setContent {
            AppTheme {
                MainScreen(
                    vm = vm,
                    onOpen = ::open,
                    onBeforeDownload = ::ensurePermissions,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    private fun handleShare(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND) {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.let(vm::onSharedText)
        }
    }

    /** Notifications (progress in background) on 13+, shared-storage write access on 8–9. */
    private fun ensurePermissions() {
        val needed = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (needed.isNotEmpty()) permissionLauncher.launch(needed.toTypedArray())
    }

    private fun open(item: DownloadItem) {
        val uri = Uri.parse(item.uri ?: return)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, item.mime ?: contentResolver.getType(uri) ?: "video/*")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "No app found to play this file", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "The file was moved or deleted", Toast.LENGTH_SHORT).show()
        }
    }
}
