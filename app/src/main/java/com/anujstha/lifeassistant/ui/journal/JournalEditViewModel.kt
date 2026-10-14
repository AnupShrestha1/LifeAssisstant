package com.anujstha.lifeassistant.ui.journal

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
import com.anujstha.lifeassistant.appContainer
import com.anujstha.lifeassistant.data.entity.JournalTagEntity
import com.anujstha.lifeassistant.data.preferences.SettingsRepository
import com.anujstha.lifeassistant.data.repository.JournalRepository
import com.anujstha.lifeassistant.domain.model.Mood
import com.anujstha.lifeassistant.domain.model.ValidationException
import com.anujstha.lifeassistant.ui.navigation.JournalEditRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

/** The editable fields; compared with the loaded snapshot to detect unsaved changes. */
data class JournalDraft(
    val title: String = "",
    val body: String = "",
    val date: LocalDate = LocalDate.now(),
    val mood: Mood? = null,
    val tagIds: Set<String> = emptySet(),
)

data class JournalEditState(
    val loaded: Boolean = false,
    val isNew: Boolean = true,
    val draft: JournalDraft = JournalDraft(),
    val original: JournalDraft = JournalDraft(),
    val saving: Boolean = false,
    val error: String? = null,
    val finished: Boolean = false,
) {
    val isDirty: Boolean get() = loaded && draft != original
}

class JournalEditViewModel(
    private val entryId: String?,
    initialDate: LocalDate?,
    private val repository: JournalRepository,
    settingsRepository: SettingsRepository,
    clock: Clock,
    /** Survives process death, so a half-written entry isn't lost when Android reclaims memory. */
    private val savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {

    private val _state = MutableStateFlow(JournalEditState(isNew = entryId == null))
    val state: StateFlow<JournalEditState> = _state.asStateFlow()

    val tags: StateFlow<List<JournalTagEntity>> = repository.observeTags()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            // Restoring after process death: use the saved draft instead of reloading.
            val savedDraft = savedState.get<String>(KEY_DRAFT)?.let(::decodeDraft)
            val savedOriginal = savedState.get<String>(KEY_ORIGINAL)?.let(::decodeDraft)
            if (savedDraft != null && savedOriginal != null) {
                _state.value = JournalEditState(loaded = true, isNew = entryId == null, draft = savedDraft, original = savedOriginal)
                return@launch
            }
            val draft = if (entryId != null) {
                val item = repository.observeEntry(entryId).first()
                if (item == null) {
                    _state.update { it.copy(loaded = true, finished = true) }
                    return@launch
                }
                JournalDraft(item.entry.title, item.entry.body, item.entry.entryDate, item.entry.mood, item.tags.map { it.id }.toSet())
            } else {
                val today = LocalDate.now(clock.withZone(ZoneId.of(settingsRepository.settings.first().displayZoneId)))
                JournalDraft(date = initialDate ?: today)
            }
            _state.value = JournalEditState(loaded = true, isNew = entryId == null, draft = draft, original = draft)
            savedState[KEY_ORIGINAL] = encodeDraft(draft)
            savedState[KEY_DRAFT] = encodeDraft(draft)
        }
    }

    private fun edit(block: (JournalDraft) -> JournalDraft) {
        _state.update { it.copy(draft = block(it.draft), error = null) }
        savedState[KEY_DRAFT] = encodeDraft(_state.value.draft)
    }

    fun setTitle(v: String) = edit { it.copy(title = v) }
    fun setBody(v: String) = edit { it.copy(body = v) }
    fun setDate(v: LocalDate) = edit { it.copy(date = v) }

    /** Tapping the selected mood again clears it (mood is optional). */
    fun toggleMood(v: Mood) = edit { it.copy(mood = if (it.mood == v) null else v) }

    fun toggleTag(id: String) = edit { it.copy(tagIds = if (id in it.tagIds) it.tagIds - id else it.tagIds + id) }

    /** Creates (or finds) the tag and selects it for this entry. */
    fun addTag(name: String) {
        viewModelScope.launch {
            runCatching { repository.createTag(name) }
                .onSuccess { tag -> edit { it.copy(tagIds = it.tagIds + tag.id) } }
                .onFailure { e -> _state.update { it.copy(error = e.message) } }
        }
    }

    fun save() {
        val s = _state.value
        if (!s.loaded || s.saving) return
        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            try {
                val d = s.draft
                repository.saveEntry(entryId, d.title, d.body, d.date, d.mood, d.tagIds)
                _state.update { it.copy(saving = false, finished = true) }
            } catch (e: ValidationException) {
                _state.update { it.copy(saving = false, error = e.message) }
            } catch (e: NoSuchElementException) {
                _state.update { it.copy(saving = false, finished = true) }
            }
        }
    }

    companion object {
        private const val KEY_DRAFT = "journal_draft"
        private const val KEY_ORIGINAL = "journal_original"
        private val draftJson = Json { ignoreUnknownKeys = true }

        @Serializable
        private data class SavedDraft(val title: String, val body: String, val date: String, val mood: String?, val tagIds: List<String>)

        internal fun encodeDraft(d: JournalDraft): String = draftJson.encodeToString(
            SavedDraft.serializer(),
            SavedDraft(d.title, d.body, d.date.toString(), d.mood?.name, d.tagIds.toList()),
        )

        internal fun decodeDraft(text: String): JournalDraft? = runCatching {
            val s = draftJson.decodeFromString(SavedDraft.serializer(), text)
            JournalDraft(s.title, s.body, LocalDate.parse(s.date), s.mood?.let(Mood::valueOf), s.tagIds.toSet())
        }.getOrNull()

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val handle = createSavedStateHandle()
                val route = handle.toRoute<JournalEditRoute>()
                val c = appContainer()
                JournalEditViewModel(
                    route.entryId,
                    route.date?.let(LocalDate::parse),
                    c.journalRepository,
                    c.settingsRepository,
                    c.clock,
                    handle,
                )
            }
        }
    }
}
