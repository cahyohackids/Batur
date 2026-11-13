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
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Switch
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.runtime.DisposableEffect
import com.cahyo.batur.data.prefs.SettingsStore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.cahyo.batur.ui.common.ConnectionLine
import com.cahyo.batur.ui.common.ConnectionState
import com.cahyo.batur.ui.common.ServerUrlDialog
import com.cahyo.batur.ui.common.UpdateBanner
import com.cahyo.batur.ui.common.SectionTitle
import com.cahyo.batur.ui.common.UiState
import com.cahyo.batur.ui.common.formatLatency

private const val GITHUB_URL = "https://github.com/cahyohackids/Batur"
private const val EZKIELYNA_URL = "https://api.ezkielyna.store"

@Composable
fun ProfileScreen(
    onOpenNotes: () -> Unit,
    viewModel: ProfileViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val baseUrl by viewModel.baseUrl.collectAsStateWithLifecycle()
    val deviceId by viewModel.deviceId.collectAsStateWithLifecycle()
    val connection by viewModel.connection.collectAsStateWithLifecycle()
    val update by viewModel.update.collectAsStateWithLifecycle()
    val speechVoice by viewModel.speechVoice.collectAsStateWithLifecycle()
    val autoSpeak by viewModel.autoSpeak.collectAsStateWithLifecycle()
    val speech by viewModel.speech.collectAsStateWithLifecycle()
    DisposableEffect(Unit) { onDispose { viewModel.stopSpeech() } }
    var editing by rememberSaveable { mutableStateOf(false) }
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
            update?.let {
                UpdateBanner(it, onDismiss = viewModel::dismissUpdate)
                Spacer(Modifier.height(12.dp))
            }
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

            Spacer(Modifier.height(12.dp))
            Card(
                onClick = onOpenNotes,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.EditNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Catatan saya", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Catatan yang kamu simpan lewat Agent",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null)
                }
            }

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

            SectionTitle("Suara", Modifier.padding(top = 16.dp))
            SpeechSettings(
                voice = speechVoice,
                autoSpeak = autoSpeak,
                previewing = speech.messageId != null,
                onVoice = viewModel::setSpeechVoice,
                onAutoSpeak = viewModel::setAutoSpeak,
                onPreview = { if (speech.messageId != null) viewModel.stopSpeech() else viewModel.previewVoice() },
            )

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
                    Text(
                        deviceId ?: "Belum terdaftar (otomatis saat terhubung)",
                        fontFamily = if (deviceId != null) FontFamily.Monospace else null,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            PoweredByCard(onOpen = { runCatching { uriHandler.openUri(EZKIELYNA_URL) } })

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
        ServerUrlDialog(
            current = baseUrl,
            onDismiss = { editing = false },
            onSave = {
                editing = false
                viewModel.saveBaseUrl(it)
            },
            onTest = viewModel::testUrl,
        )
    }
}

@Composable
private fun SpeechSettings(
    voice: String,
    autoSpeak: Boolean,
    previewing: Boolean,
    onVoice: (String) -> Unit,
    onAutoSpeak: (Boolean) -> Unit,
    onPreview: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.toggleable(value = autoSpeak, role = Role.Switch, onValueChange = onAutoSpeak),
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Bacakan jawaban otomatis", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Tanpa ini, ketuk ikon speaker di bawah jawaban.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = autoSpeak, onCheckedChange = null)
            }
            Spacer(Modifier.height(12.dp))
            Text("Suara", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    SettingsStore.VOICE_FEMALE to "Perempuan",
                    SettingsStore.VOICE_MALE to "Laki-laki",
                    SettingsStore.VOICE_DEVICE to "Bawaan ponsel",
                ).forEach { (key, label) ->
                    FilterChip(selected = voice == key, onClick = { onVoice(key) }, label = { Text(label) })
                }
            }
            Text(
                if (voice == SettingsStore.VOICE_DEVICE) {
                    "Suara bawaan Android, jalan tanpa internet."
                } else {
                    "Suara natural dari server Batur. Kalau offline, otomatis pakai suara bawaan ponsel."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onPreview, contentPadding = PaddingValues(0.dp)) {
                Icon(
                    if (previewing) Icons.Outlined.StopCircle else Icons.AutoMirrored.Outlined.VolumeUp,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(if (previewing) "Berhenti" else "Coba suara")
            }
        }
    }
}

/** Mode cloud memakai gateway Ezkielyna API; ditampilkan sekadarnya, tanpa nama model. */
@Composable
private fun PoweredByCard(onOpen: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Cloud, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Mode cloud ditenagai Ezkielyna API", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "Satu API key untuk ratusan model AI, bayar pakai QRIS.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onOpen) { Text("Lihat") }
        }
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
        ConnectionLine(
            if (state == ConnectionState.Idle) ConnectionState.Checking else state,
            Modifier.weight(1f),
        )
        if (state is ConnectionState.Failed) TextButton(onClick = onRetry) { Text("Cek lagi") }
    }
}
