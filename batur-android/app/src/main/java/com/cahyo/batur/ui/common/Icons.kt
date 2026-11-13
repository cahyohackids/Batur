package com.cahyo.batur.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.HourglassBottom
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.ui.graphics.vector.ImageVector

/** Ikon skill sesuai kolom `icon` di tabel skills. */
fun skillIcon(name: String?): ImageVector = when (name) {
    "alarm" -> Icons.Outlined.Alarm
    "hourglass" -> Icons.Outlined.HourglassBottom
    "calendar" -> Icons.Outlined.Event
    "note" -> Icons.Outlined.EditNote
    "message" -> Icons.AutoMirrored.Outlined.Chat
    "map" -> Icons.Outlined.Map
    else -> Icons.Outlined.Bolt
}

fun modelIcon(type: String): ImageVector =
    if (type == "on_device") Icons.Outlined.PhoneAndroid else Icons.Outlined.Cloud
