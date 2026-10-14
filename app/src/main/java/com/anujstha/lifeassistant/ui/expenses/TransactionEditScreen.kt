package com.anujstha.lifeassistant.ui.expenses

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.anujstha.lifeassistant.R
import com.anujstha.lifeassistant.domain.model.PaymentMethod
import com.anujstha.lifeassistant.domain.model.TimeFormat
import com.anujstha.lifeassistant.domain.model.TransactionType
import com.anujstha.lifeassistant.ui.components.Choice
import com.anujstha.lifeassistant.ui.components.DatePickerModal
import com.anujstha.lifeassistant.ui.components.DropdownField
import com.anujstha.lifeassistant.ui.components.ScreenScaffold
import com.anujstha.lifeassistant.ui.components.TimePickerModal
import com.anujstha.lifeassistant.ui.components.rememberDateFormatter
import com.anujstha.lifeassistant.ui.components.rememberTimeFormatter
import com.anujstha.lifeassistant.ui.journal.TagNameDialog

private const val NEW_CATEGORY = "\u0000new"

private enum class TxnDialog { DATE, TIME, NEW_CATEGORY, DELETE }

@Composable
fun TransactionEditScreen(
    onDone: () -> Unit,
    viewModel: TransactionEditViewModel = viewModel(factory = TransactionEditViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val choices by viewModel.categoryChoices.collectAsStateWithLifecycle()
    val timeFormat by viewModel.timeFormat.collectAsStateWithLifecycle()
    var dialog by rememberSaveable { mutableStateOf<TxnDialog?>(null) }
    val dateFormatter = rememberDateFormatter()
    val timeFormatter = rememberTimeFormatter(timeFormat)
    val context = LocalContext.current

    LaunchedEffect(state.finished) { if (state.finished) onDone() }

    ScreenScaffold(
        title = stringResource(if (state.isNew) R.string.txn_edit_title_new else R.string.txn_edit_title_edit),
        onBack = onDone,
        actions = {
            if (!state.isNew) {
                IconButton(onClick = { dialog = TxnDialog.DELETE }) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete))
                }
            }
            TextButton(onClick = viewModel::save, enabled = state.loaded && !state.saving) {
                Text(stringResource(R.string.action_save))
            }
        },
    ) { padding ->
        if (!state.loaded) return@ScreenScaffold
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                TransactionType.entries.forEachIndexed { i, type ->
                    SegmentedButton(
                        selected = state.type == type,
                        onClick = { viewModel.setType(type) },
                        shape = SegmentedButtonDefaults.itemShape(i, TransactionType.entries.size),
                        label = { Text(stringResource(type.labelRes())) },
                    )
                }
            }
            OutlinedTextField(
                value = state.amountText,
                onValueChange = viewModel::setAmount,
                label = { Text(stringResource(R.string.txn_field_amount, state.currency)) },
                isError = state.amountInvalid,
                supportingText = if (state.amountInvalid) ({ Text(stringResource(R.string.txn_amount_invalid)) }) else null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                textStyle = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.fillMaxWidth(),
            )
            DropdownField(
                label = stringResource(R.string.txn_field_category),
                options = listOf(Choice<String?>(null, stringResource(R.string.txn_choose_category))) +
                    choices.map { Choice<String?>(it.id, it.name) } +
                    Choice<String?>(NEW_CATEGORY, stringResource(R.string.task_category_new)),
                selected = state.categoryId,
                onSelect = { id ->
                    when (id) {
                        NEW_CATEGORY -> dialog = TxnDialog.NEW_CATEGORY
                        null -> Unit
                        else -> viewModel.setCategory(id)
                    }
                },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { dialog = TxnDialog.DATE }) { Text(dateFormatter.format(state.date)) }
                OutlinedButton(onClick = { dialog = TxnDialog.TIME }) {
                    Text(state.time?.let(timeFormatter::format) ?: stringResource(R.string.txn_no_time))
                }
                if (state.time != null) {
                    TextButton(onClick = { viewModel.setTime(null) }) { Text(stringResource(R.string.clear)) }
                }
            }
            DropdownField(
                label = stringResource(R.string.txn_field_payment),
                options = PaymentMethod.entries.map { Choice(it, stringResource(it.labelRes())) },
                selected = state.payment,
                onSelect = viewModel::setPayment,
            )
            OutlinedTextField(
                value = state.note,
                onValueChange = viewModel::setNote,
                label = { Text(stringResource(R.string.txn_field_description)) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    when (dialog) {
        TxnDialog.DATE -> DatePickerModal(
            initial = state.date,
            onConfirm = { viewModel.setDate(it); dialog = null },
            onDismiss = { dialog = null },
        )
        TxnDialog.TIME -> TimePickerModal(
            initial = state.time,
            is24Hour = when (timeFormat) {
                TimeFormat.SYSTEM -> DateFormat.is24HourFormat(context)
                TimeFormat.H12 -> false
                TimeFormat.H24 -> true
            },
            onConfirm = { viewModel.setTime(it); dialog = null },
            onDismiss = { dialog = null },
        )
        TxnDialog.NEW_CATEGORY -> TagNameDialog(
            title = stringResource(R.string.cat_add),
            initial = "",
            confirmLabel = stringResource(R.string.action_create),
            onConfirm = { viewModel.createCategory(it); dialog = null },
            onDismiss = { dialog = null },
            fieldLabel = stringResource(R.string.task_category_name),
        )
        TxnDialog.DELETE -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(R.string.txn_delete_title)) },
            text = { Text(stringResource(R.string.txn_delete_body)) },
            confirmButton = {
                TextButton(onClick = { dialog = null; viewModel.delete() }) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
        null -> Unit
    }
}
