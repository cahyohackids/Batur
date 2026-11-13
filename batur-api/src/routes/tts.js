const router = require('express').Router();
const db = require('../db');
const { asyncHandler, HttpError } = require('../lib/http');
const { requireDevice } = require('../lib/auth');
const { rateLimit, byDevice } = require('../lib/rateLimit');
const tts = require('../lib/tts');
const v = require('../lib/validate');

const DAILY_QUOTA = Number.parseInt(process.env.TTS_DAILY_QUOTA || '200', 10);
const VOICE_KEYS = new Set(Object.keys(tts.VOICES));

/** Tambah pemakaian TTS hari ini (WIB). Hanya dihitung saat benar-benar menyintesis. */
async function countUsage(deviceId) {
  const { rows } = await db.query(
    `INSERT INTO tts_usage (device_id, day, count)
     VALUES ($1, (NOW() AT TIME ZONE 'Asia/Jakarta')::date, 1)
     ON CONFLICT (device_id, day) DO UPDATE SET count = tts_usage.count + 1
     RETURNING count`,
    [deviceId],
  );
  return rows[0].count;
}

// POST /api/tts
// Body: { text: string (maks 1000), voice?: "perempuan" | "laki-laki" }
// Response: audio/mpeg (mp3 24 kHz mono). Error tetap JSON { success:false, message, code }.
router.post(
  '/',
  requireDevice,
  rateLimit({ windowMs: 60 * 1000, max: 20, key: byDevice, message: 'Terlalu cepat. Tunggu sebentar lalu coba lagi.' }),
  asyncHandler(async (req, res) => {
    if (process.env.TTS_ENABLED === 'false') {
      throw new HttpError(503, 'Suara cloud sedang dimatikan', 'tts_disabled');
    }
    const raw = v.requiredString(req.body?.text, 'text', tts.MAX_TEXT);
    const voice = req.body?.voice == null ? tts.DEFAULT_VOICE : v.oneOf(req.body.voice, 'voice', VOICE_KEYS);
    const text = tts.normalizeText(raw);
    if (!text) throw new HttpError(400, 'text tidak berisi kata yang bisa dibacakan', 'validation');

    let audio = await tts.readCache(text, voice);
    const cached = Boolean(audio);
    if (!audio) {
      const used = await countUsage(req.deviceId);
      if (used > DAILY_QUOTA) {
        throw new HttpError(429, `Kuota suara hari ini (${DAILY_QUOTA}) sudah habis.`, 'quota_exceeded');
      }
      audio = await tts.synthesize(text, voice);
    }

    res.set({
      'Content-Type': 'audio/mpeg',
      'Content-Length': String(audio.length),
      'Cache-Control': 'private, max-age=86400',
      'X-TTS-Cache': cached ? 'hit' : 'miss',
    });
    res.end(audio);
  }),
);

module.exports = router;
