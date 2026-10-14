package com.anujstha.lifeassistant.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.anujstha.lifeassistant.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Picks an inclusive date range; "Clear" returns (null, null). A single tapped day gives from == to. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRangeDialog(
    initialFrom: LocalDate?,
    initialTo: LocalDate?,
    onConfirm: (LocalDate?, LocalDate?) -> Unit,
    onDismiss: () -> Unit,
) {
    fun LocalDate.millis() = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    fun Long.date() = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initialFrom?.millis(),
        initialSelectedEndDateMillis = initialTo?.millis(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val from = state.selectedStartDateMillis?.date()
                onConfirm(from, state.selectedEndDateMillis?.date() ?: from)
            }) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = {
            TextButton(onClick = { onConfirm(null, null) }) { Text(stringResource(R.string.clear)) }
        },
    ) {
        Box(Modifier.weight(1f)) { DateRangePicker(state = state, showModeToggle = false) }
    }
}
