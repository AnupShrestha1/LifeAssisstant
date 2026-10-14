package com.anujstha.lifeassistant.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.anujstha.lifeassistant.R
import com.anujstha.lifeassistant.domain.model.RecurrenceFrequency
import com.anujstha.lifeassistant.ui.tasks.labelRes
import com.anujstha.lifeassistant.ui.tasks.unitPluralRes
import java.time.LocalDate

/** Repeat picker shared by the task and reminder editors: frequency, "every N units", end date. */
@Composable
fun RepeatFields(
    frequency: RecurrenceFrequency?,
    interval: String,
    repeatEnd: LocalDate?,
    onFrequency: (RecurrenceFrequency?) -> Unit,
    onInterval: (String) -> Unit,
    onPickEnd: () -> Unit,
    onClearEnd: () -> Unit,
) {
    val frequencies: List<RecurrenceFrequency?> = listOf(null) + RecurrenceFrequency.entries
    DropdownField(
        label = stringResource(R.string.task_field_repeat),
        options = frequencies.map { Choice(it, stringResource(it.labelRes())) },
        selected = frequency,
        onSelect = onFrequency,
    )
    if (frequency == null) return
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.task_field_repeat_every))
        OutlinedTextField(
            value = interval,
            onValueChange = onInterval,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(80.dp),
        )
        Text(pluralStringResource(frequency.unitPluralRes(), interval.toIntOrNull() ?: 1))
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.task_field_repeat_until))
        OutlinedButton(onClick = onPickEnd) {
            Text(repeatEnd?.let { rememberDateFormatter().format(it) } ?: stringResource(R.string.task_repeat_never_ends))
        }
        if (repeatEnd != null) {
            TextButton(onClick = onClearEnd) { Text(stringResource(R.string.clear)) }
        }
    }
}
