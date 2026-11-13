package com.cahyo.batur.ui.models

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material3.TextButton
import com.cahyo.batur.agent.LocalModelState
import com.cahyo.batur.agent.ModelStore
import com.cahyo.batur.data.remote.ModelSummary
import com.cahyo.batur.ui.AppViewModelProvider
import com.cahyo.batur.ui.common.BaturScaffold
import com.cahyo.batur.ui.common.EmptyView
import com.cahyo.batur.ui.common.ErrorView
import com.cahyo.batur.ui.common.LoadingView
import com.cahyo.batur.ui.common.SectionTitle
import com.cahyo.batur.ui.common.Tag
import com.cahyo.batur.ui.common.UiState
import com.cahyo.batur.ui.common.modelIcon

@Composable
fun ModelsScreen(
    onModelClick: (String) -> Unit,
    viewModel: ModelsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val local by viewModel.local.collectAsStateWithLifecycle()
    val offline by viewModel.offline.collectAsStateWithLifecycle()
    val activeSlug by viewModel.activeSlug.collectAsStateWithLifecycle()

    // Status unduhan bisa berubah di Detail Model; segarkan setiap layar ini tampil lagi.
    LifecycleResumeEffect(Unit) {
        viewModel.refreshLocal()
        onPauseOrDispose {}
    }

    BaturScaffold(title = "Model AI") { padding ->
        Box(Modifier.padding(padding)) {
            when (val s = state) {
                UiState.Loading -> LoadingView()
                UiState.Empty -> EmptyView("Belum ada model", "Daftar model belum diisi di server.")
                is UiState.Error -> if (offline.isEmpty()) {
                    ErrorView(s.message, onRetry = viewModel::load)
                } else {
                    OfflineModels(s.message, offline, activeSlug, onRetry = viewModel::load, onModelClick = onModelClick)
                }
                is UiState.Success -> {
                    val groups = s.data.groupBy { it.type }
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        groups["on_device"]?.let { list ->
                            item { SectionTitle("Di ponsel (offline)") }
                            items(list, key = { it.slug }) {
                                ModelCard(it, local = local[it.slug], active = it.slug == activeSlug) { onModelClick(it.slug) }
                            }
                        }
                        groups["cloud"]?.let { list ->
                            item { SectionTitle("Cloud (lewat Batur API)") }
                            items(list, key = { it.slug }) { ModelCard(it) { onModelClick(it.slug) } }
                        }
                    }
                }
            }
        }
    }
}

/** Server tidak terjangkau: model yang sudah ada di ponsel tetap bisa dibuka, dinonaktifkan, atau dihapus. */
@Composable
private fun OfflineModels(
    message: String,
    models: List<LocalModelEntry>,
    activeSlug: String?,
    onRetry: () -> Unit,
    onModelClick: (String) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Column(Modifier.padding(16.dp).fillMaxWidth()) {
                    Text("Daftar model gagal dimuat", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(message, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = onRetry) { Text("Coba lagi") }
                }
            }
        }
        item { SectionTitle("Di ponsel ini") }
        items(models, key = { it.model.slug }) { entry ->
            val summary = ModelSummary(
                id = 0,
                slug = entry.model.slug,
                name = entry.model.name,
                type = "on_device",
                provider = "Tersimpan di ponsel",
                sizeLabel = entry.model.sizeBytes?.let(ModelStore::formatBytes),
                isDefault = false,
                fileName = entry.model.fileName,
                sizeBytes = entry.model.sizeBytes,
            )
            ModelCard(summary, local = entry.state, active = entry.model.slug == activeSlug) { onModelClick(entry.model.slug) }
        }
    }
}

@Composable
private fun ModelCard(model: ModelSummary, local: LocalModelState? = null, active: Boolean = false, onClick: () -> Unit) {
    val downloaded = local is LocalModelState.Ready
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) { Icon(modelIcon(model.type), contentDescription = null) }
            }
            Spacer(Modifier.size(14.dp))
            Column(Modifier.weight(1f)) {
                Text(model.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    listOfNotNull(model.provider, model.sizeLabel).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (active && downloaded) {
                    Tag("Aktif", container = MaterialTheme.colorScheme.primary, content = MaterialTheme.colorScheme.onPrimary)
                } else if (downloaded) {
                    Tag("Terunduh")
                } else if (local is LocalModelState.Downloading) {
                    Tag("Mengunduh")
                } else if (local is LocalModelState.Failed) {
                    Tag("Gagal", container = MaterialTheme.colorScheme.errorContainer, content = MaterialTheme.colorScheme.onErrorContainer)
                }
                if (model.isDefault) Tag("Utama")
            }
        }
    }
}
