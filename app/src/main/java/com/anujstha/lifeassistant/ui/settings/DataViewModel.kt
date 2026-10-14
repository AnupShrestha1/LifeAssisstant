package com.anujstha.lifeassistant.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.anujstha.lifeassistant.appContainer
import com.anujstha.lifeassistant.data.export.TransactionExporter
import com.anujstha.lifeassistant.data.repository.DataResetter
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

sealed interface DataMessage {
    data class Exported(val count: Int) : DataMessage
    data object ExportFailed : DataMessage
    data object Cleared : DataMessage
}

/** Settings → "Your data": CSV export and clearing all data. */
class DataViewModel(
    private val exporter: TransactionExporter,
    private val resetter: DataResetter,
) : ViewModel() {
    private val channel = Channel<DataMessage>(Channel.BUFFERED)
    val messages: Flow<DataMessage> = channel.receiveAsFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    fun exportCsv(uri: Uri) {
        viewModelScope.launch {
            channel.send(runCatching { exporter.export(uri) }.fold({ DataMessage.Exported(it) }, { DataMessage.ExportFailed }))
        }
    }

    fun clearAll() {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            try {
                resetter.clearAll()
                channel.send(DataMessage.Cleared)
            } finally {
                _busy.value = false
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val c = appContainer()
                DataViewModel(c.transactionExporter, c.dataResetter)
            }
        }
    }
}
