// Identitas perangkat. Setiap HP mendaftar sekali (POST /api/devices/register)
// dan menerima token acak. Server hanya menyimpan hash SHA-256-nya.
// Semua data pribadi (riwayat, catatan, preferensi skill) dibatasi ke perangkat pemilik token.
const crypto = require('crypto');
const db = require('../db');
const { HttpError } = require('./http');

const hashToken = (token) => crypto.createHash('sha256').update(token).digest('hex');

async function registerDevice() {
  const token = crypto.randomBytes(32).toString('base64url');
  const { rows } = await db.query(
    'INSERT INTO devices (token_hash) VALUES ($1) RETURNING id',
    [hashToken(token)],
  );
  return { device_id: rows[0].id, token };
}

function bearer(req) {
  const header = req.get('authorization') || '';
  const m = header.match(/^Bearer\s+([A-Za-z0-9_-]{20,100})$/);
  return m ? m[1] : null;
}

async function lookup(token) {
  const { rows } = await db.query(
    `UPDATE devices SET last_seen_at = NOW()
      WHERE token_hash = $1
      RETURNING id`,
    [hashToken(token)],
  );
  return rows[0]?.id ?? null;
}

const unauthorized = () =>
  new HttpError(401, 'Perangkat belum terdaftar. Buka ulang aplikasi Batur.', 'unauthorized');

/** Wajib token: mengisi req.deviceId atau menolak dengan 401. */
async function requireDevice(req, _res, next) {
  try {
    const token = bearer(req);
    if (!token) throw unauthorized();
    const id = await lookup(token);
    if (!id) throw unauthorized();
    req.deviceId = id;
    next();
  } catch (err) {
    next(err);
  }
}

/** Token opsional: kalau ada dan valid, req.deviceId terisi; kalau tidak, lanjut tanpa perangkat. */
async function optionalDevice(req, _res, next) {
  try {
    const token = bearer(req);
    if (token) req.deviceId = await lookup(token);
    next();
  } catch (err) {
    next(err);
  }
}

module.exports = { registerDevice, requireDevice, optionalDevice, hashToken };
