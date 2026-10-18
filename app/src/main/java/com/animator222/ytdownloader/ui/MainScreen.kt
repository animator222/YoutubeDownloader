package com.animator222.ytdownloader.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkInfo
import coil.compose.AsyncImage
import com.animator222.ytdownloader.download.DownloadItem
import com.animator222.ytdownloader.engine.AudioState
import com.animator222.ytdownloader.engine.QualityOption
import com.animator222.ytdownloader.engine.VideoDetails
import com.animator222.ytdownloader.engine.VideoKind
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    vm: MainViewModel,
    onOpen: (DownloadItem) -> Unit,
    onBeforeDownload: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val downloads by vm.downloads.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val clipboard = LocalClipboardManager.current
    var menuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            vm.messageShown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("YT Downloader", fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Menu")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = {
                                Text(if (state.engineBusy) "Updating engine…" else "Update download engine")
                            },
                            enabled = !state.engineBusy,
                            onClick = {
                                menuOpen = false
                                vm.updateEngine()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Clear finished downloads") },
                            onClick = {
                                menuOpen = false
                                vm.clearFinished()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("yt-dlp ${state.engineVersion ?: "…"}") },
                            enabled = false,
                            onClick = {},
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                OutlinedTextField(
                    value = state.url,
                    onValueChange = vm::onUrlChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("YouTube link (video, Short or live)") },
                    singleLine = true,
                    isError = state.error != null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { vm.fetch() }),
                    trailingIcon = {
                        if (state.url.isEmpty()) {
                            IconButton(onClick = {
                                clipboard.getText()?.text?.let {
                                    vm.onUrlChange(it.trim())
                                    vm.fetch()
                                }
                            }) { Icon(Icons.Filled.ContentPaste, contentDescription = "Paste") }
                        } else {
                            IconButton(onClick = { vm.onUrlChange("") }) {
                                Icon(Icons.Filled.Close, contentDescription = "Clear")
                            }
                        }
                    },
                )
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = vm::fetch, enabled = !state.loading) {
                        Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Get qualities")
                    }
                    Spacer(Modifier.weight(1f))
                    Icon(
                        Icons.Filled.Bolt,
                        contentDescription = null,
                        tint = if (state.turbo) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    )
                    Text("Turbo", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.width(8.dp))
                    Switch(checked = state.turbo, onCheckedChange = vm::setTurbo)
                }
            }

            state.error?.let { error ->
                item {
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
            }

            if (state.loading) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("Reading available qualities…")
                    }
                }
            }

            state.details?.let { details ->
                item { VideoHeader(details) }
                item {
                    Text("Choose quality", style = MaterialTheme.typography.titleMedium)
                }
                items(details.options, key = { it.selector + it.title }) { option ->
                    QualityRow(option, selected = option == state.selected, onClick = { vm.select(option) })
                }
                item {
                    Button(
                        onClick = {
                            onBeforeDownload()
                            vm.download()
                        },
                        enabled = state.selected != null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (details.kind == VideoKind.LIVE) "Start recording" else "Download")
                    }
                }
            }

            if (downloads.isNotEmpty()) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Downloads", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        if (downloads.any { !it.isActive }) {
                            TextButton(onClick = vm::clearFinished) {
                                Icon(Icons.Filled.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Clear finished")
                            }
                        }
                    }
                }
                items(downloads, key = { it.id.toString() }) { item ->
                    DownloadRow(
                        item = item,
                        onCancel = { vm.cancel(item.id) },
                        onStop = { vm.stopAndSave(item.id) },
                        onOpen = { onOpen(item) },
                    )
                }
            }

            if (state.details == null && !state.loading && downloads.isEmpty()) {
                item { EmptyHint() }
            }
        }
    }
}

@Composable
private fun EmptyHint() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Filled.Download,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.outline,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Paste a link, or tap Share → YT Downloader in the YouTube app.\n" +
                "Videos are saved to Movies/YT Downloader and show up in your gallery.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun VideoHeader(details: VideoDetails) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Box {
            AsyncImage(
                model = details.thumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
            val (label, color) = when (details.kind) {
                VideoKind.LIVE -> "● LIVE" to Color(0xFFD32F2F)
                VideoKind.SHORT -> "SHORT" to Color(0xFF7B1FA2)
                VideoKind.VIDEO -> formatDuration(details.durationSec) to Color(0xCC000000)
            }
            if (label.isNotEmpty()) {
                Text(
                    label,
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(color)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        Column(Modifier.padding(12.dp)) {
            Text(details.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            details.uploader?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun QualityRow(option: QualityOption, selected: Boolean, onClick: () -> Unit) {
    val border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        border = border,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            else MaterialTheme.colorScheme.surfaceContainer
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = onClick)
            Column(Modifier.weight(1f)) {
                Text(option.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    listOfNotNull(option.subtitle, option.sizeBytes?.let { "~" + formatSize(it) }).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AudioBadge(option.audio)
            Spacer(Modifier.width(8.dp))
        }
    }
}

@Composable
private fun AudioBadge(audio: AudioState) {
    val (icon, text, color) = when (audio) {
        AudioState.INCLUDED -> Triple(Icons.AutoMirrored.Filled.VolumeUp, "Audio", Color(0xFF2E7D32))
        AudioState.MERGED -> Triple(Icons.AutoMirrored.Filled.VolumeOff, "No audio · added", Color(0xFFEF6C00))
        AudioState.AUDIO_ONLY -> Triple(Icons.Filled.MusicNote, "Audio only", Color(0xFF1565C0))
    }
    Badge(icon, text, color)
}

@Composable
private fun Badge(icon: ImageVector, text: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.12f),
        contentColor = color,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(text, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun DownloadRow(
    item: DownloadItem,
    onCancel: () -> Unit,
    onStop: () -> Unit,
    onOpen: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val icon = when (item.state) {
                    WorkInfo.State.SUCCEEDED -> Icons.Filled.CheckCircle
                    WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> Icons.Filled.ErrorOutline
                    else -> if (item.isAudio) Icons.Filled.MusicNote else Icons.Filled.Download
                }
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(
                    item.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(8.dp))
            when (item.state) {
                WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> {
                    Text("Waiting for network…", style = MaterialTheme.typography.bodySmall)
                    LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 6.dp))
                }
                WorkInfo.State.RUNNING -> {
                    if (item.progress in 0f..100f && !item.isLive) {
                        LinearProgressIndicator(
                            progress = { item.progress / 100f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    Text(
                        item.status ?: "Starting…",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                WorkInfo.State.SUCCEEDED -> Text(
                    "Saved to gallery",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF2E7D32),
                )
                WorkInfo.State.FAILED -> Text(
                    item.error ?: "Failed",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                WorkInfo.State.CANCELLED -> Text("Cancelled", style = MaterialTheme.typography.bodySmall)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                if (item.isActive) {
                    if (item.isLive && item.state == WorkInfo.State.RUNNING) {
                        OutlinedButton(onClick = onStop) {
                            Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Stop & save")
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    TextButton(onClick = onCancel) { Text("Cancel") }
                } else if (item.state == WorkInfo.State.SUCCEEDED && item.uri != null) {
                    TextButton(onClick = onOpen) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Open")
                    }
                }
            }
        }
    }
}

private fun formatSize(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024) String.format(Locale.US, "%.2f GB", mb / 1024) else String.format(Locale.US, "%.1f MB", mb)
}

private fun formatDuration(seconds: Long): String {
    if (seconds <= 0) return ""
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s) else String.format(Locale.US, "%d:%02d", m, s)
}
