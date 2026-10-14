package com.anujstha.lifeassistant.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.anujstha.lifeassistant.appContainer
import com.anujstha.lifeassistant.data.backup.BackupData
import com.anujstha.lifeassistant.data.backup.BackupManager
import com.anujstha.lifeassistant.data.backup.BackupTooLargeException
import com.anujstha.lifeassistant.data.backup.DecodeResult
import com.anujstha.lifeassistant.data.backup.RestoreMode
import com.anujstha.lifeassistant.data.backup.RestorePreview
import com.anujstha.lifeassistant.data.backup.RestoreReport
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant

sealed interface BackupUiState {
    data object Idle : BackupUiState
    data object Working : BackupUiState

    /** A valid file was read; nothing is changed until the user confirms. */
    data class Ready(
        val data: BackupData,
        val appVersion: String,
        val exportedAt: Instant?,
        val mode: RestoreMode,
        val preview: RestorePreview,
    ) : BackupUiState

    data class Invalid(val errors: List<String>) : BackupUiState
}

sealed interface BackupMessage {
    data class Created(val records: Int) : BackupMessage
    data object CreateFailed : BackupMessage
    data object ReadFailed : BackupMessage
    data object TooLarge : BackupMessage
    data class Restored(val report: RestoreReport) : BackupMessage
    data object RestoreFailed : BackupMessage
}

class BackupViewModel(private val manager: BackupManager) : ViewModel() {
    private val _state = MutableStateFlow<BackupUiState>(BackupUiState.Idle)
    val state: StateFlow<BackupUiState> = _state.asStateFlow()

    private val channel = Channel<BackupMessage>(Channel.BUFFERED)
    val messages: Flow<BackupMessage> = channel.receiveAsFlow()

    fun createBackup(uri: Uri) = work {
        val message = runCatching { manager.export(uri) }.fold({ BackupMessage.Created(it) }, { BackupMessage.CreateFailed })
        _state.value = BackupUiState.Idle
        channel.send(message)
    }

    fun openBackup(uri: Uri) = work {
        val result = try {
            manager.read(uri)
        } catch (e: BackupTooLargeException) {
            _state.value = BackupUiState.Idle
            channel.send(BackupMessage.TooLarge)
            return@work
        } catch (e: Exception) {
            _state.value = BackupUiState.Idle
            channel.send(BackupMessage.ReadFailed)
            return@work
        }
        _state.value = when (result) {
            is DecodeResult.Invalid -> BackupUiState.Invalid(result.errors)
            is DecodeResult.Valid -> BackupUiState.Ready(
                result.data, result.appVersion, result.exportedAt, RestoreMode.MERGE,
                manager.preview(result.data, RestoreMode.MERGE),
            )
        }
    }

    fun setMode(mode: RestoreMode) {
        val ready = _state.value as? BackupUiState.Ready ?: return
        viewModelScope.launch {
            val preview = manager.preview(ready.data, mode)
            _state.update { (it as? BackupUiState.Ready)?.copy(mode = mode, preview = preview) ?: it }
        }
    }

    fun restore() {
        val ready = _state.value as? BackupUiState.Ready ?: return
        work {
            val message = runCatching { manager.restore(ready.data, ready.mode) }
                .fold({ BackupMessage.Restored(it) }, { BackupMessage.RestoreFailed })
            _state.value = BackupUiState.Idle
            channel.send(message)
        }
    }

    fun dismiss() {
        _state.value = BackupUiState.Idle
    }

    private fun work(block: suspend () -> Unit) {
        if (_state.value == BackupUiState.Working) return
        _state.value = BackupUiState.Working
        viewModelScope.launch { block() }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { BackupViewModel(appContainer().backupManager) }
        }
    }
}
