package com.anujstha.lifeassistant.ui.expenses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
import com.anujstha.lifeassistant.appContainer
import com.anujstha.lifeassistant.data.entity.ExpenseCategoryEntity
import com.anujstha.lifeassistant.data.preferences.SettingsRepository
import com.anujstha.lifeassistant.data.repository.ExpenseCategoryRepository
import com.anujstha.lifeassistant.data.repository.TransactionInput
import com.anujstha.lifeassistant.data.repository.TransactionRepository
import com.anujstha.lifeassistant.domain.model.PaymentMethod
import com.anujstha.lifeassistant.domain.model.TimeFormat
import com.anujstha.lifeassistant.domain.model.TransactionType
import com.anujstha.lifeassistant.domain.model.ValidationException
import com.anujstha.lifeassistant.ui.navigation.TransactionEditRoute
import com.anujstha.lifeassistant.util.Money
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale

data class TransactionEditState(
    val loaded: Boolean = false,
    val isNew: Boolean = true,
    val type: TransactionType = TransactionType.EXPENSE,
    val amountText: String = "",
    val currency: String = "NPR",
    val categoryId: String? = null,
    val date: LocalDate = LocalDate.now(),
    val time: LocalTime? = null,
    val payment: PaymentMethod = PaymentMethod.CASH,
    val note: String = "",
    val amountInvalid: Boolean = false,
    val error: String? = null,
    val saving: Boolean = false,
    val finished: Boolean = false,
)

class TransactionEditViewModel(
    private val transactionId: String?,
    private val transactions: TransactionRepository,
    private val categories: ExpenseCategoryRepository,
    settingsRepository: SettingsRepository,
    clock: Clock,
    private val locale: Locale = Locale.getDefault(),
) : ViewModel() {

    private val _state = MutableStateFlow(TransactionEditState(isNew = transactionId == null))
    val state: StateFlow<TransactionEditState> = _state.asStateFlow()

    /** Categories offered for the current type; an archived category stays visible if this transaction uses it. */
    val categoryChoices: StateFlow<List<ExpenseCategoryEntity>> = combine(categories.observeAll(), _state) { all, s ->
        all.filter { it.type == s.type && (it.archivedAt == null || it.id == s.categoryId) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val timeFormat: StateFlow<TimeFormat> = settingsRepository.settings.map { it.timeFormat }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TimeFormat.SYSTEM)

    init {
        viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            _state.value = if (transactionId != null) {
                val t = transactions.observe(transactionId).first()
                if (t == null) {
                    TransactionEditState(loaded = true, finished = true)
                } else {
                    TransactionEditState(
                        loaded = true,
                        isNew = false,
                        type = t.type,
                        amountText = Money.plain(t.amountMinor, t.currencyCode),
                        currency = t.currencyCode, // editing never relabels an existing amount
                        categoryId = t.categoryId,
                        date = t.date,
                        time = t.time,
                        payment = t.paymentMethod,
                        note = t.note.orEmpty(),
                    )
                }
            } else {
                TransactionEditState(
                    loaded = true,
                    currency = settings.currencyCode,
                    date = LocalDate.now(clock.withZone(ZoneId.of(settings.displayZoneId))),
                )
            }
        }
    }

    private fun edit(block: (TransactionEditState) -> TransactionEditState) =
        _state.update { block(it).copy(error = null) }

    /** Switching type clears the category, since categories belong to one type. */
    fun setType(v: TransactionType) = edit { if (it.type == v) it else it.copy(type = v, categoryId = null) }
    fun setAmount(v: String) = edit { it.copy(amountText = v, amountInvalid = false) }
    fun setCategory(v: String) = edit { it.copy(categoryId = v) }
    fun setDate(v: LocalDate) = edit { it.copy(date = v) }
    fun setTime(v: LocalTime?) = edit { it.copy(time = v) }
    fun setPayment(v: PaymentMethod) = edit { it.copy(payment = v) }
    fun setNote(v: String) = edit { it.copy(note = v) }

    fun createCategory(name: String) {
        viewModelScope.launch {
            runCatching { categories.create(name, _state.value.type) }
                .onSuccess { c -> edit { it.copy(categoryId = c.id) } }
                .onFailure { e -> _state.update { it.copy(error = e.message) } }
        }
    }

    fun save() {
        val s = _state.value
        if (!s.loaded || s.saving) return
        val amount = Money.parse(s.amountText, s.currency, locale)
        if (amount == null || amount <= 0) {
            _state.update { it.copy(amountInvalid = true) }
            return
        }
        val categoryId = s.categoryId ?: run {
            _state.update { it.copy(error = "Choose a category") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            try {
                transactions.save(
                    transactionId,
                    TransactionInput(amount, s.currency, s.type, categoryId, s.date, s.time, s.payment, s.note),
                )
                _state.update { it.copy(saving = false, finished = true) }
            } catch (e: ValidationException) {
                _state.update { it.copy(saving = false, error = e.message) }
            }
        }
    }

    fun delete() {
        val id = transactionId ?: return
        viewModelScope.launch {
            transactions.delete(id)
            _state.update { it.copy(finished = true) }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val c = appContainer()
                TransactionEditViewModel(
                    createSavedStateHandle().toRoute<TransactionEditRoute>().transactionId,
                    c.transactionRepository, c.expenseCategoryRepository, c.settingsRepository, c.clock,
                )
            }
        }
    }
}
