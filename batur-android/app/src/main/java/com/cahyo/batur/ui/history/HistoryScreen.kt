package com.cahyo.batur.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cahyo.batur.data.remote.TaskSummary
import com.cahyo.batur.ui.AppViewModelProvider
import com.cahyo.batur.ui.common.BaturScaffold
import com.cahyo.batur.ui.common.EmptyView
import com.cahyo.batur.ui.common.ErrorView
import com.cahyo.batur.ui.common.LoadingView
import com.cahyo.batur.ui.common.ModeTag
import com.cahyo.batur.ui.common.StatusTag
import com.cahyo.batur.ui.common.UiState
import com.cahyo.batur.ui.common.formatLatency
import com.cahyo.batur.ui.common.formatRelative
import com.cahyo.batur.ui.common.statusLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onTaskClick: (Int) -> Unit,
    viewModel: HistoryViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val refreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.refresh() }

    BaturScaffold(title = "Riwayat") { padding ->
        Column(Modifier.padding(padding)) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    FilterChip(selected = status == null, onClick = { viewModel.selectStatus(null) }, label = { Text("Semua") })
                }
                items(HistoryViewModel.STATUS_FILTERS) { s ->
                    FilterChip(
                        selected = status == s,
                        onClick = { viewModel.selectStatus(s) },
                        label = { Text(statusLabel(s)) },
                    )
                }
            }
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                when (val s = state) {
                    UiState.Loading -> LoadingView()
                    UiState.Empty -> EmptyView(
                        title = if (status == null) "Belum ada riwayat" else "Tidak ada tugas ${statusLabel(status!!).lowercase()}",
                        message = "Perintah yang kamu jalankan di halaman Agent akan tercatat di sini beserta jejak langkahnya.",
                    )
                    is UiState.Error -> ErrorView(s.message, onRetry = viewModel::refresh)
                    is UiState.Success -> LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(s.data, key = { it.id }) { task ->
                            TaskCard(task, onClick = { onTaskClick(task.id) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskCard(task: TaskSummary, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                task.command,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            task.resultText?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusTag(task.status)
                ModeTag(task.mode)
                Spacer(Modifier.weight(1f))
                Text(
                    "${formatRelative(task.createdAt)} · ${formatLatency(task.latencyMs)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
