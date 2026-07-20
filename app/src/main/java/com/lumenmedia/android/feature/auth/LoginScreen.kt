package com.lumenmedia.android.feature.auth

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumenmedia.android.R
import com.lumenmedia.android.core.designsystem.FpBrandMark
import com.lumenmedia.android.core.designsystem.FpButton
import com.lumenmedia.android.core.designsystem.FpButtonVariant
import com.lumenmedia.android.core.designsystem.FpColors
import com.lumenmedia.android.core.designsystem.FpDimens
import com.lumenmedia.android.core.designsystem.FpTextField
import com.lumenmedia.android.core.designsystem.FullPageLoading
import com.lumenmedia.android.core.designsystem.isTvDevice

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
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.3).sp,
                )
            }
            Text(
                if (state.needsSetup == true) {
                    stringResource(R.string.auth_subtitle_setup)
                } else {
                    stringResource(R.string.auth_subtitle_login)
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(FpDimens.space4))
            FpTextField(state.baseUrl, viewModel::onBaseUrlChange, stringResource(R.string.auth_server_url))
            if (state.needsSetup == true) {
                FpTextField(state.serverName, viewModel::onServerNameChange, stringResource(R.string.auth_server_name))
            }
            FpTextField(state.username, viewModel::onUsernameChange, stringResource(R.string.auth_username))
            FpTextField(state.password, viewModel::onPasswordChange, stringResource(R.string.auth_password), isPassword = true)
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
                    text = stringResource(R.string.auth_remember),
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
                    state.submitting -> stringResource(R.string.auth_please_wait)
                    state.needsSetup == true -> stringResource(R.string.auth_create_admin)
                    else -> stringResource(R.string.auth_sign_in)
                },
                modifier = Modifier.fillMaxWidth(),
            )
            FpButton(
                onClick = viewModel::refreshServerInfo,
                label = stringResource(R.string.auth_check_server),
                variant = FpButtonVariant.Secondary,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
