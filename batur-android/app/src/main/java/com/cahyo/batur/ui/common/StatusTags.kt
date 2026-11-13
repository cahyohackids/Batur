package com.cahyo.batur.ui.common

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.cahyo.batur.ui.theme.StatusColors

@Composable
fun StatusTag(status: String, modifier: Modifier = Modifier) {
    val dark = isSystemInDarkTheme()
    val scheme = MaterialTheme.colorScheme
    val (bg, fg) = when (status) {
        "success" -> (if (dark) StatusColors.successDark else StatusColors.success).copy(alpha = 0.16f) to
            (if (dark) StatusColors.successDark else StatusColors.success)
        "answered" -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        "cancelled" -> (if (dark) StatusColors.warningDark else StatusColors.warning).copy(alpha = 0.16f) to
            (if (dark) StatusColors.warningDark else StatusColors.warning)
        "failed" -> scheme.errorContainer to scheme.onErrorContainer
        else -> scheme.surfaceVariant to scheme.onSurfaceVariant
    }
    Tag(statusLabel(status), modifier, container = bg, content = fg)
}

@Composable
fun ModeTag(mode: String, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val (bg, fg) = when (mode) {
        "on_device" -> scheme.primaryContainer to scheme.onPrimaryContainer
        "cloud" -> scheme.secondaryContainer to scheme.onSecondaryContainer
        else -> scheme.surfaceVariant to scheme.onSurfaceVariant
    }
    Tag(modeLabel(mode), modifier, container = bg, content = fg)
}
