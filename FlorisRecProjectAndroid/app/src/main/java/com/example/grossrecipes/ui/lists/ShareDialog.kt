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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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

@Composable
fun ShareDialog(
    list: GroceryList,
    onDismiss: () -> Unit,
    onShare: (String) -> Unit,
    onUnshare: (String) -> Unit,
    onSharedExternally: () -> Unit
) {
    var username by remember { mutableStateOf("") }
    val context = LocalContext.current

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
                    onValueChange = { username = it },
                    placeholder = { Text("Username, e.g. laura") },
                    singleLine = true,
                    shape = PillShape,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (username.isNotBlank()) {
                            onShare(username.trim())
                            username = ""
                        }
                    }),
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (username.isNotBlank()) {
                            onShare(username.trim())
                            username = ""
                        }
                    },
                    shape = PillShape,
                    colors = ButtonDefaults.buttonColors(containerColor = Accent)
                ) {
                    Text("Add")
                }
            }

            if (list.sharedWith.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    list.sharedWith.forEach { person ->
                        Row(
                            modifier = Modifier
                                .clip(PillShape)
                                .background(Accent2Light)
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(person, style = MaterialTheme.typography.labelMedium, color = Accent2Deep)
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Remove $person",
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
                ShareViaIcon(icon = Icons.Default.MailOutline, contentDescription = "Share via Messages") {
                    shareListViaSystemSheet(context, list)
                    onSharedExternally()
                }
                ShareViaIcon(icon = Icons.Default.Email, contentDescription = "Share via Mail") {
                    shareListViaSystemSheet(context, list)
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

private fun shareListViaSystemSheet(context: android.content.Context, list: GroceryList) {
    val itemsSummary = list.items.joinToString("\n") { "- ${it.name}" }
    val shareText = "Check out my grocery list \"${list.name}\":\n$itemsSummary"
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, shareText)
    }
    context.startActivity(Intent.createChooser(intent, "Share list"))
}