package com.cahyo.batur.ui.notes

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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cahyo.batur.data.remote.Note
import com.cahyo.batur.data.repository.PendingNote
import com.cahyo.batur.ui.AppViewModelProvider
import com.cahyo.batur.ui.common.BaturScaffold
import com.cahyo.batur.ui.common.EmptyView
import com.cahyo.batur.ui.common.ErrorView
import com.cahyo.batur.ui.common.LoadingView
import com.cahyo.batur.ui.common.Tag
import com.cahyo.batur.ui.common.UiState
import com.cahyo.batur.ui.common.formatRelative

/** Yang akan dihapus setelah dikonfirmasi: catatan di server atau catatan di antrean. */
private sealed interface DeleteTarget {
    val content: String

    data class Remote(val note: Note) : DeleteTarget {
        override val content: String get() = note.content
    }

    data class Queued(val note: PendingNote) : DeleteTarget {
        override val content: String get() = note.content
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(
    onBack: () -> Unit,
    viewModel: NotesViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pending by viewModel.pending.collectAsStateWithLifecycle()
    val refreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    var deleting by remember { mutableStateOf<DeleteTarget?>(null) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { viewModel.refresh() }
    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.messageShown()
        }
    }

    BaturScaffold(title = "Catatan saya", onBack = onBack, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            val s = state
            val remote = (s as? UiState.Success)?.data.orEmpty()
            when {
                s == UiState.Loading && pending.isEmpty() -> LoadingView()
                s is UiState.Error && pending.isEmpty() -> ErrorView(s.message, onRetry = viewModel::refresh)
                s == UiState.Empty && pending.isEmpty() -> EmptyView(
                    title = "Belum ada catatan",
                    message = "Coba bilang ke Agent: \"Catat: beli plastik kemasan 2 pak\".",
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (s is UiState.Error) {
                        item {
                            Text(
                                "Catatan di server belum bisa dimuat: ${s.message}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    items(pending, key = { "p-" + it.clientId }) { note ->
                        NoteCard(note.content, note.createdAt, queued = true) { deleting = DeleteTarget.Queued(note) }
                    }
                    items(remote, key = { it.id }) { note ->
                        NoteCard(note.content, note.createdAt, queued = false) { deleting = DeleteTarget.Remote(note) }
                    }
                }
            }
        }
    }

    deleting?.let { target ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Hapus catatan?") },
            text = { Text("\"${target.content}\" akan dihapus permanen.") },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    when (target) {
                        is DeleteTarget.Remote -> viewModel.delete(target.note)
                        is DeleteTarget.Queued -> viewModel.deletePending(target.note)
                    }
                }) { Text("Hapus") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Batal") } },
        )
    }
}

@Composable
private fun NoteCard(content: String, createdAt: String, queued: Boolean, onDelete: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(content, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (queued) {
                        Tag(
                            "Belum terkirim",
                            container = MaterialTheme.colorScheme.tertiaryContainer,
                            content = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                    Text(
                        formatRelative(createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, contentDescription = "Hapus catatan") }
        }
    }
}
