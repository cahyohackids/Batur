const router = require('express').Router();
const db = require('../db');
const { ok, asyncHandler, HttpError } = require('../lib/http');
const { optionalDevice } = require('../lib/auth');

const VALID_TYPES = new Set(['on_device', 'cloud']);

// Kolom yang boleh dilihat aplikasi. id model di gateway sengaja tidak pernah dikirim.
const PUBLIC_COLUMNS = `m.id, m.slug, m.name, m.type, m.provider, m.runtime, m.size_label, m.ram_label,
  m.supports_tools, m.description, m.is_default, m.is_active, m.download_url, m.file_name,
  m.size_bytes, m.created_at`;

// GET /api/models?type=on_device|cloud
router.get('/', asyncHandler(async (req, res) => {
  const params = [];
  let where = 'WHERE is_active = TRUE';
  if (req.query.type) {
    if (!VALID_TYPES.has(req.query.type)) throw new HttpError(400, 'type harus on_device atau cloud', 'validation');
    params.push(req.query.type);
    where += ' AND type = $1';
  }
  const { rows } = await db.query(
    `SELECT id, slug, name, type, provider, size_label, is_default, file_name, size_bytes
       FROM models ${where}
      ORDER BY is_default DESC, type DESC, name`,
    params,
  );
  ok(res, rows);
}));

// GET /api/models/:slug -> detail model + statistik pemakaian perangkat ini
router.get('/:slug', optionalDevice, asyncHandler(async (req, res) => {
  const { rows } = await db.query(
    `SELECT ${PUBLIC_COLUMNS},
            COUNT(t.id)::int                AS usage_count,
            ROUND(AVG(t.latency_ms))::int   AS avg_latency_ms
       FROM models m
       LEFT JOIN tasks t ON t.model_slug = m.slug AND t.device_id = $2::text
      WHERE m.slug = $1
      GROUP BY m.id`,
    [String(req.params.slug).slice(0, 50), req.deviceId ?? null],
  );
  if (!rows.length) throw new HttpError(404, 'Model tidak ditemukan');
  ok(res, rows[0]);
}));

module.exports = router;
