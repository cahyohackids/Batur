// Text-to-speech untuk membacakan jawaban Batur, memakai suara neural Indonesia dari
// layanan Read Aloud Microsoft Edge (lewat library node-edge-tts). Layanan ini bukan API
// resmi berbayar, jadi bisa berubah sewaktu-waktu: aplikasi selalu punya cadangan TTS
// bawaan Android kalau endpoint ini gagal.
//
// Hasil disimpan di disk (kunci = sha256 suara + teks) supaya kalimat yang sama, misalnya
// "Alarm 05.00 dipasang…", tidak perlu disintesis ulang.
const crypto = require('crypto');
const fs = require('fs/promises');
const path = require('path');
const { HttpError } = require('./http');

const VOICES = {
  perempuan: 'id-ID-GadisNeural',
  'laki-laki': 'id-ID-ArdiNeural',
};
const DEFAULT_VOICE = 'perempuan';
const MAX_TEXT = 1000;
const MAX_AUDIO_BYTES = 2 * 1024 * 1024;
const TIMEOUT_MS = 15_000;
const MAX_CONCURRENT = Number.parseInt(process.env.TTS_MAX_CONCURRENT || '4', 10);
const CACHE_DIR = process.env.TTS_CACHE_DIR || path.join(__dirname, '../../.cache/tts');
const CACHE_MAX_FILES = Number.parseInt(process.env.TTS_CACHE_MAX_FILES || '1000', 10);

/**
 * Rapikan teks sebelum dibacakan: buang emoji, simbol markdown, dan karakter kontrol,
 * lalu satukan spasi. Dipakai juga untuk kunci cache, jadi teks yang hanya beda spasi
 * memakai file yang sama.
 */
function normalizeText(raw) {
  return String(raw)
    .replace(/[\p{Extended_Pictographic}\p{Emoji_Modifier}\u{FE00}-\u{FE0F}\u{200D}\u{20E3}]/gu, ' ')
    .replace(/[*_`#~>|]/g, ' ')
    .replace(/[\p{Cc}\p{Cf}]/gu, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}

async function edgeEngine(text, voiceName) {
  const { EdgeTTS } = require('node-edge-tts');
  const tts = new EdgeTTS({ voice: voiceName, lang: 'id-ID', timeout: TIMEOUT_MS });
  const parts = [];
  let size = 0;
  for await (const chunk of tts.streamAudio(text)) {
    if (!Buffer.isBuffer(chunk)) continue;
    size += chunk.length;
    if (size > MAX_AUDIO_BYTES) throw new Error('Audio TTS terlalu besar');
    parts.push(chunk);
  }
  if (size === 0) throw new Error('Audio TTS kosong');
  return Buffer.concat(parts);
}

let engine = edgeEngine;
/** Untuk tes: ganti mesin sintesis supaya tidak memanggil layanan luar. */
function setEngine(fn) {
  engine = fn || edgeEngine;
}

const cacheKey = (text, voice) => crypto.createHash('sha256').update(`${voice}\n${text}`).digest('hex');
const cachePath = (key) => path.join(CACHE_DIR, `${key}.mp3`);

async function readCache(text, voice) {
  try {
    return await fs.readFile(cachePath(cacheKey(text, voice)));
  } catch {
    return null;
  }
}

let writes = 0;
async function writeCache(text, voice, audio) {
  await fs.mkdir(CACHE_DIR, { recursive: true });
  const file = cachePath(cacheKey(text, voice));
  const tmp = `${file}.${process.pid}.${Date.now()}.tmp`;
  await fs.writeFile(tmp, audio);
  await fs.rename(tmp, file);
  writes += 1;
  if (writes % 50 === 0) await pruneCache();
}

/** Simpan paling banyak CACHE_MAX_FILES file; yang paling lama dihapus. */
async function pruneCache() {
  const names = (await fs.readdir(CACHE_DIR)).filter((n) => n.endsWith('.mp3'));
  if (names.length <= CACHE_MAX_FILES) return;
  const stats = await Promise.all(
    names.map(async (n) => ({ n, t: (await fs.stat(path.join(CACHE_DIR, n)).catch(() => ({ mtimeMs: 0 }))).mtimeMs })),
  );
  stats.sort((a, b) => a.t - b.t);
  await Promise.all(stats.slice(0, stats.length - CACHE_MAX_FILES).map(({ n }) => fs.rm(path.join(CACHE_DIR, n), { force: true })));
}

let active = 0;
/** Sintesis baru (bukan dari cache). Dibatasi MAX_CONCURRENT supaya VPS tidak kewalahan. */
async function synthesize(text, voice) {
  if (active >= MAX_CONCURRENT) {
    throw new HttpError(503, 'Suara sedang sibuk, coba lagi sebentar.', 'tts_busy');
  }
  active += 1;
  try {
    const audio = await engine(text, VOICES[voice]);
    await writeCache(text, voice, audio).catch((err) => console.error('[tts] gagal menulis cache:', err.message));
    return audio;
  } catch (err) {
    if (err instanceof HttpError) throw err;
    console.error('[tts] sintesis gagal:', err.message);
    throw new HttpError(502, 'Layanan suara tidak tersedia', 'tts_unavailable');
  } finally {
    active -= 1;
  }
}

module.exports = { VOICES, DEFAULT_VOICE, MAX_TEXT, normalizeText, readCache, synthesize, setEngine, pruneCache };
