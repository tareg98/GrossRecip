package com.example.grossrecipes.ui.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.grossrecipes.ui.theme.Accent
import com.example.grossrecipes.ui.theme.Accent2
import com.example.grossrecipes.ui.theme.CardShape
import com.example.grossrecipes.ui.theme.DividerLight
import com.example.grossrecipes.ui.theme.FaintText
import com.example.grossrecipes.ui.theme.MutedText
import com.example.grossrecipes.ui.theme.PillShape
import com.example.grossrecipes.ui.theme.Surface

@Composable
fun SettingsScreen(
    onLoggedOut: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val session by viewModel.session.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val lastSyncError by viewModel.lastSyncError.collectAsState()
    val pendingChangeCount by viewModel.pendingChangeCount.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(CardShape)
                .background(Surface)
        ) {
            SettingsRow(
                label = "SERVER",
                value = session.serverUrl,
                onClick = {
                    clipboardManager.setText(AnnotatedString(session.serverUrl))
                    Toast.makeText(context, "Server URL copied", Toast.LENGTH_SHORT).show()
                }
            )
            HorizontalDivider(color = DividerLight, thickness = 1.dp)
            SettingsRow(label = "SIGNED IN AS", value = session.username)
        }

        Spacer(Modifier.height(16.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(CardShape)
                .background(Surface)
                .padding(16.dp)
        ) {
            // Neither connectivity, an empty outbox, nor "not mid-sync" alone
            // means "synced": the server can be reachable and still reject
            // every request (an expired token, a server error, etc), and
            // that can happen with nothing local queued to push (a PULL can
            // fail on its own) - lastSyncError is what catches that case,
            // since pendingChangeCount would stay 0 throughout.
            val fullySynced = isOnline && !isSyncing && lastSyncError == null && pendingChangeCount == 0

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (fullySynced) Accent2 else Accent)
                )
                Spacer(Modifier.width(8.dp))
                Text("Sync status", style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = when {
                    !isOnline -> "Can't reach your server right now. Changes are saved on this device and will sync automatically once you're back online."
                    isSyncing -> "Syncing…"
                    lastSyncError != null -> "Last sync failed: $lastSyncError"
                    pendingChangeCount > 0 -> "$pendingChangeCount change${if (pendingChangeCount == 1) "" else "s"} haven't made it to the server yet. Your connection looks fine, so the server may be rejecting the request - try again shortly."
                    else -> "All changes are synced with your server."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MutedText
            )
        }

        Spacer(Modifier.height(24.dp))

        OutlinedButton(
            onClick = { viewModel.logout(onLoggedOut) },
            shape = PillShape,
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
        ) {
            Text("Log Out")
        }

        Spacer(Modifier.weight(1f))

        Text(
            text = "Grocery Tracker v0.1",
            style = MaterialTheme.typography.bodySmall,
            color = FaintText,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        )
    }
}

@Composable
private fun SettingsRow(label: String, value: String, onClick: (() -> Unit)? = null) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(16.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MutedText)
        Spacer(Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
