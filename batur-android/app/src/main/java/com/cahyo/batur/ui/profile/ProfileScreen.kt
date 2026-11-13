package com.cahyo.batur.ui.profile

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cahyo.batur.BuildConfig
import com.cahyo.batur.R
import com.cahyo.batur.data.remote.TaskStats
import com.cahyo.batur.ui.AppViewModelProvider
import com.cahyo.batur.ui.common.BaturScaffold
import com.cahyo.batur.ui.common.SectionTitle
import com.cahyo.batur.ui.common.UiState
import com.cahyo.batur.ui.common.formatLatency
import com.cahyo.batur.ui.theme.StatusColors

private const val GITHUB_URL = "https://github.com/cahyohackids/Batur"

@Composable
fun ProfileScreen(viewModel: ProfileViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val baseUrl by viewModel.baseUrl.collectAsStateWithLifecycle()
    val deviceId by viewModel.deviceId.collectAsStateWithLifecycle()
    val connection by viewModel.connection.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current

    LaunchedEffect(Unit) {
        viewModel.refresh()
        viewModel.checkConnection()
    }

    BaturScaffold(title = "Profil") { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(R.drawable.ic_launcher_foreground),
                        contentDescription = null,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF00696B)),
                    )
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text("Batur", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(
                            "Asisten AI On-Device untuk Eksekusi Perintah di Ponsel",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text("Versi ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Batur (bahasa Jawa: teman, pembantu) menerima perintah sehari-hari, memilih kemampuan yang cocok, " +
                    "meminta konfirmasi, lalu menjalankannya di ponsel, misalnya memasang alarm atau membuat jadwal. " +
                    "Data skill dan riwayat dikelola lewat Batur API.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SectionTitle("Pembuat", Modifier.padding(top = 16.dp))
            InfoCard(
                listOf(
                    "Nama" to "Ahmad Putra Cahyo",
                    "NIM" to "21120124140143",
                    "Program studi" to "Teknik Komputer, Universitas Diponegoro",
                    "Praktikum" to "PPB 2026 · Kelompok 27 · Shift 4",
                ),
            )

            SectionTitle("Statistik penggunaan", Modifier.padding(top = 16.dp))
            when (val s = stats) {
                is UiState.Success -> StatsGrid(s.data)
                is UiState.Error -> Text(s.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                else -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            }

            SectionTitle("Server Batur API", Modifier.padding(top = 16.dp))
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Alamat", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(baseUrl, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
                        }
                        IconButton(onClick = { editing = true }) {
                            Icon(Icons.Outlined.Edit, contentDescription = "Ubah alamat server")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    ConnectionRow(connection, onRetry = viewModel::checkConnection)
                    Spacer(Modifier.height(8.dp))
                    Text("ID perangkat", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(deviceId, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                }
            }

            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = { uriHandler.openUri(GITHUB_URL) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Kode sumber di GitHub")
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (editing) {
        BaseUrlDialog(
            current = baseUrl,
            onDismiss = { editing = false },
            onSave = {
                editing = false
                viewModel.saveBaseUrl(it)
            },
        )
    }
}

@Composable
private fun InfoCard(rows: List<Pair<String, String>>) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column {
            rows.forEachIndexed { index, (label, value) ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1.8f))
                }
            }
        }
    }
}

@Composable
private fun StatsGrid(stats: TaskStats) {
    val successRate = if (stats.total > 0) "${stats.success * 100 / stats.total}%" else "-"
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard("Total tugas", "${stats.total}", Modifier.weight(1f))
            StatCard("Berhasil", successRate, Modifier.weight(1f))
            StatCard("Rata-rata", formatLatency(stats.avgLatencyMs), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard("On-device", "${stats.onDevice}", Modifier.weight(1f))
            StatCard("Cloud", "${stats.cloud}", Modifier.weight(1f))
            StatCard("Fallback", "${stats.fallback}", Modifier.weight(1f))
        }
        Text(
            "Skill favorit: ${stats.topSkill ?: "belum ada"}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ConnectionRow(state: ConnectionState, onRetry: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        when (state) {
            ConnectionState.Idle, ConnectionState.Checking -> {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("Mengecek koneksi…", style = MaterialTheme.typography.bodySmall)
            }
            ConnectionState.Connected -> {
                Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = StatusColors.success, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Terhubung ke server dan database", style = MaterialTheme.typography.bodySmall)
            }
            is ConnectionState.Failed -> {
                Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(state.message, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                TextButton(onClick = onRetry) { Text("Cek lagi") }
            }
        }
    }
}

@Composable
private fun BaseUrlDialog(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Alamat server") },
        text = {
            Column {
                Text(
                    "Emulator: http://10.0.2.2:3000\nHP asli: http://<IP laptop>:3000 (satu jaringan/hotspot)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(text) }, enabled = text.isNotBlank()) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}
