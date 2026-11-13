const router = require('express').Router();
const { ok, asyncHandler } = require('../lib/http');
const { registerDevice } = require('../lib/auth');
const { rateLimit, byIp } = require('../lib/rateLimit');

// POST /api/devices/register -> { device_id, token }
// Dipanggil aplikasi sekali saat pertama kali dibuka (atau saat token ditolak).
router.post(
  '/register',
  rateLimit({ windowMs: 60 * 60 * 1000, max: 10, key: byIp, message: 'Terlalu banyak pendaftaran dari jaringan ini. Coba lagi nanti.' }),
  asyncHandler(async (req, res) => {
    ok(res, await registerDevice(), 201);
  }),
);

module.exports = router;
