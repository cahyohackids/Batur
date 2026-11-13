const router = require('express').Router();
const { ok } = require('../lib/http');

// GET /api/app/version -> versi APK terbaru, dipakai aplikasi untuk menampilkan banner update.
// Nilainya diatur lewat .env supaya rilis baru tidak perlu mengubah kode.
router.get('/version', (req, res) => {
  ok(res, {
    version_code: Number.parseInt(process.env.APP_VERSION_CODE || '0', 10) || 0,
    version_name: process.env.APP_VERSION_NAME || '',
    apk_url: process.env.APP_APK_URL || 'https://github.com/cahyohackids/Batur/releases/latest',
    notes: process.env.APP_RELEASE_NOTES || '',
  });
});

module.exports = router;
