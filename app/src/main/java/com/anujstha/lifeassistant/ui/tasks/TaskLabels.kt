package com.anujstha.lifeassistant.ui.tasks

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.anujstha.lifeassistant.R
import com.anujstha.lifeassistant.domain.model.Priority
import com.anujstha.lifeassistant.domain.model.RecurrenceFrequency
import com.anujstha.lifeassistant.domain.model.TaskStatus
import com.anujstha.lifeassistant.domain.usecase.TaskSort
import com.anujstha.lifeassistant.domain.usecase.TaskView

@StringRes
fun TaskStatus.labelRes(): Int = when (this) {
    TaskStatus.PENDING -> R.string.status_pending
    TaskStatus.IN_PROGRESS -> R.string.status_in_progress
    TaskStatus.COMPLETED -> R.string.status_completed
    TaskStatus.CANCELLED -> R.string.status_cancelled
}

@StringRes
fun Priority.labelRes(): Int = when (this) {
    Priority.LOW -> R.string.priority_low
    Priority.MEDIUM -> R.string.priority_medium
    Priority.HIGH -> R.string.priority_high
    Priority.URGENT -> R.string.priority_urgent
}

/** Only high and urgent get a color; low/medium stay neutral to avoid visual noise. */
@Composable
fun Priority.color(): Color = when (this) {
    Priority.URGENT -> MaterialTheme.colorScheme.error
    Priority.HIGH -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

@StringRes
fun RecurrenceFrequency?.labelRes(): Int = when (this) {
    null -> R.string.repeat_none
    RecurrenceFrequency.DAILY -> R.string.repeat_daily
    RecurrenceFrequency.WEEKLY -> R.string.repeat_weekly
    RecurrenceFrequency.MONTHLY -> R.string.repeat_monthly
}

@PluralsRes
fun RecurrenceFrequency.unitPluralRes(): Int = when (this) {
    RecurrenceFrequency.DAILY -> R.plurals.repeat_unit_days
    RecurrenceFrequency.WEEKLY -> R.plurals.repeat_unit_weeks
    RecurrenceFrequency.MONTHLY -> R.plurals.repeat_unit_months
}

@StringRes
fun TaskView.labelRes(): Int = when (this) {
    TaskView.TODAY -> R.string.tasks_view_today
    TaskView.UPCOMING -> R.string.tasks_view_upcoming
    TaskView.OVERDUE -> R.string.tasks_view_overdue
    TaskView.ALL -> R.string.tasks_view_all
    TaskView.COMPLETED -> R.string.tasks_view_completed
}

@StringRes
fun TaskView.emptyRes(): Int = when (this) {
    TaskView.TODAY -> R.string.tasks_empty_today
    TaskView.UPCOMING -> R.string.tasks_empty_upcoming
    TaskView.OVERDUE -> R.string.tasks_empty_overdue
    TaskView.ALL -> R.string.tasks_empty_all
    TaskView.COMPLETED -> R.string.tasks_empty_completed
}

@StringRes
fun TaskSort.labelRes(): Int = when (this) {
    TaskSort.DUE_DATE -> R.string.tasks_sort_due
    TaskSort.PRIORITY -> R.string.tasks_sort_priority
    TaskSort.CREATED -> R.string.tasks_sort_created
}
