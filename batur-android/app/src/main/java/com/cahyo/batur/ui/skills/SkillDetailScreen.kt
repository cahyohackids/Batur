package com.cahyo.batur.ui.skills

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cahyo.batur.data.remote.SkillDetail
import com.cahyo.batur.ui.AppViewModelProvider
import com.cahyo.batur.ui.common.BaturScaffold
import com.cahyo.batur.ui.common.ErrorView
import com.cahyo.batur.ui.common.LoadingView
import com.cahyo.batur.ui.common.SectionTitle
import com.cahyo.batur.ui.common.Tag
import com.cahyo.batur.ui.common.UiState
import com.cahyo.batur.ui.common.categoryLabel
import com.cahyo.batur.ui.common.formatRelative
import com.cahyo.batur.ui.common.skillIcon

@Composable
fun SkillDetailScreen(
    onBack: () -> Unit,
    onTryExample: (String) -> Unit,
    viewModel: SkillDetailViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val toggling by viewModel.toggling.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.messageShown()
        }
    }

    BaturScaffold(
        title = "Detail Skill",
        onBack = onBack,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (val s = state) {
                UiState.Loading, UiState.Empty -> LoadingView()
                is UiState.Error -> ErrorView(s.message, onRetry = viewModel::load)
                is UiState.Success -> SkillDetailContent(
                    skill = s.data,
                    toggling = toggling,
                    onToggle = viewModel::setActive,
                    onTryExample = onTryExample,
                )
            }
        }
    }
}

@Composable
private fun SkillDetailContent(
    skill: SkillDetail,
    toggling: Boolean,
    onToggle: (Boolean) -> Unit,
    onTryExample: (String) -> Unit,
) {
    val params = remember(skill.parameters) { parseParameters(skill.parameters) }

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
                    Icon(skillIcon(skill.icon), contentDescription = null, modifier = Modifier.size(28.dp))
                }
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(skill.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Tag(categoryLabel(skill.category))
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(skill.description, style = MaterialTheme.typography.bodyLarge)

        Spacer(Modifier.height(16.dp))
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Skill aktif", style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (skill.isActive) "Agent boleh memilih skill ini." else "Agent tidak akan memilih skill ini.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = skill.isActive, onCheckedChange = onToggle, enabled = !toggling)
            }
        }

        SectionTitle("Statistik pemakaian", Modifier.padding(top = 16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatBox("Dipakai", "${skill.usageCount}×", Modifier.weight(1f))
            StatBox("Berhasil", "${skill.successCount}×", Modifier.weight(1f))
            StatBox("Terakhir", skill.lastUsedAt?.let(::formatRelative) ?: "belum", Modifier.weight(1f))
        }

        val examples = skill.exampleCommands.orEmpty()
        if (examples.isNotEmpty()) {
            SectionTitle("Contoh perintah", Modifier.padding(top = 16.dp))
            Text(
                "Ketuk untuk mencobanya di halaman Agent.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                examples.forEach { example ->
                    Card(
                        onClick = { onTryExample(example) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("“$example”", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Icon(
                                Icons.AutoMirrored.Outlined.Send,
                                contentDescription = "Coba",
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }

        SectionTitle("Fungsi untuk model AI", Modifier.padding(top = 16.dp))
        Text(
            "${skill.functionName}(${params.joinToString { if (it.required) it.name else "${it.name}?" }})",
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodyMedium,
        )
        if (params.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column {
                    params.forEachIndexed { index, param ->
                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(param.name, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    param.type,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    if (param.required) "wajib" else "opsional",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (param.required) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (param.description.isNotBlank()) {
                                Text(
                                    param.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }

        SectionTitle("Cara Batur menjalankannya", Modifier.padding(top = 16.dp))
        InfoRow("Mekanisme Android", skill.androidAction ?: "-", monospace = true)
        InfoRow(
            "Izin",
            skill.permissions.orEmpty().takeIf { it.isNotEmpty() }?.joinToString("\n") { it.substringAfterLast('.') }
                ?: "Tidak butuh izin khusus",
        )
        InfoRow(
            "Konfirmasi",
            if (skill.requiresConfirmation) "Batur menampilkan kartu konfirmasi sebelum menjalankan aksi"
            else "Langsung dijalankan tanpa konfirmasi",
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun StatBox(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = modifier,
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, monospace: Boolean = false) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = if (monospace) FontFamily.Monospace else null,
        )
    }
}
