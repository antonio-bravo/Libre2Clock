package com.tonio.libre2clock.ui.login

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tonio.libre2clock.R
import com.tonio.libre2clock.data.model.DexcomServerRegion
import com.tonio.libre2clock.data.model.GlucoseSourceProvider
import com.tonio.libre2clock.service.GlucoseForegroundService
import com.tonio.libre2clock.ui.icons.Visibility
import com.tonio.libre2clock.ui.icons.VisibilityOff

@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    onLoginSuccess: () -> Unit
) {
    val context = LocalContext.current
    val selectedProvider by viewModel.selectedProvider.collectAsStateWithLifecycle()
    val dexcomRegion by viewModel.dexcomRegion.collectAsStateWithLifecycle()
    val email by viewModel.email.collectAsStateWithLifecycle()
    val password by viewModel.password.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val loginResult by viewModel.loginResult.collectAsStateWithLifecycle()
    var passwordVisible by remember { mutableStateOf(false) }

    LaunchedEffect(loginResult) {
        if (loginResult?.isSuccess == true) {
            context.startForegroundService(Intent(context, GlucoseForegroundService::class.java))
            onLoginSuccess()
        }
    }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.login_welcome),
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(24.dp))

            // Selector de Proveedor
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth()
            ) {
                SegmentedButton(
                    selected = selectedProvider == GlucoseSourceProvider.LIBRE_LINK_UP,
                    onClick = { viewModel.onProviderSelected(GlucoseSourceProvider.LIBRE_LINK_UP) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) {
                    Text("LibreLinkUp")
                }
                SegmentedButton(
                    selected = selectedProvider == GlucoseSourceProvider.DEXCOM_SHARE,
                    onClick = { viewModel.onProviderSelected(GlucoseSourceProvider.DEXCOM_SHARE) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) {
                    Text("Dexcom Share")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Si es Dexcom Share, mostramos el selector de región (predeterminado OUS)
            if (selectedProvider == GlucoseSourceProvider.DEXCOM_SHARE) {
                Text(
                    text = "Región de Servidor Dexcom",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = dexcomRegion == DexcomServerRegion.OUS,
                        onClick = { viewModel.onDexcomRegionSelected(DexcomServerRegion.OUS) },
                        label = { Text("Europa e Internacional") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = dexcomRegion == DexcomServerRegion.US,
                        onClick = { viewModel.onDexcomRegionSelected(DexcomServerRegion.US) },
                        label = { Text("EE.UU.") },
                        modifier = Modifier.weight(0.6f)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            OutlinedTextField(
                value = email,
                onValueChange = viewModel::onEmailChanged,
                label = {
                    Text(
                        if (selectedProvider == GlucoseSourceProvider.DEXCOM_SHARE)
                            "Usuario / Email Dexcom"
                        else
                            stringResource(R.string.login_email_label)
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
            )
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = password,
                onValueChange = viewModel::onPasswordChanged,
                label = { Text(stringResource(R.string.login_password_label)) },
                visualTransformation = if (passwordVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) {
                                Icons.Filled.VisibilityOff
                            } else {
                                Icons.Filled.Visibility
                            },
                            contentDescription = if (passwordVisible) {
                                stringResource(R.string.login_hide_password)
                            } else {
                                stringResource(R.string.login_show_password)
                            }
                        )
                    }
                }
            )
            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = viewModel::login,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading && email.isNotBlank() && password.isNotBlank()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(stringResource(R.string.login_sign_in_button))
                }
            }

            if (loginResult?.isFailure == true) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = loginResult?.exceptionOrNull()?.message ?: stringResource(R.string.login_error_invalid),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
            
            TextButton(
                onClick = viewModel::startDemoMode,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.login_try_demo))
            }
        }
    }
}
