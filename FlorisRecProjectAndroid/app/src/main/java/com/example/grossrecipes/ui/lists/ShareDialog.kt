package com.example.grossrecipes.ui.lists

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.grossrecipes.ui.theme.Accent
import com.example.grossrecipes.ui.theme.Accent2Deep
import com.example.grossrecipes.ui.theme.Accent2Light
import com.example.grossrecipes.ui.theme.DialogShape
import com.example.grossrecipes.ui.theme.Divider
import com.example.grossrecipes.ui.theme.MutedText
import com.example.grossrecipes.ui.theme.PillShape
import com.example.grossrecipes.ui.theme.Surface
import kotlinx.coroutines.launch

@Composable
fun ShareDialog(
    list: GroceryList,
    onDismiss: () -> Unit,
    onLookupUsername: suspend (String) -> Result<String?>,
    onLookupUserId: suspend (String) -> Result<String?>,
    onShare: (username: String, userId: String) -> Unit,
    onUnshare: (String) -> Unit,
    onSharedExternally: () -> Unit
) {
    var username by remember { mutableStateOf("") }
    var isLookingUp by remember { mutableStateOf(false) }
    var lookupError by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    // list.sharedWith now holds each person's UUID (see
    // ListsRepository.shareList), not their username - resolve each one to
    // a display name for the pills below. Keyed by UUID so this doesn't
    // re-fetch names it already has whenever sharedWith changes length.
    // Falls back to showing the raw UUID for anyone still loading or whose
    // name failed to resolve, rather than hiding them.
    val resolvedNames = remember { mutableStateMapOf<String, String>() }
    LaunchedEffect(list.sharedWith) {
        list.sharedWith.filter { it !in resolvedNames }.forEach { userId ->
            onLookupUserId(userId).onSuccess { name ->
                if (name != null) resolvedNames[userId] = name
            }
        }
    }

    // Resolves the typed username against the backend before actually
    // sharing - see AccountApi.lookupUsername. Only calls onShare once a
    // real user (with a real UUID) is confirmed to exist, instead of
    // trusting whatever text was typed and finding out it was wrong only
    // once the share event fails to mean anything on the other end.
    val submitUsername = {
        val typed = username.trim()
        if (typed.isNotBlank() && !isLookingUp) {
            isLookingUp = true
            lookupError = null
            coroutineScope.launch {
                onLookupUsername(typed).fold(
                    onSuccess = { userId ->
                        isLookingUp = false
                        if (userId == null) {
                            lookupError = "No user found with that username"
                        } else {
                            onShare(typed, userId)
                            username = ""
                        }
                    },
                    onFailure = { e ->
                        isLookingUp = false
                        lookupError = e.message ?: "Lookup failed"
                    }
                )
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(DialogShape)
                .background(Surface)
                .padding(24.dp)
        ) {
            Text("Share \"${list.name}\"", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(14.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = username,
                    onValueChange = {
                        username = it
                        lookupError = null
                    },
                    placeholder = { Text("Username, e.g. laura") },
                    singleLine = true,
                    enabled = !isLookingUp,
                    isError = lookupError != null,
                    shape = PillShape,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submitUsername() }),
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { submitUsername() },
                    enabled = !isLookingUp,
                    shape = PillShape,
                    colors = ButtonDefaults.buttonColors(containerColor = Accent)
                ) {
                    if (isLookingUp) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Surface,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Add")
                    }
                }
            }

            if (lookupError != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = lookupError!!,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (list.sharedWith.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    list.sharedWith.forEach { person ->
                        // person is the raw UUID (what's actually stored and
                        // what onUnshare needs) - displayName is just what's
                        // shown, resolved via lookup-by-id above.
                        val displayName = resolvedNames[person] ?: person
                        Row(
                            modifier = Modifier
                                .clip(PillShape)
                                .background(Accent2Light)
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(displayName, style = MaterialTheme.typography.labelMedium, color = Accent2Deep)
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Remove $displayName",
                                tint = Accent2Deep,
                                modifier = Modifier
                                    .size(14.dp)
                                    .clickable { onUnshare(person) }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = Divider, thickness = 1.dp)
            Spacer(Modifier.height(16.dp))

            Text("Or share via", style = MaterialTheme.typography.bodyMedium, color = MutedText)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                // Each of these used to call the exact same generic system
                // sheet, making them three identical buttons - now Messages
                // and Mail go straight to their own app (no picker at all),
                // and only "More" falls back to letting the user pick from
                // everything installed that can handle shared text.
                ShareViaIcon(icon = Icons.Default.MailOutline, contentDescription = "Share via Messages") {
                    shareListViaSms(context, list)
                    onSharedExternally()
                }
                ShareViaIcon(icon = Icons.Default.Email, contentDescription = "Share via Mail") {
                    shareListViaEmail(context, list)
                    onSharedExternally()
                }
                ShareViaIcon(icon = Icons.Default.MoreHoriz, contentDescription = "More sharing options") {
                    shareListViaSystemSheet(context, list)
                    onSharedExternally()
                }
            }

            Spacer(Modifier.height(20.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(
                    onClick = onDismiss,
                    shape = PillShape,
                    colors = ButtonDefaults.buttonColors(containerColor = Accent)
                ) {
                    Text("Done")
                }
            }
        }
    }
}

@Composable
private fun ShareViaIcon(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Surface)
            .border(1.dp, Divider, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = contentDescription)
    }
}

private fun listShareText(list: GroceryList): String {
    val itemsSummary = list.items.joinToString("\n") { "- ${it.name}" }
    return "Check out my grocery list \"${list.name}\":\n$itemsSummary"
}

/** Generic share sheet - shows every app installed that can handle shared text, not just SMS/Mail. */
private fun shareListViaSystemSheet(context: android.content.Context, list: GroceryList) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, listShareText(list))
    }
    context.startActivity(Intent.createChooser(intent, "Share list"))
}

/** Goes straight to the default messaging app - no picker, unlike the generic sheet above. */
private fun shareListViaSms(context: android.content.Context, list: GroceryList) {
    val intent = Intent(Intent.ACTION_SENDTO).apply {
        data = android.net.Uri.parse("smsto:")
        putExtra("sms_body", listShareText(list))
    }
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        // No messaging app registered for smsto: - fall back rather than crash.
        shareListViaSystemSheet(context, list)
    }
}

/** Goes straight to an email app - no picker, unlike the generic sheet above. */
private fun shareListViaEmail(context: android.content.Context, list: GroceryList) {
    val intent = Intent(Intent.ACTION_SENDTO).apply {
        data = android.net.Uri.parse("mailto:")
        putExtra(Intent.EXTRA_SUBJECT, "Grocery list: ${list.name}")
        putExtra(Intent.EXTRA_TEXT, listShareText(list))
    }
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        // No email app registered for mailto: - fall back rather than crash.
        shareListViaSystemSheet(context, list)
    }
}