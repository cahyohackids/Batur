package com.cahyo.batur.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cahyo.batur.data.remote.TaskDetail
import com.cahyo.batur.data.remote.TaskStep
import com.cahyo.batur.ui.AppViewModelProvider
import com.cahyo.batur.ui.common.BaturScaffold
import com.cahyo.batur.ui.common.ErrorView
import com.cahyo.batur.ui.common.LoadingView
import com.cahyo.batur.ui.common.ModeTag
import com.cahyo.batur.ui.common.SectionTitle
import com.cahyo.batur.ui.common.StatusTag
import com.cahyo.batur.ui.common.UiState
import com.cahyo.batur.ui.common.formatDateTime
import com.cahyo.batur.ui.common.formatFunctionCall
import com.cahyo.batur.ui.common.formatLatency
import com.cahyo.batur.ui.common.formatTime

@Composable
fun HistoryDetailScreen(
    onBack: () -> Unit,
    viewModel: HistoryDetailViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val deleting by viewModel.deleting.collectAsStateWithLifecycle()
    val deleted by viewModel.deleted.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(deleted) { if (deleted) onBack() }
    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.messageShown()
        }
    }

    BaturScaffold(
        title = "Detail Riwayat",
        onBack = onBack,
        snackbarHost = { SnackbarHost(snackbar) },
        actions = {
            if (state is UiState.Success) {
                if (deleting) {
                    CircularProgressIndicator(Modifier.size(24.dp).padding(end = 4.dp), strokeWidth = 2.dp)
                } else {
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Hapus riwayat")
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (val s = state) {
                UiState.Loading, UiState.Empty -> LoadingView()
                is UiState.Error -> ErrorView(s.message, onRetry = viewModel::load)
                is UiState.Success -> TaskDetailContent(s.data)
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Hapus riwayat ini?") },
            text = { Text("Tugas beserta seluruh jejak langkahnya akan dihapus dari server.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete()
                }) { Text("Hapus") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Batal") }
            },
        )
    }
}

@Composable
private fun TaskDetailContent(task: TaskDetail) {
    val steps = task.steps.orEmpty()
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
            Column(Modifier.padding(16.dp)) {
                Text("Perintah", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("“${task.command}”", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusTag(task.status)
                    ModeTag(task.mode)
                }
                Spacer(Modifier.height(12.dp))
                Row {
                    MetaItem("Model", task.modelSlug ?: "-", Modifier.weight(1f))
                    MetaItem("Skill", task.skillSlug ?: "tidak ada", Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                Row {
                    MetaItem("Latency", formatLatency(task.latencyMs), Modifier.weight(1f))
                    MetaItem("Waktu", formatDateTime(task.createdAt), Modifier.weight(1f))
                }
            }
        }

        formatFunctionCall(task.functionCall)?.let { call ->
            SectionTitle("Pemanggilan fungsi", Modifier.padding(top = 16.dp))
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(call, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(12.dp))
            }
        }

        SectionTitle("Jejak agent", Modifier.padding(top = 16.dp))
        if (steps.isEmpty()) {
            Text("Tidak ada langkah yang tercatat.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        steps.forEachIndexed { index, step ->
            TimelineItem(step = step, isLast = index == steps.lastIndex)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun MetaItem(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

private data class StepStyle(val label: String, val icon: ImageVector, val container: Color, val content: Color)

@Composable
private fun stepStyle(type: String): StepStyle {
    val c = MaterialTheme.colorScheme
    return when (type) {
        "input" -> StepStyle("Perintah pengguna", Icons.Outlined.Person, c.secondaryContainer, c.onSecondaryContainer)
        "reasoning" -> StepStyle("Penalaran", Icons.Outlined.Psychology, c.tertiaryContainer, c.onTertiaryContainer)
        "tool_call" -> StepStyle("Model memanggil fungsi", Icons.Outlined.Code, c.primaryContainer, c.onPrimaryContainer)
        "confirmation" -> StepStyle("Konfirmasi", Icons.Outlined.TaskAlt, c.secondaryContainer, c.onSecondaryContainer)
        "execution" -> StepStyle("Eksekusi di ponsel", Icons.Outlined.PlayArrow, c.primary, c.onPrimary)
        "response" -> StepStyle("Respons Batur", Icons.AutoMirrored.Outlined.Chat, c.surfaceContainerHighest, c.onSurface)
        "error" -> StepStyle("Error", Icons.Outlined.ErrorOutline, c.errorContainer, c.onErrorContainer)
        else -> StepStyle(type, Icons.Outlined.Code, c.surfaceVariant, c.onSurfaceVariant)
    }
}

@Composable
private fun TimelineItem(step: TaskStep, isLast: Boolean) {
    val style = stepStyle(step.type)
    Row(Modifier.height(IntrinsicSize.Min)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(36.dp)) {
            Surface(shape = CircleShape, color = style.container, contentColor = style.content, modifier = Modifier.size(32.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(style.icon, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
            if (!isLast) {
                Box(
                    Modifier
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.padding(top = 4.dp, bottom = if (isLast) 0.dp else 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(style.label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(8.dp))
                Text(
                    formatTime(step.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                step.content,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = if (step.type == "tool_call" || step.type == "execution") FontFamily.Monospace else null,
                color = if (step.type == "error") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
