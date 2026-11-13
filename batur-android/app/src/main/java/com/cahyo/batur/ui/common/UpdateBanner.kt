package com.cahyo.batur.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cahyo.batur.data.remote.AppVersion

const val RELEASES_URL = "https://github.com/cahyohackids/Batur/releases"

/** Banner versi baru dari GET /api/app/version. Tombol Unduh membuka tautan APK di browser. */
@Composable
fun UpdateBanner(update: AppVersion, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    val url = update.apkUrl?.takeIf { it.startsWith("https://") } ?: RELEASES_URL
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.SystemUpdate, contentDescription = null)
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            ) {
                Text("Batur ${update.versionName} sudah tersedia", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                update.notes?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            TextButton(onClick = { runCatching { uriHandler.openUri(url) } }) { Text("Unduh") }
            IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, contentDescription = "Tutup") }
        }
    }
}
