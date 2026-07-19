package com.freeplex.android.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freeplex.android.core.designsystem.FpBrandMark
import com.freeplex.android.core.designsystem.FpButton
import com.freeplex.android.core.designsystem.FpButtonVariant
import com.freeplex.android.core.designsystem.FpColors
import com.freeplex.android.core.designsystem.FpDimens
import com.freeplex.android.core.designsystem.FpTextField
import com.freeplex.android.core.designsystem.FullPageLoading
import com.freeplex.android.core.designsystem.isTvDevice

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
            .background(
                Brush.radialGradient(
                    colors = listOf(FpColors.Accent.copy(alpha = 0.14f), FpColors.Bg),
                    radius = 900f,
                ),
            )
            .padding(if (tv) FpDimens.space32 else FpDimens.space20),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = if (tv) 520.dp else 420.dp)
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f), RoundedCornerShape(FpDimens.radiusXl))
                .background(
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    RoundedCornerShape(FpDimens.radiusXl),
                )
                .padding(if (tv) FpDimens.space28 else FpDimens.space24)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(FpDimens.space12),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(FpDimens.space12),
            ) {
                FpBrandMark(size = if (tv) 36.dp else 32.dp)
                Text(
                    "FreePlex",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.3).sp,
                )
            }
            Text(
                if (state.needsSetup == true) "Create admin account" else "Sign in to your server",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(FpDimens.space4))
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
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.primary,
                        uncheckedColor = MaterialTheme.colorScheme.outline,
                    ),
                )
                Text(
                    text = "Remember username and password",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = FpDimens.space4),
                )
            }
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            FpButton(
                onClick = viewModel::submit,
                enabled = !state.submitting,
                label = when {
                    state.submitting -> "Please wait…"
                    state.needsSetup == true -> "Create & sign in"
                    else -> "Sign in"
                },
                modifier = Modifier.fillMaxWidth(),
            )
            FpButton(
                onClick = viewModel::refreshServerInfo,
                label = "Check server",
                variant = FpButtonVariant.Secondary,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
