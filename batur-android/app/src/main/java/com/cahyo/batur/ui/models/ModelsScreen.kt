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
    val downloaded by viewModel.downloaded.collectAsStateWithLifecycle()
    val activeSlug by viewModel.activeSlug.collectAsStateWithLifecycle()

    // Status unduhan bisa berubah di Detail Model; segarkan setiap layar ini tampil lagi.
    LifecycleResumeEffect(Unit) {
        viewModel.refreshDownloaded()
        onPauseOrDispose {}
    }

    BaturScaffold(title = "Model AI") { padding ->
        Box(Modifier.padding(padding)) {
            when (val s = state) {
                UiState.Loading -> LoadingView()
                UiState.Empty -> EmptyView("Belum ada model", "Daftar model belum diisi di server.")
                is UiState.Error -> ErrorView(s.message, onRetry = viewModel::load)
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
                                ModelCard(it, downloaded = it.slug in downloaded, active = it.slug == activeSlug) { onModelClick(it.slug) }
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

@Composable
private fun ModelCard(model: ModelSummary, downloaded: Boolean = false, active: Boolean = false, onClick: () -> Unit) {
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
                }
                if (model.isDefault) Tag("Utama")
            }
        }
    }
}
