package com.freeplex.android.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freeplex.android.core.designsystem.FpTextField
import com.freeplex.android.core.designsystem.FullPageLoading
import com.freeplex.android.core.designsystem.isTvDevice
import com.freeplex.android.core.designsystem.tvFocusable

@Composable
fun LoginScreen(
    onAuthenticated: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val tv = isTvDevice()

    LaunchedEffect(state.status) {
        if (state.status == AuthStatus.Authenticated) onAuthenticated()
    }

    if (state.status == AuthStatus.Restoring) {
        FullPageLoading()
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(if (tv) 32.dp else 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = if (tv) 520.dp else 480.dp)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                .padding(if (tv) 28.dp else 24.dp),
            verticalArrangement = Arrangement.spacedBy(if (tv) 12.dp else 12.dp),
        ) {
            Text(
                "FreePlex",
                style = if (tv) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                if (state.needsSetup == true) "Create admin account" else "Sign in to your server",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FpTextField(state.baseUrl, viewModel::onBaseUrlChange, "Server URL")
            if (state.needsSetup == true) {
                FpTextField(state.serverName, viewModel::onServerNameChange, "Server name")
            }
            FpTextField(state.username, viewModel::onUsernameChange, "Username")
            FpTextField(state.password, viewModel::onPasswordChange, "Password", isPassword = true)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = state.rememberCredentials,
                        role = Role.Checkbox,
                        onValueChange = viewModel::onRememberCredentialsChange,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = state.rememberCredentials,
                    onCheckedChange = null,
                )
                Text(
                    text = "Remember username and password",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
            }
            Button(
                onClick = viewModel::submit,
                enabled = !state.submitting,
                modifier = Modifier
                    .fillMaxWidth()
                    .tvFocusable(onClick = viewModel::submit, scaleFocused = 1.04f),
            ) {
                Text(
                    when {
                        state.submitting -> "Please wait…"
                        state.needsSetup == true -> "Create & sign in"
                        else -> "Sign in"
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Button(
                onClick = viewModel::refreshServerInfo,
                modifier = Modifier
                    .fillMaxWidth()
                    .tvFocusable(onClick = viewModel::refreshServerInfo, scaleFocused = 1.04f),
            ) {
                Text("Check server", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
