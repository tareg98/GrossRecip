package com.example.grossrecipes.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.grossrecipes.data.AuthOutcome
import com.example.grossrecipes.data.SessionManager
import com.example.grossrecipes.data.createAccountApi
import com.example.grossrecipes.data.dto.Credentials
import com.example.grossrecipes.data.parseAuthResponse
import com.example.grossrecipes.ui.theme.Accent
import com.example.grossrecipes.ui.theme.Accent2
import com.example.grossrecipes.ui.theme.FaintText
import com.example.grossrecipes.ui.theme.MutedText
import com.example.grossrecipes.ui.theme.PillShape
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(onLoginSuccess: () -> Unit, onNavigateToSignUp: () -> Unit) {
    var serverUrl by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isLoggingIn by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val sessionManager = remember { SessionManager(context) }
    val coroutineScope = rememberCoroutineScope()
    val canLogIn = serverUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Brand block
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(Accent2),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "GC",
                style = MaterialTheme.typography.titleLarge
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "GrossRecipe",
            style = MaterialTheme.typography.titleLarge
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Sign in to your self-hosted server",
            style = MaterialTheme.typography.bodyMedium,
            color = MutedText
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Form fields
        OutlinedTextField(
            value = serverUrl,
            onValueChange = { serverUrl = it; errorMessage = null },
            placeholder = { Text("Server URL") },
            singleLine = true,
            shape = PillShape,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = username,
            onValueChange = { username = it; errorMessage = null },
            placeholder = { Text("Username") },
            singleLine = true,
            shape = PillShape,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it; errorMessage = null },
            placeholder = { Text("Password") },
            singleLine = true,
            shape = PillShape,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (passwordVisible) "Hide password" else "Show password"
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = errorMessage.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Log In button
        Button(
            onClick = {
                coroutineScope.launch {
                    isLoggingIn = true
                    errorMessage = null
                    try {
                        val api = createAccountApi(serverUrl)
                        val response = api.login(Credentials(username, password))
                        when (val outcome = parseAuthResponse(response)) {
                            is AuthOutcome.Success -> {
                                sessionManager.saveLogin(
                                    serverUrl = serverUrl,
                                    username = username,
                                    accessToken = outcome.accessToken,
                                    refreshToken = outcome.refreshToken
                                )
                                onLoginSuccess()
                            }
                            is AuthOutcome.Failure -> errorMessage = outcome.message
                        }
                    } catch (e: Exception) {
                        // Couldn't reach the server at all (bad URL, offline, wrong port, etc.)
                        errorMessage = "Couldn't reach that server. Check the URL and try again."
                    } finally {
                        isLoggingIn = false
                    }
                }
            },
            enabled = canLogIn && !isLoggingIn,
            shape = PillShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = Accent,
                disabledContainerColor = Accent.copy(alpha = 0.45f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
        ) {
            Text("Log In")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Don't have an account? Sign up",
            style = MaterialTheme.typography.bodyMedium,
            color = Accent,
            modifier = Modifier.clickable { onNavigateToSignUp() }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Footer
        Text(
            text = "Your grocery data stays on your own server.",
            style = MaterialTheme.typography.bodySmall,
            color = FaintText,
            textAlign = TextAlign.Center
        )
    }
}