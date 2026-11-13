package com.cahyo.batur.ui.common

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val localeId: Locale = Locale.forLanguageTag("id-ID")
private val zoneJakarta: ZoneId = ZoneId.of("Asia/Jakarta")
private val fullFormat = DateTimeFormatter.ofPattern("d MMM yyyy, HH.mm", localeId)
private val timeFormat = DateTimeFormatter.ofPattern("HH.mm.ss", localeId)

private fun parse(iso: String): Instant? = try {
    Instant.parse(iso)
} catch (_: Exception) {
    null
}

fun formatDateTime(iso: String?): String {
    val instant = iso?.let(::parse) ?: return "-"
    return fullFormat.format(instant.atZone(zoneJakarta)) + " WIB"
}

fun formatTime(iso: String?): String {
    val instant = iso?.let(::parse) ?: return ""
    return timeFormat.format(instant.atZone(zoneJakarta))
}

/** "baru saja", "5 menit lalu", "3 jam lalu", lalu tanggal lengkap. */
fun formatRelative(iso: String?): String {
    val instant = iso?.let(::parse) ?: return "-"
    val diff = Duration.between(instant, Instant.now())
    return when {
        diff.isNegative || diff.toMinutes() < 1 -> "baru saja"
        diff.toMinutes() < 60 -> "${diff.toMinutes()} menit lalu"
        diff.toHours() < 24 -> "${diff.toHours()} jam lalu"
        diff.toDays() < 7 -> "${diff.toDays()} hari lalu"
        else -> formatDateTime(iso)
    }
}

fun formatLatency(ms: Int?): String = when {
    ms == null -> "-"
    ms < 1000 -> "$ms ms"
    else -> String.format(localeId, "%.1f dtk", ms / 1000.0)
}

fun modeLabel(mode: String): String = when (mode) {
    "on_device" -> "On-device"
    "cloud" -> "Cloud"
    "fallback" -> "Fallback"
    else -> mode
}

fun statusLabel(status: String): String = when (status) {
    "success" -> "Berhasil"
    "cancelled" -> "Dibatalkan"
    "failed" -> "Gagal"
    "answered" -> "Dijawab"
    else -> status
}

fun categoryLabel(category: String): String =
    category.replaceFirstChar { it.titlecase(localeId) }

/** set_alarm + {"hour":5,"label":"Subuh"} -> set_alarm(hour=5, label="Subuh") */
fun formatFunctionCall(name: String, arguments: JsonObject?): String {
    val args = arguments?.entrySet().orEmpty().joinToString(", ") { (key, value) ->
        val shown = if (value.isJsonPrimitive && value.asJsonPrimitive.isString) "\"${value.asString}\"" else value.toString()
        "$key=$shown"
    }
    return "$name($args)"
}

/** {"name":"set_alarm","arguments":{"hour":5}} -> set_alarm(hour=5) */
fun formatFunctionCall(element: JsonElement?): String? {
    val call = element?.takeIf { it.isJsonObject }?.asJsonObject
    val name = call?.get("name")?.takeIf { it.isJsonPrimitive }?.asString ?: return null
    val args = call.get("arguments")?.takeIf { it.isJsonObject }?.asJsonObject
    return formatFunctionCall(name, args)
}
