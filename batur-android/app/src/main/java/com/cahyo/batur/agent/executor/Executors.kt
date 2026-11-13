package com.cahyo.batur.agent.executor

import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import com.cahyo.batur.data.repository.NoteOutcome
import com.google.gson.JsonObject
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

private val localeId: Locale = Locale.forLanguageTag("id-ID")
private val dateLabel = DateTimeFormatter.ofPattern("EEEE, d MMM yyyy", localeId)

private fun hhmm(hour: Int, minute: Int) = String.format(localeId, "%02d.%02d", hour, minute)

// ---------- set_alarm ----------

object AlarmExecutor : SkillExecutor {
    override val functionName = "set_alarm"

    override fun prepare(args: JsonObject): PreparedAction {
        val hour = args.requireInt("hour", 0..23)
        val minute = args.optInt("minute") ?: 0
        if (minute !in 0..59) throw InvalidArgumentsException("minute=$minute di luar batas 0–59")
        val label = args.optString("label")
        val time = hhmm(hour, minute)

        return PreparedAction(
            functionName = functionName,
            title = "Pasang alarm",
            details = listOfNotNull("Jam" to time, label?.let { "Label" to it }),
            icon = "alarm",
            mechanism = "AlarmClock.ACTION_SET_ALARM",
        ) { ctx ->
            val intent = Intent(AlarmClock.ACTION_SET_ALARM)
                .putExtra(AlarmClock.EXTRA_HOUR, hour)
                .putExtra(AlarmClock.EXTRA_MINUTES, minute)
                .putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            label?.let { intent.putExtra(AlarmClock.EXTRA_MESSAGE, it) }
            if (ctx.context.launch(intent)) {
                ExecResult(true, "Alarm $time dipasang lewat aplikasi Jam.", "AlarmClock.ACTION_SET_ALARM")
            } else {
                ExecResult(false, "Tidak ada aplikasi Jam yang bisa memasang alarm.", "AlarmClock.ACTION_SET_ALARM")
            }
        }
    }
}

// ---------- set_timer ----------

object TimerExecutor : SkillExecutor {
    override val functionName = "set_timer"

    override fun prepare(args: JsonObject): PreparedAction {
        val seconds = args.requireInt("seconds", 1..86_400)
        val label = args.optString("label")
        val duration = formatDuration(seconds)

        return PreparedAction(
            functionName = functionName,
            title = "Jalankan timer",
            details = listOfNotNull("Durasi" to duration, label?.let { "Label" to it }),
            icon = "hourglass",
            mechanism = "AlarmClock.ACTION_SET_TIMER",
        ) { ctx ->
            val intent = Intent(AlarmClock.ACTION_SET_TIMER)
                .putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                .putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            label?.let { intent.putExtra(AlarmClock.EXTRA_MESSAGE, it) }
            if (ctx.context.launch(intent)) {
                ExecResult(true, "Timer $duration dimulai di aplikasi Jam.", "AlarmClock.ACTION_SET_TIMER")
            } else {
                ExecResult(false, "Tidak ada aplikasi Jam yang bisa menjalankan timer.", "AlarmClock.ACTION_SET_TIMER")
            }
        }
    }

    internal fun formatDuration(seconds: Int): String {
        val h = seconds / 3600
        val m = seconds % 3600 / 60
        val s = seconds % 60
        return listOfNotNull(
            h.takeIf { it > 0 }?.let { "$it jam" },
            m.takeIf { it > 0 }?.let { "$it menit" },
            s.takeIf { it > 0 }?.let { "$it detik" },
        ).joinToString(" ")
    }
}

// ---------- create_calendar_event ----------

object CalendarExecutor : SkillExecutor {
    override val functionName = "create_calendar_event"
    private val timePattern = Regex("""^(\d{1,2})[:.](\d{2})""")

    override fun prepare(args: JsonObject): PreparedAction {
        val title = args.requireString("title")
        val date = try {
            LocalDate.parse(args.requireString("date"))
        } catch (_: DateTimeParseException) {
            throw InvalidArgumentsException("format date harus YYYY-MM-DD")
        }
        val match = timePattern.find(args.requireString("start_time"))
            ?: throw InvalidArgumentsException("format start_time harus HH:mm")
        val (hour, minute) = match.destructured.let { (h, m) -> h.toInt() to m.toInt() }
        if (hour !in 0..23 || minute !in 0..59) throw InvalidArgumentsException("start_time tidak valid")
        val durationMinutes = (args.optInt("duration_minutes") ?: 60).coerceIn(5, 1440)
        val location = args.optString("location")

        val start = date.atTime(LocalTime.of(hour, minute))
        val end = start.plusMinutes(durationMinutes.toLong())
        val zone = ZoneId.systemDefault()
        val timeRange = "${hhmm(hour, minute)}–${hhmm(end.hour, end.minute)}"

        return PreparedAction(
            functionName = functionName,
            title = "Tambah acara ke Kalender",
            details = listOfNotNull(
                "Judul" to title,
                "Tanggal" to dateLabel.format(date),
                "Jam" to timeRange,
                location?.let { "Lokasi" to it },
            ),
            icon = "calendar",
            mechanism = "Intent.ACTION_INSERT → CalendarContract.Events",
        ) { ctx ->
            val intent = Intent(Intent.ACTION_INSERT)
                .setData(CalendarContract.Events.CONTENT_URI)
                .putExtra(CalendarContract.Events.TITLE, title)
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start.atZone(zone).toInstant().toEpochMilli())
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end.atZone(zone).toInstant().toEpochMilli())
            location?.let { intent.putExtra(CalendarContract.Events.EVENT_LOCATION, it) }
            if (ctx.context.launch(intent)) {
                ExecResult(
                    true,
                    "Form acara \"$title\" dibuka di Kalender. Tekan Simpan di sana untuk menyimpannya.",
                    "Intent.ACTION_INSERT → CalendarContract.Events",
                )
            } else {
                ExecResult(false, "Tidak ada aplikasi Kalender di ponsel ini.", "Intent.ACTION_INSERT → CalendarContract.Events")
            }
        }
    }
}

// ---------- save_note ----------

object NoteExecutor : SkillExecutor {
    override val functionName = "save_note"
    override val defaultConfirmation = false

    override fun prepare(args: JsonObject): PreparedAction {
        // Model kecil kadang ikut menyalin kata perintahnya ("Catat: ...") ke isi catatan.
        val content = args.requireString("content")
            .replace(COMMAND_PREFIX, "")
            .replaceFirstChar { it.uppercaseChar() }
            .ifBlank { throw InvalidArgumentsException("isi catatan kosong") }
        return PreparedAction(
            functionName = functionName,
            title = "Simpan catatan",
            details = listOf("Isi" to content),
            icon = "note",
            mechanism = "POST /api/notes",
        ) { ctx ->
            when (val outcome = ctx.noteRepository.save(content)) {
                is NoteOutcome.Saved -> ExecResult(true, "Catatan tersimpan: \"$content\"", "POST /api/notes")
                NoteOutcome.Queued -> ExecResult(
                    true,
                    "Catatan disimpan di ponsel, dikirim saat online: \"$content\"",
                    "Antrean lokal → POST /api/notes",
                )
                is NoteOutcome.Failed -> ExecResult(false, "Catatan gagal disimpan: ${outcome.message}", "POST /api/notes")
            }
        }
    }

    private val COMMAND_PREFIX = Regex("""^\s*(tolong\s+)?(catat(kan)?|simpan\s+catatan|bikin\s+catatan|catatan)\s*[:,-]?\s*""", RegexOption.IGNORE_CASE)
}

// ---------- draft_whatsapp ----------

object WhatsAppExecutor : SkillExecutor {
    override val functionName = "draft_whatsapp"

    override fun prepare(args: JsonObject): PreparedAction {
        // Model kecil kadang menyalin seluruh perintah ("Kirim WA ke 62... bilang ...") sebagai isi pesan.
        val message = args.requireString("message")
            .replace(COMMAND_PREFIX, "")
            .replaceFirstChar { it.uppercaseChar() }
            .ifBlank { throw InvalidArgumentsException("isi pesan kosong") }
        val phone = normalizePhone(args.optString("phone"))
        val url = buildString {
            append("https://wa.me/")
            phone?.let { append(it) }
            append("?text=")
            append(Uri.encode(message))
        }

        return PreparedAction(
            functionName = functionName,
            title = "Buka draf WhatsApp",
            details = listOf(
                "Ke" to (phone?.let { "+$it" } ?: "Pilih kontak di WhatsApp"),
                "Pesan" to message,
            ),
            icon = "message",
            mechanism = "Intent.ACTION_VIEW → wa.me",
        ) { ctx ->
            if (ctx.context.launch(Intent(Intent.ACTION_VIEW, Uri.parse(url)))) {
                ExecResult(true, "Draf WhatsApp dibuka. Pesan belum terkirim; tekan kirim sendiri di WhatsApp.", "Intent.ACTION_VIEW $url")
            } else {
                ExecResult(false, "Tidak ada aplikasi yang bisa membuka WhatsApp atau tautan wa.me.", "Intent.ACTION_VIEW $url")
            }
        }
    }

    /** "0812-3456-7890" / "+62 812..." / "812..." -> "62812..." ; nomor aneh -> null. */
    internal fun normalizePhone(raw: String?): String? {
        val digits = raw?.filter(Char::isDigit)?.takeIf { it.isNotEmpty() } ?: return null
        val normalized = when {
            digits.startsWith("62") -> digits
            digits.startsWith("0") -> "62" + digits.drop(1)
            digits.startsWith("8") -> "62$digits"
            else -> digits
        }
        return normalized.takeIf { it.length in 10..15 }
    }

    private val COMMAND_PREFIX = Regex(
        """^\s*(tolong\s+)?(kirim(in)?|bikin(in)?|buat(kan)?|tulis(kan)?)\s+(pesan\s+)?(wa|whatsapp)""" +
            """(\s+ke\s+\+?[\w.-]+)?\s*(bilang(in)?|bahwa|isinya|:)?\s*""",
        RegexOption.IGNORE_CASE,
    )
}

// ---------- open_maps ----------

object MapsExecutor : SkillExecutor {
    override val functionName = "open_maps"

    override fun prepare(args: JsonObject): PreparedAction {
        val query = args.requireString("query")
        val navigate = args.optBoolean("navigate") ?: false
        val encoded = Uri.encode(query)

        return PreparedAction(
            functionName = functionName,
            title = if (navigate) "Mulai navigasi" else "Cari di peta",
            details = listOf("Tujuan" to query),
            icon = "map",
            mechanism = if (navigate) "google.navigation:q=…" else "geo:0,0?q=…",
        ) { ctx ->
            // Urutan coba: navigasi (kalau diminta) → geo: → Google Maps versi web.
            val candidates = buildList {
                if (navigate) add("google.navigation:q=$encoded")
                add("geo:0,0?q=$encoded")
                add("https://www.google.com/maps/search/?api=1&query=$encoded")
            }
            val used = candidates.firstOrNull { ctx.context.launch(Intent(Intent.ACTION_VIEW, Uri.parse(it))) }
            if (used != null) {
                ExecResult(true, "Peta dibuka untuk \"$query\".", "Intent.ACTION_VIEW $used")
            } else {
                ExecResult(false, "Tidak ada aplikasi peta atau browser di ponsel ini.", "Intent.ACTION_VIEW geo:")
            }
        }
    }
}
