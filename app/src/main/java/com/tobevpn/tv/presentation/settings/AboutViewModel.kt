package com.tobevpn.tv.presentation.settings

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tobevpn.tv.util.DiagnosticLogFileInfo
import com.tobevpn.tv.util.DiagnosticLogManager
import com.tobevpn.tv.util.SafeDiagnostics
import com.tobevpn.tv.vpn.XRayCore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class DiagnosticHistoryUiState(
    val logs: List<DiagnosticLogFileInfo> = emptyList(),
    val isLoading: Boolean = false,
    val deletingFileName: String? = null,
)

sealed interface DiagnosticUiEvent {
    data class ModeChanged(val enabled: Boolean) : DiagnosticUiEvent
    data class ShareLog(val intent: Intent, val fileName: String) : DiagnosticUiEvent
    data class LogExported(val location: String) : DiagnosticUiEvent
    data object NoLogToExport : DiagnosticUiEvent
    data object OperationFailed : DiagnosticUiEvent
}

@HiltViewModel
class AboutViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val diagnosticLogManager: DiagnosticLogManager,
) : ViewModel() {
    val diagnosticState = diagnosticLogManager.state

    private val _xrayVersion = MutableStateFlow<String?>(null)
    val xrayVersion: StateFlow<String?> = _xrayVersion.asStateFlow()

    private val _history = MutableStateFlow(DiagnosticHistoryUiState())
    val history: StateFlow<DiagnosticHistoryUiState> = _history.asStateFlow()

    private val _events = MutableSharedFlow<DiagnosticUiEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<DiagnosticUiEvent> = _events.asSharedFlow()
    private var diagnosticModeToggleJob: Job? = null

    init {
        viewModelScope.launch {
            _xrayVersion.value = withContext(Dispatchers.IO) { XRayCore.getVersion() }
            runCatching { diagnosticLogManager.refresh() }
        }
    }

    fun toggleDiagnosticMode() {
        if (diagnosticModeToggleJob?.isActive == true) return
        diagnosticModeToggleJob = viewModelScope.launch {
            runCatching {
                val enabled = !diagnosticLogManager.state.value.debugModeEnabled
                diagnosticLogManager.setDebugModeEnabled(enabled)
                _events.emit(DiagnosticUiEvent.ModeChanged(enabled))
            }.onFailure(::reportFailure)
        }
    }

    fun toggleCollection() {
        viewModelScope.launch {
            runCatching {
                diagnosticLogManager.setCollectionEnabled(
                    !diagnosticLogManager.state.value.collecting,
                )
            }.onFailure(::reportFailure)
        }
    }

    fun loadHistory() {
        viewModelScope.launch {
            _history.value = _history.value.copy(isLoading = true)
            runCatching { diagnosticLogManager.logHistory() }
                .onSuccess { logs -> _history.value = DiagnosticHistoryUiState(logs = logs) }
                .onFailure {
                    _history.value = _history.value.copy(isLoading = false)
                    reportFailure(it)
                }
        }
    }

    fun shareLog(fileName: String) {
        viewModelScope.launch {
            runCatching {
                val file = diagnosticLogManager.logForSharing(fileName)
                if (file == null) {
                    _events.emit(DiagnosticUiEvent.NoLogToExport)
                    return@runCatching
                }
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file,
                )
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    clipData = ClipData.newUri(context.contentResolver, file.name, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                if (hasUsableShareTarget(intent)) {
                    _events.emit(DiagnosticUiEvent.ShareLog(intent, file.name))
                } else {
                    exportLogFile(file)
                }
            }.onFailure(::reportFailure)
        }
    }

    fun saveLogToDownloads(fileName: String) {
        viewModelScope.launch {
            runCatching {
                val file = diagnosticLogManager.logForSharing(fileName)
                if (file == null) {
                    _events.emit(DiagnosticUiEvent.NoLogToExport)
                    return@runCatching
                }
                exportLogFile(file)
            }.onFailure(::reportFailure)
        }
    }

    fun deleteLog(fileName: String) {
        if (_history.value.deletingFileName != null) return
        viewModelScope.launch {
            _history.value = _history.value.copy(deletingFileName = fileName)
            runCatching {
                val deleted = diagnosticLogManager.deleteLog(fileName)
                _history.value = DiagnosticHistoryUiState(
                    logs = diagnosticLogManager.logHistory(),
                )
                if (!deleted) _events.emit(DiagnosticUiEvent.OperationFailed)
            }.onFailure {
                _history.value = _history.value.copy(deletingFileName = null)
                reportFailure(it)
            }
        }
    }

    private fun reportFailure(error: Throwable) {
        SafeDiagnostics.warn(
            TAG,
            "Diagnostic action failed: ${SafeDiagnostics.failureCategory(error)}",
        )
        _events.tryEmit(DiagnosticUiEvent.OperationFailed)
    }

    @Suppress("DEPRECATION")
    private fun hasUsableShareTarget(intent: Intent): Boolean =
        context.packageManager
            .queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            .any { target ->
                val activity = target.activityInfo ?: return@any false
                activity.enabled &&
                    activity.applicationInfo.enabled &&
                    activity.packageName != TV_FRAMEWORK_STUB_PACKAGE &&
                    !activity.name.orEmpty().contains("Stub", ignoreCase = true)
            }

    private suspend fun exportLogFile(file: File) {
        val location = withContext(Dispatchers.IO) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                exportToSharedDownloads(file)
            } else {
                exportToAppDownloads(file)
            }
        }
        _events.emit(DiagnosticUiEvent.LogExported(location))
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun exportToSharedDownloads(source: File): String {
        val relativeDirectory = "${Environment.DIRECTORY_DOWNLOADS}/$EXPORT_DIRECTORY"
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, source.name)
            // MediaProvider appends ".txt" to unknown extensions when this is
            // declared as text/plain. Keep the original user-facing .log name.
            put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
            put(MediaStore.Downloads.RELATIVE_PATH, relativeDirectory)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("Unable to create diagnostic export")
        try {
            resolver.openOutputStream(uri, "w")?.use { output ->
                source.inputStream().buffered().use { input -> input.copyTo(output) }
            } ?: error("Unable to open diagnostic export")
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (error: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            throw error
        }
        val exportedName = resolver.query(
            uri,
            arrayOf(MediaStore.Downloads.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }.orEmpty().ifBlank { source.name }
        return "$relativeDirectory/$exportedName"
    }

    private fun exportToAppDownloads(source: File): String {
        val downloads = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: File(context.filesDir, "exports")
        val directory = File(downloads, EXPORT_DIRECTORY)
        check(directory.exists() || directory.mkdirs()) {
            "Unable to create diagnostic export directory"
        }
        val destination = File(directory, source.name)
        source.copyTo(destination, overwrite = true)
        return destination.absolutePath
    }

    private companion object {
        const val TAG = "AboutViewModel"
        const val EXPORT_DIRECTORY = "ToBeVPN"
        const val TV_FRAMEWORK_STUB_PACKAGE = "com.android.tv.frameworkpackagestubs"
    }
}
