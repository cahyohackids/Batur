package com.cahyo.batur.ui.models

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.cahyo.batur.agent.LocalModelState
import com.cahyo.batur.agent.ModelStore
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cahyo.batur.data.remote.ModelDetail
import com.cahyo.batur.ui.AppViewModelProvider
import com.cahyo.batur.ui.common.BaturScaffold
import com.cahyo.batur.ui.common.ErrorView
import com.cahyo.batur.ui.common.LoadingView
import com.cahyo.batur.ui.common.SectionTitle
import com.cahyo.batur.ui.common.Tag
import com.cahyo.batur.ui.common.UiState
import com.cahyo.batur.ui.common.formatLatency
import com.cahyo.batur.ui.common.modelIcon

@Composable
fun ModelDetailScreen(
    onBack: () -> Unit,
    viewModel: ModelDetailViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val target by viewModel.target.collectAsStateWithLifecycle()
    val local by viewModel.localState.collectAsStateWithLifecycle()
    val isActive by viewModel.isActive.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    val localSection: @Composable () -> Unit = {
        val t = target
        val l = local
        if (t != null && l != null) {
            LocalModelSection(
                target = t,
                local = l,
                isActive = isActive,
                freeBytes = viewModel::freeBytes,
                ramCheck = viewModel::ramCheck,
                onDownload = viewModel::download,
                onDismissFailure = viewModel::dismissFailure,
                onDelete = viewModel::delete,
                onActivate = viewModel::activate,
                onDeactivate = viewModel::deactivate,
            )
        }
    }

    BaturScaffold(title = "Detail Model", onBack = onBack, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Box(Modifier.padding(padding)) {
            when (val s = state) {
                UiState.Loading, UiState.Empty -> LoadingView()
                is UiState.Error -> {
                    val t = target
                    if (t == null) ErrorView(s.message, onRetry = viewModel::load)
                    else OfflineModelContent(t, s.message, onRetry = viewModel::load, localSection = localSection)
                }
                is UiState.Success -> ModelDetailContent(s.data, localSection)
            }
        }
    }
}

/** Server tidak terjangkau, tapi file model ada di ponsel: tetap bisa dinonaktifkan atau dihapus. */
@Composable
private fun OfflineModelContent(target: LocalTarget, message: String, onRetry: () -> Unit, localSection: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(target.model.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Tag("On-device")
        Spacer(Modifier.height(12.dp))
        Text(
            "Detail model belum bisa dimuat ($message). Kamu tetap bisa mengelola file di ponsel ini.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onRetry) { Text("Coba lagi") }
        localSection()
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ModelDetailContent(model: ModelDetail, localSection: @Composable () -> Unit) {
    val onDevice = model.type == "on_device"
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(56.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(modelIcon(model.type), contentDescription = null, modifier = Modifier.size(28.dp))
                }
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(model.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Tag(if (onDevice) "On-device" else "Cloud")
                    if (model.isDefault) Tag("Utama")
                    if (model.supportsTools) Tag("Function calling")
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(model.description, style = MaterialTheme.typography.bodyLarge)

        localSection()

        SectionTitle("Spesifikasi", Modifier.padding(top = 16.dp))
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
            Column {
                SpecRow("Penyedia", model.provider)
                SpecDivider()
                SpecRow("Runtime", model.runtime)
                SpecDivider()
                SpecRow("Ukuran", model.sizeLabel ?: "-")
                SpecDivider()
                SpecRow("Kebutuhan RAM", model.ramLabel ?: "-")
            }
        }

        SectionTitle("Statistik", Modifier.padding(top = 16.dp))
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
            Column {
                SpecRow("Dipakai", "${model.usageCount} tugas")
                SpecDivider()
                SpecRow("Rata-rata latency", formatLatency(model.avgLatencyMs))
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SpecRow(label: String, value: String) {
    Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1.3f))
    }
}

@Composable
private fun SpecDivider() = HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

/** Bagian "Di ponsel ini": unduh, pantau, aktifkan, atau hapus file model .litertlm. */
@Composable
private fun LocalModelSection(
    target: LocalTarget,
    local: LocalModelState,
    isActive: Boolean,
    freeBytes: () -> Long,
    ramCheck: () -> RamCheck?,
    onDownload: () -> Unit,
    onDismissFailure: () -> Unit,
    onDelete: () -> Unit,
    onActivate: () -> Unit,
    onDeactivate: () -> Unit,
) {
    var confirmDownload by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val model = target.model
    val size = model.sizeBytes?.let(ModelStore::formatBytes) ?: target.sizeLabel ?: "-"
    val ram = remember(target) { ramCheck() }
    val canDownload = target.downloadUrl != null

    SectionTitle("Di ponsel ini", Modifier.padding(top = 16.dp))
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when (local) {
                LocalModelState.NotDownloaded -> {
                    Text("Belum diunduh. File $size disimpan di folder aplikasi dan hanya perlu diunduh sekali.", style = MaterialTheme.typography.bodyMedium)
                    if (ram != null && !ram.enough) RamWarning(ram.warning)
                    Button(onClick = { confirmDownload = true }, enabled = canDownload) {
                        Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Unduh ($size)")
                    }
                }

                is LocalModelState.Downloading -> {
                    val total = local.totalBytes?.let(ModelStore::formatBytes) ?: size
                    Text(
                        "Mengunduh ${ModelStore.formatBytes(local.downloadedBytes)} dari $total" +
                            (local.waitingReason?.let { " · $it" } ?: ""),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    val progress = local.progress
                    if (progress != null) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(
                        "Unduhan tetap jalan walau aplikasi ditutup, dan model langsung dipakai kalau belum ada model aktif.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = onDelete) { Text("Batalkan unduhan") }
                }

                is LocalModelState.Ready -> {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Tersimpan di ponsel ($size)", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        if (isActive) Tag("Aktif")
                    }
                    Text(
                        if (isActive) "Agent memakai model ini lebih dulu, lalu Cloud kalau model tidak yakin atau gagal."
                        else "Aktifkan supaya Agent memproses perintah langsung di ponsel.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!isActive && ram != null && !ram.enough) RamWarning(ram.warning)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (isActive) OutlinedButton(onClick = onDeactivate) { Text("Nonaktifkan") }
                        else Button(onClick = onActivate) { Text("Jadikan model aktif") }
                        TextButton(onClick = { confirmDelete = true }) { Text("Hapus file") }
                    }
                }

                is LocalModelState.Failed -> {
                    Text(local.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (canDownload) {
                            Button(onClick = { confirmDownload = true }) { Text(if (local.fromDownload) "Coba lagi" else "Unduh ulang") }
                        }
                        if (local.fromDownload) TextButton(onClick = onDismissFailure) { Text("Tutup") }
                        else TextButton(onClick = onDelete) { Text("Hapus") }
                    }
                }
            }
        }
    }

    if (confirmDownload) {
        val free = remember { ModelStore.formatBytes(freeBytes()) }
        val lowRam = ram != null && !ram.enough
        AlertDialog(
            onDismissRequest = { confirmDownload = false },
            title = { Text("Unduh ${model.name}?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Ukuran file $size dari Hugging Face (litert-community). Sebaiknya pakai Wi-Fi. " +
                            "Ruang kosong saat ini $free. Kebutuhan memori: ${target.ramLabel ?: "-"}.",
                    )
                    if (lowRam) RamWarning(ram.warning)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDownload = false
                    onDownload()
                }) { Text(if (lowRam) "Tetap unduh" else "Unduh") }
            },
            dismissButton = { TextButton(onClick = { confirmDownload = false }) { Text("Batal") } },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Hapus file model?") },
            text = {
                Text(
                    "File ${model.name} ($size) dihapus dari ponsel. Agent kembali memakai Cloud " +
                        "sampai model diunduh lagi.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text("Hapus") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Batal") } },
        )
    }
}

@Composable
private fun RamWarning(text: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(
            Icons.Outlined.WarningAmber,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(18.dp),
        )
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}
