package com.animator222.ytdownloader.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.animator222.ytdownloader.Prefs
import com.animator222.ytdownloader.download.DownloadControl
import com.animator222.ytdownloader.download.DownloadItem
import com.animator222.ytdownloader.download.DownloadQueue
import com.animator222.ytdownloader.engine.Engine
import com.animator222.ytdownloader.engine.QualityOption
import com.animator222.ytdownloader.engine.VideoDetails
import com.animator222.ytdownloader.engine.YtInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class UiState(
    val url: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    val details: VideoDetails? = null,
    val selected: QualityOption? = null,
    val turbo: Boolean = true,
    val engineVersion: String? = null,
    val engineBusy: Boolean = false,
    val message: String? = null,
)

class MainViewModel(private val app: Application) : AndroidViewModel(app) {
    private val _state = MutableStateFlow(UiState(turbo = Prefs.turbo(app)))
    val state: StateFlow<UiState> = _state.asStateFlow()

    val downloads: StateFlow<List<DownloadItem>> = DownloadQueue.observe(app)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var fetchJob: Job? = null

    init {
        viewModelScope.launch {
            try {
                Engine.ensureReady(app)
                _state.update { it.copy(engineVersion = Engine.version(app)) }
                // Bring yt-dlp up to date right away so the first fetch doesn't wait for it.
                Engine.prepare(app)
                _state.update { it.copy(engineVersion = Engine.version(app)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // Offline or GitHub unreachable: keep using the bundled yt-dlp.
            }
        }
    }

    fun onUrlChange(url: String) {
        _state.update { it.copy(url = url, error = null) }
    }

    fun onSharedText(text: String) {
        val url = YtInfo.extractUrl(text) ?: return
        _state.update { it.copy(url = url, error = null) }
        fetch()
    }

    fun fetch() {
        val url = YtInfo.extractUrl(_state.value.url)
        if (url == null) {
            _state.update { it.copy(error = "Paste a YouTube link first.") }
            return
        }
        fetchJob?.cancel()
        _state.update { it.copy(loading = true, error = null, details = null, selected = null) }
        fetchJob = viewModelScope.launch {
            try {
                val details = YtInfo.fetch(app, url)
                _state.update {
                    it.copy(loading = false, details = details, selected = details.options.firstOrNull())
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _state.update { it.copy(loading = false, error = YtInfo.friendlyError(e)) }
            }
        }
    }

    fun select(option: QualityOption) {
        _state.update { it.copy(selected = option) }
    }

    fun download() {
        val s = _state.value
        val details = s.details ?: return
        val option = s.selected ?: return
        DownloadQueue.enqueue(app, details, option, s.turbo)
        _state.update { it.copy(message = "Download started — it continues in the background.") }
    }

    fun setTurbo(enabled: Boolean) {
        Prefs.setTurbo(app, enabled)
        _state.update { it.copy(turbo = enabled) }
    }

    fun cancel(id: UUID) = DownloadControl.cancel(app, id.toString())

    fun stopAndSave(id: UUID) = DownloadControl.stopAndSave(app, id.toString())

    fun clearFinished() = DownloadQueue.clearFinished(app)

    fun messageShown() {
        _state.update { it.copy(message = null) }
    }

    fun updateEngine() {
        viewModelScope.launch {
            if (DownloadQueue.hasActive(app)) {
                _state.update { it.copy(message = "Wait for downloads to finish before updating.") }
                return@launch
            }
            _state.update { it.copy(engineBusy = true) }
            val message = try {
                when (Engine.update(app, force = true)) {
                    Engine.UpdateResult.UPDATED -> "Engine updated to yt-dlp ${Engine.version(app)}."
                    else -> "Engine is up to date (yt-dlp ${Engine.version(app)})."
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                "Update failed: ${e.message}"
            }
            _state.update { it.copy(engineBusy = false, message = message, engineVersion = Engine.version(app)) }
        }
    }
}
