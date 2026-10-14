package com.anujstha.lifeassistant.ui.reminders

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.anujstha.lifeassistant.LifeAssistantApp
import com.anujstha.lifeassistant.R

/** Notification permission state, re-checked whenever the screen resumes (e.g. back from Settings). */
class NotificationPermissionState(
    val granted: Boolean,
    /** Ask via the system dialog, or open app notification settings if Android won't show the dialog any more. */
    val request: () -> Unit,
)

@Composable
fun rememberNotificationPermissionState(onResult: (Boolean) -> Unit = {}): NotificationPermissionState {
    val context = LocalContext.current
    val notifier = remember { (context.applicationContext as LifeAssistantApp).container.reminderNotifier }
    var granted by remember { mutableStateOf(notifier.canPostNotifications()) }
    var askedBefore by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { granted = notifier.canPostNotifications() }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        askedBefore = true
        granted = notifier.canPostNotifications()
        onResult(ok)
    }
    val request: () -> Unit = {
        val activity = context.findActivity()
        val canShowDialog = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && activity != null &&
            (!askedBefore || ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS))
        if (canShowDialog) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            // Permission permanently denied, or notifications blocked in system settings.
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
            )
            onResult(false)
        }
    }
    return NotificationPermissionState(granted, request)
}

/** Explains what happens without notifications and offers to enable them. */
@Composable
fun NotificationPermissionBanner(state: NotificationPermissionState) {
    if (state.granted) return
    InfoBanner(
        title = stringResource(R.string.reminders_notif_off_title),
        body = stringResource(R.string.reminders_notif_off_body),
        action = stringResource(R.string.reminders_notif_allow),
        onAction = state.request,
    )
}

/** Shown when Android 12+ hasn't granted exact alarms; reminders still work but may be late. */
@Composable
fun ExactAlarmBanner() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val context = LocalContext.current
    val repository = remember { (context.applicationContext as LifeAssistantApp).container.reminderRepository }
    var canExact by remember { mutableStateOf(repository.canScheduleExact()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { canExact = repository.canScheduleExact() }
    if (canExact) return
    InfoBanner(
        title = stringResource(R.string.reminders_exact_off_title),
        body = stringResource(R.string.reminders_exact_off_body),
        action = stringResource(R.string.reminders_exact_allow),
        onAction = {
            context.startActivity(
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")),
            )
        },
    )
}

@Composable
private fun InfoBanner(title: String, body: String, action: String, onAction: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp, end = 8.dp))
            TextButton(onClick = onAction) { Text(action) }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
