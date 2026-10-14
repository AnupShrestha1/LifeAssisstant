package com.anujstha.lifeassistant.ui.journal

import androidx.annotation.StringRes
import com.anujstha.lifeassistant.R
import com.anujstha.lifeassistant.data.entity.JournalEntryEntity
import com.anujstha.lifeassistant.domain.model.Mood

@StringRes
fun Mood.labelRes(): Int = when (this) {
    Mood.HAPPY -> R.string.mood_happy
    Mood.NEUTRAL -> R.string.mood_neutral
    Mood.SAD -> R.string.mood_sad
    Mood.STRESSED -> R.string.mood_stressed
    Mood.EXCITED -> R.string.mood_excited
}

/** Heading for an entry: its title, else the first non-blank line of the body, else null ("Untitled"). */
fun JournalEntryEntity.headline(maxLength: Int = 80): String? =
    title.takeIf { it.isNotBlank() }
        ?: body.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }?.take(maxLength)

/** Body preview with line breaks collapsed, skipping the line already used as the headline. */
fun JournalEntryEntity.preview(maxLength: Int = 160): String {
    val lines = body.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
    val rest = if (title.isBlank()) lines.drop(1) else lines
    return rest.joinToString(" ").take(maxLength)
}
