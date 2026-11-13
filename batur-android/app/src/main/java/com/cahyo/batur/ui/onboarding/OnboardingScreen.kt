package com.cahyo.batur.ui.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cahyo.batur.R
import com.cahyo.batur.agent.ModelRecommendation
import com.cahyo.batur.agent.ModelRequirements
import com.cahyo.batur.ui.AppViewModelProvider
import com.cahyo.batur.ui.common.ConnectionLine
import com.cahyo.batur.ui.common.ConnectionState
import com.cahyo.batur.ui.common.ServerUrlDialog

private const val STEPS = 3

/**
 * Tiga langkah saat aplikasi pertama dibuka: kenalan, tes koneksi server, rekomendasi model.
 * @param onFinish dipanggil setelah flag tersimpan; argumennya slug model yang ingin diunduh, atau null.
 */
@Composable
fun OnboardingScreen(
    onFinish: (downloadSlug: String?) -> Unit,
    viewModel: OnboardingViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    val connection by viewModel.connection.collectAsStateWithLifecycle()
    val baseUrl by viewModel.baseUrl.collectAsStateWithLifecycle()
    var editing by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(step) {
        if (step == 1 && connection == ConnectionState.Idle) viewModel.checkConnection()
    }

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(24.dp),
    ) {
        LinearProgressIndicator(progress = { (step + 1f) / STEPS }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Text(
            "Langkah ${step + 1} dari $STEPS",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            when (step) {
                0 -> IntroStep()
                1 -> ServerStep(
                    baseUrl = baseUrl,
                    connection = connection,
                    onRetry = viewModel::checkConnection,
                    onEdit = { editing = true },
                )
                else -> ModelStep(viewModel.totalRamBytes, viewModel.recommendation)
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (step > 0) TextButton(onClick = { step-- }) { Text("Kembali") }
            Spacer(Modifier.weight(1f))
            val recommended = viewModel.recommendation.slug
            when {
                step < STEPS - 1 -> Button(onClick = { step++ }) { Text("Lanjut") }
                recommended != null -> {
                    OutlinedButton(onClick = { viewModel.finish { onFinish(null) } }) { Text("Nanti saja") }
                    Button(onClick = { viewModel.finish { onFinish(recommended) } }) { Text("Unduh sekarang") }
                }
                else -> Button(onClick = { viewModel.finish { onFinish(null) } }) { Text("Mulai pakai Batur") }
            }
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
private fun IntroStep() {
    Image(
        painter = painterResource(R.drawable.ic_launcher_foreground),
        contentDescription = null,
        modifier = Modifier
            .size(72.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF00696B)),
    )
    Spacer(Modifier.height(16.dp))
    Text("Halo, aku Batur", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(8.dp))
    Text(
        "Asisten yang menjalankan perintah sehari-hari langsung di ponselmu.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(24.dp))
    Point(Icons.Outlined.Tune, "Ketik atau ucapkan perintah", "Misalnya \"Bangunin aku jam 5 subuh\" atau \"Catat: beli gas\".")
    Point(Icons.Outlined.TaskAlt, "Selalu minta konfirmasi", "Setiap aksi ditampilkan dulu. Batur baru jalan setelah kamu tekan Jalankan.")
    Point(Icons.Outlined.PhoneAndroid, "Bisa offline", "Dengan model on-device, perintah diproses di ponsel tanpa internet.")
}

@Composable
private fun Point(icon: ImageVector, title: String, body: String) {
    Row(Modifier.padding(vertical = 8.dp)) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(40.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.padding(8.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ServerStep(baseUrl: String, connection: ConnectionState, onRetry: () -> Unit, onEdit: () -> Unit) {
    Text("Cek koneksi server", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(8.dp))
    Text(
        "Server Batur menyimpan riwayat dan catatan, dan menjalankan mode Cloud.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(16.dp))
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Alamat", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(baseUrl, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
            ConnectionLine(if (connection == ConnectionState.Idle) ConnectionState.Checking else connection)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (connection is ConnectionState.Failed) OutlinedButton(onClick = onRetry) { Text("Coba lagi") }
                TextButton(onClick = onEdit) { Text("Ubah alamat server") }
            }
        }
    }
    if (connection is ConnectionState.Failed) {
        Spacer(Modifier.height(12.dp))
        Text(
            "Tidak apa-apa, kamu tetap bisa lanjut. Alamat server bisa diubah nanti di Profil.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ModelStep(totalRam: Long, recommendation: ModelRecommendation) {
    Text("Pilih otak Batur", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(8.dp))
    Text(
        "RAM ponsel ini ${ModelRequirements.formatRam(totalRam)}.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(16.dp))
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (recommendation == ModelRecommendation.CLOUD_ONLY) Icons.Outlined.Cloud else Icons.Outlined.PhoneAndroid,
                contentDescription = null,
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Saran: ${recommendation.label}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    when (recommendation) {
                        ModelRecommendation.GEMMA -> "Paling pintar untuk on-device. File ±2,6 GB, sebaiknya unduh lewat Wi-Fi."
                        ModelRecommendation.QWEN -> "Ringan dan cukup cepat untuk RAM ponsel ini. File ±350 MB."
                        ModelRecommendation.CLOUD_ONLY -> "RAM ponsel ini terlalu kecil untuk model on-device. Batur tetap jalan lewat mode Cloud."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    Text(
        "Tanpa model on-device pun Batur tetap bisa dipakai lewat mode Cloud (butuh internet). Model bisa diunduh kapan saja dari tab Model.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
