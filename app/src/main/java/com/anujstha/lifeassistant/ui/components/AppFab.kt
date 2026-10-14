package com.anujstha.lifeassistant.ui.components

import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/**
 * Extended FAB with its label exposed to accessibility services. Material's
 * ExtendedFloatingActionButton clears the label's semantics, which leaves screen readers with an
 * unlabeled button, so the label is set as the content description explicitly.
 */
@Composable
fun AppFab(label: String, icon: ImageVector, onClick: () -> Unit) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        icon = { Icon(icon, contentDescription = null) },
        text = { Text(label) },
        modifier = Modifier.semantics { contentDescription = label },
    )
}
