// Rate limit di memori (jendela tetap per menit/jam). Cukup untuk satu proses Node;
// kuota harian agent disimpan di database (lihat routes/agent.js) supaya tahan restart.
const { HttpError } = require('./http');

function rateLimit({ windowMs, max, key, message }) {
  const hits = new Map();

  // Bersihkan jendela yang sudah lewat supaya Map tidak tumbuh terus.
  const sweep = setInterval(() => {
    const now = Date.now();
    for (const [k, v] of hits) if (v.reset <= now) hits.delete(k);
  }, windowMs);
  sweep.unref();

  return (req, res, next) => {
    const k = key(req);
    if (!k) return next();
    const now = Date.now();
    let entry = hits.get(k);
    if (!entry || entry.reset <= now) {
      entry = { count: 0, reset: now + windowMs };
      hits.set(k, entry);
    }
    entry.count += 1;
    if (entry.count > max) {
      res.set('Retry-After', String(Math.ceil((entry.reset - now) / 1000)));
      return next(new HttpError(429, message, 'rate_limited'));
    }
    next();
  };
}

const byIp = (req) => req.ip;
const byDevice = (req) => req.deviceId;

module.exports = { rateLimit, byIp, byDevice };
