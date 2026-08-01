package com.example.grossrecipes.ui.lists

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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.grossrecipes.ui.theme.Accent
import com.example.grossrecipes.ui.theme.DialogShape
import com.example.grossrecipes.ui.theme.MutedText
import com.example.grossrecipes.ui.theme.PillShape
import com.example.grossrecipes.ui.theme.PrimaryText
import com.example.grossrecipes.ui.theme.Surface

@Composable
fun NewListDialog(
    onDismiss: () -> Unit,
    onLookupUsername: suspend (String) -> Result<String?>,
    onCreate: (name: String, color: Color?, sharedWithUserId: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf<Color?>(null) }
    var shareChecked by remember { mutableStateOf(false) }
    var shareUsername by remember { mutableStateOf("") }
    var isLookingUp by remember { mutableStateOf(false) }
    var lookupError by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    // Same reasoning as ShareDialog.submitUsername - ListCreated's
    // sharedWith needs a real UUID (see ListsRepository.createList), not
    // whatever text was typed, so this resolves it against the backend
    // before Create is allowed to actually fire.
    val submitCreate = {
        val typed = shareUsername.trim()
        if (!shareChecked || typed.isBlank()) {
            onCreate(name, selectedColor, "")
        } else if (!isLookingUp) {
            isLookingUp = true
            lookupError = null
            coroutineScope.launch {
                onLookupUsername(typed).fold(
                    onSuccess = { userId ->
                        isLookingUp = false
                        if (userId == null) {
                            lookupError = "No user found with that username"
                        } else {
                            onCreate(name, selectedColor, userId)
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
            Text("New List", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(14.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text("e.g. Weekend BBQ") },
                label = { Text("List name") },
                singleLine = true,
                shape = PillShape,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listColorPalette.forEach { color ->
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(color)
                            .then(
                                if (selectedColor == color) Modifier.border(2.dp, PrimaryText, CircleShape)
                                else Modifier
                            )
                            .clickable { selectedColor = color }
                    )
                }
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .drawBehind {
                            drawCircle(
                                color = MutedText,
                                style = Stroke(
                                    width = 2.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))
                                )
                            )
                        }
                        .clickable { selectedColor = null }
                )
            }

            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = shareChecked, onCheckedChange = { shareChecked = it })
                Text("Share this list (e.g. with Laura)", style = MaterialTheme.typography.bodyMedium)
            }

            if (shareChecked) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = shareUsername,
                    onValueChange = {
                        shareUsername = it
                        lookupError = null
                    },
                    placeholder = { Text("Share with (username)") },
                    singleLine = true,
                    enabled = !isLookingUp,
                    isError = lookupError != null,
                    shape = PillShape,
                    modifier = Modifier.fillMaxWidth()
                )
                if (lookupError != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = lookupError!!,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                OutlinedButton(onClick = onDismiss, shape = PillShape) {
                    Text("Cancel")
                }
                Spacer(Modifier.width(10.dp))
                Button(
                    onClick = { submitCreate() },
                    enabled = name.isNotBlank() && !isLookingUp,
                    shape = PillShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Accent,
                        disabledContainerColor = Accent.copy(alpha = 0.45f)
                    )
                ) {
                    if (isLookingUp) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Surface,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Create")
                    }
                }
            }
        }
    }
}