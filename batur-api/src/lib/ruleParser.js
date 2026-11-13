// Fallback rule-based parser untuk perintah bahasa Indonesia.
// Dipakai saat gateway AI tidak dikonfigurasi atau timeout.
// Returns: { type, function_call?, skill?, text?, mode: 'fallback' }

const HOUR_SUFFIX = {
  subuh: 4, pagi: 7, siang: 12, sore: 15, malam: 19, tengah: 0,
};

const MONTHS_ID = {
  januari: 1, februari: 2, maret: 3, april: 4, mei: 5, juni: 6,
  juli: 7, agustus: 8, september: 9, oktober: 10, november: 11, desember: 12,
};

function normalizeHour(h, suffix) {
  if (!suffix) return h;
  const base = HOUR_SUFFIX[suffix] ?? 0;
  if (suffix === 'pagi' || suffix === 'subuh') return h < 12 ? h : h - 12;
  if (suffix === 'siang') return h === 12 ? 12 : (h < 12 ? h + 12 : h);
  if (suffix === 'sore')  return h < 12 ? h + 12 : h;
  // "jam 12 malam" = 00.00, "jam 1/2/3 malam" = dini hari, "jam 7 malam" = 19.00
  if (suffix === 'malam') return h === 12 ? 0 : (h >= 1 && h <= 3 ? h : (h < 12 ? h + 12 : h));
  if (suffix === 'tengah malam') return h === 12 ? 0 : h;
  return h;
}

function parseTime(text) {
  // "jam setengah 6 pagi" = 05.30 (cara umum orang Indonesia menyebut jam X.30)
  const half = text.match(/(?:jam|pukul)?\s*setengah\s+(\d{1,2})\s*(subuh|pagi|siang|sore|tengah\s+malam|malam)?/i);
  if (half) {
    const hRaw = Number.parseInt(half[1], 10) - 1;
    const hour = normalizeHour(hRaw < 0 ? 23 : hRaw, (half[2] ?? '').toLowerCase().replace(/\s+/g, ' ') || null);
    if (hour >= 0 && hour <= 23) return { hour, minute: 30 };
  }

  // "jam 05.30 pagi", "jam 5 subuh", "pukul 06:30", "5:30 sore", "05.30"
  const m = text.match(
    /(?:jam|pukul)\s+(\d{1,2})(?:[.:](\d{2}))?\s*(subuh|pagi|siang|sore|tengah\s+malam|malam)?|(\d{1,2})[.:](\d{2})\s*(subuh|pagi|siang|sore|tengah\s+malam|malam)?/i,
  );
  if (!m) return null;
  const hRaw  = Number.parseInt(m[1] ?? m[4], 10);
  const min   = Number.parseInt(m[2] ?? m[5] ?? '0', 10);
  const sfx   = (m[3] ?? m[6] ?? '').toLowerCase().replace(/\s+/g, ' ');
  const hour  = normalizeHour(hRaw, sfx || null);
  if (hour < 0 || hour > 23 || min < 0 || min > 59) return null;
  return { hour, minute: min };
}

function getJakartaDate() {
  return new Date(new Date().toLocaleString('en-US', { timeZone: 'Asia/Jakarta' }));
}

function parseDate(text) {
  const lower = text.toLowerCase();
  const now = getJakartaDate();
  // "jam 12 tengah malam" tanpa tanggal = pergantian hari berikutnya.
  const hasExplicitDate = /\b(besok|lusa|tanggal)\b/.test(lower)
    || Object.keys(MONTHS_ID).some((m) => lower.includes(m));
  if (/\btengah malam\b/.test(lower) && !hasExplicitDate) {
    const d = new Date(now); d.setDate(d.getDate() + 1); return d;
  }
  if (/\bbesok\b/.test(lower)) {
    const d = new Date(now); d.setDate(d.getDate() + 1); return d;
  }
  if (/\blusa\b/.test(lower)) {
    const d = new Date(now); d.setDate(d.getDate() + 2); return d;
  }
  // "tanggal 15 desember" or "15 desember" or "tanggal 15"
  const dateM = lower.match(/(?:tanggal\s+)?(\d{1,2})\s+(januari|februari|maret|april|mei|juni|juli|agustus|september|oktober|november|desember)/);
  if (dateM) {
    const day = Number.parseInt(dateM[1], 10);
    const month = MONTHS_ID[dateM[2]] - 1;
    let d = new Date(now.getFullYear(), month, day);
    if (d < startOfDay(now)) d = new Date(now.getFullYear() + 1, month, day);
    // "31 februari" tidak boleh bergeser jadi Maret.
    if (d.getMonth() !== month || d.getDate() !== day) return null;
    return d;
  }
  const numDateM = lower.match(/tanggal\s+(\d{1,2})/);
  if (numDateM) {
    const day = Number.parseInt(numDateM[1], 10);
    let d = new Date(now.getFullYear(), now.getMonth(), day);
    if (d < startOfDay(now)) d = new Date(now.getFullYear(), now.getMonth() + 1, day);
    if (day < 1 || d.getDate() !== day) return null;
    return d;
  }
  return now;
}

function startOfDay(d) {
  return new Date(d.getFullYear(), d.getMonth(), d.getDate());
}

// "Jadwalkan rapat himpunan besok jam 9 pagi di aula" -> "Rapat himpunan"
function extractEventTitle(command) {
  let t = command
    .replace(/^\s*(?:tolong\s+)?(?:jadwalkan|jadwal|buat(?:kan)?|tambah(?:kan)?|bikin(?:in)?|set|ingatkan(?:\s+aku)?|catat\s+jadwal)\s+/i, '')
    .replace(/^(?:acara|event|agenda|jadwal)\s+/i, '')
    .split(/\s+(?:besok|lusa|hari\s+ini|tanggal|pada|jam|pukul|di)\b|\s+\d{1,2}\s+(?:januari|februari|maret|april|mei|juni|juli|agustus|september|oktober|november|desember)\b/i)[0]
    .trim();
  if (!t) t = 'Acara';
  return t.charAt(0).toUpperCase() + t.slice(1);
}

function pad2(n) { return String(n).padStart(2, '0'); }

function formatDate(d) {
  return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}`;
}

function parse(command) {
  const lower = command.toLowerCase();

  // ── 1. Alarm ────────────────────────────────────────────────────────────────
  if (/\b(alarm|bangun(?:in|kan)?|wakeup)\b/.test(lower)) {
    const time = parseTime(command);
    if (time) {
      return {
        type: 'tool_call',
        skill: 'set-alarm',
        function_call: { name: 'set_alarm', arguments: time },
        mode: 'fallback',
      };
    }
  }

  // ── 2. Timer ────────────────────────────────────────────────────────────────
  if (/\b(timer|hitung\s*mundur)\b/.test(lower)) {
    const mM = lower.match(/(\d+)\s*menit/);
    const sM = lower.match(/(\d+)\s*detik/);
    const jM = lower.match(/(\d+)\s*jam/);
    let seconds = 0;
    if (jM) seconds += Number.parseInt(jM[1], 10) * 3600;
    if (mM) seconds += Number.parseInt(mM[1], 10) * 60;
    if (sM) seconds += Number.parseInt(sM[1], 10);
    if (seconds > 86400) {
      return { type: 'text', text: 'Timer maksimal 24 jam. Untuk waktu yang lebih lama, pakai alarm atau jadwal.', mode: 'fallback' };
    }
    if (seconds > 0) {
      return {
        type: 'tool_call',
        skill: 'set-timer',
        function_call: { name: 'set_timer', arguments: { seconds } },
        mode: 'fallback',
      };
    }
  }

  // ── 3. Catatan eksplisit ("Catat: ...") ─────────────────────────────────────
  const explicitNote = command.match(/^\s*(?:catat(?:an)?|simpan\s+catatan|tulis\s+catatan)\s*:\s*(.+)/i);
  if (explicitNote && explicitNote[1].trim()) {
    return {
      type: 'tool_call',
      skill: 'save-note',
      function_call: { name: 'save_note', arguments: { content: explicitNote[1].trim() } },
      mode: 'fallback',
    };
  }

  // ── 4. WhatsApp ─────────────────────────────────────────────────────────────
  const waM = command.match(/\b(?:wa|whatsapp)\b.*?(?:ke|to)\s+([\d+]+).*?(?:bilang|kirim|katakan|tulis)\s+(.+)/i)
           ?? command.match(/\b(?:wa|whatsapp)\b.*?(?:bilang|kirim|tulis|katakan)\s+(.+)/i)
           ?? command.match(/^\s*(?:tolong\s+)?(?:tulis|kirim|bikin(?:in)?|buat(?:kan|in)?)\s+(?:pesan\s+)?(?:wa|whatsapp)\b\s*[:\-]?\s*(.+)/i);
  if (waM) {
    // Pola pertama punya dua grup (nomor, pesan); pola lain hanya pesan.
    const hasPhone = waM[2] !== undefined;
    const phone   = hasPhone ? waM[1].replace(/^0/, '62') : undefined;
    const message = (hasPhone ? waM[2] : waM[1]).trim();
    return {
      type: 'tool_call',
      skill: 'draft-whatsapp',
      function_call: { name: 'draft_whatsapp', arguments: { message, ...(phone ? { phone } : {}) } },
      mode: 'fallback',
    };
  }

  // ── 5. Maps / Navigasi ──────────────────────────────────────────────────────
  const mapsM = command.match(/\b(?:rute|navigasi|arah|cari\s+jalan|buka\s+maps?)\s+(?:ke|menuju)?\s*(.+)/i)
             ?? command.match(/\b(?:lokasi|temukan)\s+(.+)/i);
  if (mapsM && mapsM[1].trim()) {
    const navigate = /navigasi|rute|arah/.test(lower);
    return {
      type: 'tool_call',
      skill: 'open-maps',
      function_call: { name: 'open_maps', arguments: { query: mapsM[1].trim(), navigate } },
      mode: 'fallback',
    };
  }

  // ── 6. Jadwal / Kalender ────────────────────────────────────────────────────
  if (/\b(jadwal\w*|agenda|event|acara|meeting|rapat|kuliah|ujian|ingatkan|reminder)\b/.test(lower)) {
    const timeInfo = parseTime(command);
    const date     = parseDate(command);
    const title    = extractEventTitle(command);
    if (timeInfo && !date) {
      return { type: 'text', text: 'Tanggalnya tidak valid. Coba sebut tanggal yang benar, misalnya "15 Desember".', mode: 'fallback' };
    }
    if (timeInfo) {
      return {
        type: 'tool_call',
        skill: 'create-event',
        function_call: {
          name: 'create_calendar_event',
          arguments: {
            title,
            date: formatDate(date),
            start_time: `${pad2(timeInfo.hour)}:${pad2(timeInfo.minute)}`,
          },
        },
        mode: 'fallback',
      };
    }
  }

  // ── 7. Catatan ──────────────────────────────────────────────────────────────
  const noteM = command.match(/^\s*(?:tolong\s+)?(?:catat(?:kan)?|simpan\s+catatan|tulis(?:\s+catatan)?)\b\s*[:\-]?\s*(.+)/i);
  if (noteM && noteM[1].trim()) {
    return {
      type: 'tool_call',
      skill: 'save-note',
      function_call: { name: 'save_note', arguments: { content: noteM[1].trim() } },
      mode: 'fallback',
    };
  }

  return {
    type: 'text',
    text: 'Maaf, Batur belum paham perintah itu. Coba lebih spesifik, misalnya "Pasang alarm jam 6 pagi" atau "Catat: beli susu".',
    mode: 'fallback',
  };
}

module.exports = { parse };
