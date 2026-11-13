package com.cahyo.batur.ui.agent

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Cloud
import com.cahyo.batur.data.prefs.SettingsStore
import androidx.compose.runtime.remember
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.HistoryToggleOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.StopCircle
import com.cahyo.batur.speech.SpeechState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cahyo.batur.AppContainer
import com.cahyo.batur.ui.AppViewModelProvider
import com.cahyo.batur.agent.EngineState
import com.cahyo.batur.agent.executor.NoteExecutor
import com.cahyo.batur.ui.common.BaturScaffold
import com.cahyo.batur.ui.common.UpdateBanner
import com.cahyo.batur.ui.common.ModeTag
import com.cahyo.batur.ui.common.skillIcon

private val EXAMPLES = listOf(
    "Bangunin aku jam 5 subuh",
    "Timer 25 menit buat fokus belajar",
    "Besok jam 1 siang rapat himpunan",
    "Catat: beli plastik kemasan 2 pak",
    "Cari rute ke Simpang Lima",
)

@Composable
fun AgentScreen(
    container: AppContainer,
    onOpenTask: (Int) -> Unit,
    onOpenNotes: () -> Unit,
    viewModel: AgentViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val engine by viewModel.engine.collectAsStateWithLifecycle()
    val engineState by viewModel.engineState.collectAsStateWithLifecycle()
    val speech by viewModel.speechState.collectAsStateWithLifecycle()
    val update by container.update.collectAsStateWithLifecycle()
    var input by rememberSaveable { mutableStateOf("") }
    val draft by container.agentDraft.collectAsStateWithLifecycle()
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val voice = rememberVoiceInput { spoken -> input = if (input.isBlank()) spoken else "${input.trimEnd()} $spoken" }

    // Berhenti membacakan saat pindah ke layar lain.
    DisposableEffect(Unit) { onDispose { viewModel.stopSpeech() } }

    LaunchedEffect(draft) {
        draft?.let {
            input = it
            container.agentDraft.value = null
        }
    }

    val submit = {
        if (state.canSend && input.isNotBlank()) {
            viewModel.send(input)
            input = ""
            keyboard?.hide()
            // Lepas fokus supaya keyboard tidak muncul lagi saat kembali dari aplikasi tujuan.
            focusManager.clearFocus()
        }
    }

    BaturScaffold(
        title = "Batur",
        actions = {
            if (state.messages.isNotEmpty()) {
                IconButton(onClick = viewModel::clear, enabled = state.canSend) {
                    Icon(Icons.Outlined.RestartAlt, contentDescription = "Percakapan baru")
                }
            }
            EngineChip(
                choice = engine,
                onSelect = viewModel::setEngineMode,
                modifier = Modifier.padding(end = 8.dp),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding(),
        ) {
            update?.let {
                UpdateBanner(it, onDismiss = container::dismissUpdate, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }
            Box(Modifier.weight(1f)) {
                if (state.messages.isEmpty()) {
                    Welcome(onExample = { input = it })
                } else {
                    MessageList(
                        messages = state.messages,
                        loadingModel = engineState == EngineState.Loading,
                        onConfirm = viewModel::confirm,
                        onCancel = viewModel::cancel,
                        onCancelThinking = viewModel::cancelThinking,
                        onOpenTask = onOpenTask,
                        onOpenNotes = onOpenNotes,
                        speech = speech,
                        onSpeak = viewModel::toggleSpeech,
                    )
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text("Ketik perintah…") },
                    supportingText = if (state.hasPendingConfirmation) {
                        { Text("Jalankan atau batalkan aksi di atas dulu") }
                    } else {
                        null
                    },
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { submit() }),
                    modifier = Modifier.weight(1f),
                )
                if (voice != null) {
                    IconButton(onClick = voice, enabled = state.canSend) {
                        Icon(Icons.Outlined.Mic, contentDescription = "Ucapkan perintah")
                    }
                } else {
                    Spacer(Modifier.width(8.dp))
                }
                FilledIconButton(onClick = submit, enabled = state.canSend && input.isNotBlank()) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Kirim")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Welcome(onExample: (String) -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text("Halo! Mau Batur bantu apa?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(
            "Ketik perintah sehari-hari. Batur memilih skill yang cocok, minta konfirmasi, lalu menjalankannya.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Text("Coba salah satu:", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EXAMPLES.forEach { example ->
                AssistChip(onClick = { onExample(example) }, label = { Text(example) })
            }
        }
    }
}

@Composable
private fun MessageList(
    messages: List<ChatMessage>,
    loadingModel: Boolean,
    onConfirm: (Long) -> Unit,
    onCancel: (Long) -> Unit,
    onCancelThinking: () -> Unit,
    onOpenTask: (Int) -> Unit,
    onOpenNotes: () -> Unit,
    speech: SpeechState,
    onSpeak: (ChatMessage.Assistant) -> Unit,
) {
    val listState = rememberLazyListState()
    // Gulir ke pesan terbaru setiap ada pesan baru atau status kartu berubah.
    LaunchedEffect(messages.size, messages.lastOrNull()) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(messages, key = { it.id }) { message ->
            when (message) {
                is ChatMessage.User -> UserBubble(message.text)
                is ChatMessage.Thinking -> ThinkingRow(loadingModel, onCancelThinking)
                is ChatMessage.Assistant -> AssistantBubble(
                    message,
                    speech = speech.takeIf { it.messageId == message.id },
                    onSpeak = { onSpeak(message) },
                    onOpenTask = onOpenTask,
                )
                is ChatMessage.Confirmation -> ConfirmationCard(
                    message,
                    onConfirm = { onConfirm(message.id) },
                    onCancel = { onCancel(message.id) },
                    onOpenNotes = onOpenNotes,
                )
            }
        }
    }
}

@Composable
private fun UserBubble(text: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            shape = RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp),
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
        }
    }
}

@Composable
private fun ThinkingRow(loadingModel: Boolean, onCancel: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp)) {
        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            if (loadingModel) "Memuat model…" else "Batur sedang berpikir…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onCancel) { Text("Batal") }
    }
}

@Composable
private fun AssistantBubble(
    message: ChatMessage.Assistant,
    /** Status suara untuk pesan ini saja; null kalau tidak sedang dibacakan. */
    speech: SpeechState?,
    onSpeak: () -> Unit,
    onOpenTask: (Int) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val (container, content) = when (message.tone) {
        Tone.NORMAL -> colors.surfaceContainerHigh to colors.onSurface
        Tone.SUCCESS -> colors.secondaryContainer to colors.onSecondaryContainer
        Tone.ERROR -> colors.errorContainer to colors.onErrorContainer
    }
    Column(Modifier.widthIn(max = 320.dp)) {
        Surface(
            shape = RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp),
            color = container,
            contentColor = content,
        ) {
            Text(message.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
            message.mode?.let { ModeTag(it) }
            message.taskId?.let { id ->
                TextButton(onClick = { onOpenTask(id) }) { Text("Lihat jejak") }
            }
            IconButton(onClick = onSpeak, modifier = Modifier.size(40.dp)) {
                when (speech) {
                    is SpeechState.Loading -> CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    is SpeechState.Playing -> Icon(Icons.Outlined.StopCircle, contentDescription = "Berhenti membacakan")
                    else -> Icon(
                        Icons.AutoMirrored.Outlined.VolumeUp,
                        contentDescription = "Bacakan jawaban",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ConfirmationCard(
    message: ChatMessage.Confirmation,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onOpenNotes: () -> Unit,
) {
    val pending = message.state == ConfirmState.PENDING
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = if (pending) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(40.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) { Icon(skillIcon(message.icon), contentDescription = null) }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(message.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    message.skillName?.let {
                        Text("Skill: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                ModeTag(message.mode)
            }

            Spacer(Modifier.height(12.dp))
            message.details.forEach { (label, value) ->
                Row(Modifier.padding(vertical = 3.dp)) {
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(80.dp),
                    )
                    Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                message.mechanism,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))

            when (message.state) {
                ConfirmState.PENDING -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Batal") }
                    Button(onClick = onConfirm, modifier = Modifier.weight(1f)) { Text("Jalankan") }
                }

                ConfirmState.RUNNING -> Column {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Spacer(Modifier.height(6.dp))
                    Text("Menjalankan…", style = MaterialTheme.typography.bodySmall)
                }

                ConfirmState.DONE -> Row(verticalAlignment = Alignment.CenterVertically) {
                    StateLine(Icons.Outlined.CheckCircle, "Dijalankan", MaterialTheme.colorScheme.primary)
                    if (message.functionName == NoteExecutor.functionName) {
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = onOpenNotes) { Text("Lihat catatan") }
                    }
                }
                ConfirmState.FAILED -> StateLine(Icons.Outlined.ErrorOutline, "Gagal dijalankan", MaterialTheme.colorScheme.error)
                ConfirmState.CANCELLED -> StateLine(Icons.Outlined.Cancel, "Dibatalkan", MaterialTheme.colorScheme.onSurfaceVariant)
                ConfirmState.EXPIRED -> StateLine(
                    Icons.Outlined.HistoryToggleOff,
                    "Kedaluwarsa. Kirim ulang perintahnya kalau masih perlu.",
                    MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StateLine(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = color)
    }
}

/** Chip engine di bilah atas: menunjukkan engine perintah berikutnya dan bisa diganti. */
@Composable
private fun EngineChip(choice: EngineChoice, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        AssistChip(
            onClick = { open = true },
            label = { Text(if (choice.usesOnDevice) "On-device" else "Cloud") },
            leadingIcon = {
                Icon(
                    if (choice.usesOnDevice) Icons.Outlined.PhoneAndroid else Icons.Outlined.Cloud,
                    contentDescription = null,
                    modifier = Modifier.width(18.dp),
                )
            },
            colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            EngineOption(
                selected = choice.mode == SettingsStore.ENGINE_AUTO,
                title = "Otomatis",
                subtitle = choice.onDeviceModel?.let { "On-device ($it), Cloud kalau perlu" }
                    ?: "Belum ada model on-device aktif, jadi pakai Cloud",
            ) {
                onSelect(SettingsStore.ENGINE_AUTO)
                open = false
            }
            EngineOption(
                selected = choice.mode == SettingsStore.ENGINE_CLOUD,
                title = "Selalu Cloud",
                subtitle = "Lewat Batur API, butuh internet",
            ) {
                onSelect(SettingsStore.ENGINE_CLOUD)
                open = false
            }
        }
    }
}

@Composable
private fun EngineOption(selected: Boolean, title: String, subtitle: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = {
            Column {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        leadingIcon = {
            if (selected) Icon(Icons.Outlined.Check, contentDescription = "Dipilih")
            else Spacer(Modifier.width(24.dp))
        },
        onClick = onClick,
    )
}
