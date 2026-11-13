package com.cahyo.batur.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.cahyo.batur.data.prefs.SettingsStore
import com.cahyo.batur.data.remote.HealthStatus
import com.cahyo.batur.ui.theme.StatusColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Hasil tes koneksi ke server. */
sealed interface ConnectionState {
    data object Idle : ConnectionState
    data object Checking : ConnectionState
    data object Connected : ConnectionState
    data class Failed(val message: String) : ConnectionState
}

fun Result<HealthStatus>.toConnectionState(): ConnectionState = fold(
    onSuccess = { ConnectionState.Connected },
    onFailure = { ConnectionState.Failed(it.message ?: "Gagal terhubung") },
)

/**
 * Dialog alamat server Batur API. [onTest] mengecek alamat yang sedang diketik (belum disimpan).
 */
@Composable
fun ServerUrlDialog(
    current: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onTest: suspend (String) -> Result<HealthStatus>,
) {
    var text by rememberSaveable { mutableStateOf(current) }
    var test by remember { mutableStateOf<ConnectionState>(ConnectionState.Idle) }
    var testJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Alamat server") },
        text = {
            Column {
                Text(
                    "Contoh: https://batur.ezkielyna.store\nBawaan aplikasi: ${SettingsStore.DEFAULT_BASE_URL}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        test = ConnectionState.Idle
                    },
                    singleLine = true,
                    placeholder = { Text("https://…") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = {
                            testJob?.cancel()
                            test = ConnectionState.Checking
                            testJob = scope.launch { test = onTest(text).toConnectionState() }
                        },
                        enabled = text.isNotBlank() && test != ConnectionState.Checking,
                    ) { Text("Tes koneksi") }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = {
                        text = SettingsStore.DEFAULT_BASE_URL
                        test = ConnectionState.Idle
                    }) { Text("Pakai bawaan") }
                }
                ConnectionLine(test)
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(text) }, enabled = text.isNotBlank()) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}

/** Satu baris status koneksi; kosong saat [ConnectionState.Idle]. */
@Composable
fun ConnectionLine(state: ConnectionState, modifier: Modifier = Modifier) {
    when (state) {
        ConnectionState.Idle -> Unit
        ConnectionState.Checking -> Row(modifier, verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            Text("Mengecek koneksi…", style = MaterialTheme.typography.bodySmall)
        }
        ConnectionState.Connected -> Row(modifier, verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = StatusColors.success, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Terhubung ke server dan database", style = MaterialTheme.typography.bodySmall)
        }
        is ConnectionState.Failed -> Row(modifier, verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(state.message, style = MaterialTheme.typography.bodySmall)
        }
    }
}
