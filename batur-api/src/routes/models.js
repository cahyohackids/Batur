const router = require('express').Router();
const db = require('../db');
const { ok, asyncHandler, HttpError } = require('../lib/http');

// GET /api/models?type=on_device|cloud
router.get('/', asyncHandler(async (req, res) => {
  const params = [];
  let where = 'WHERE is_active = TRUE';
  if (req.query.type) {
    params.push(req.query.type);
    where += ` AND type = $1`;
  }
  const { rows } = await db.query(
    `SELECT id, slug, name, type, provider, size_label, is_default, file_name, size_bytes
       FROM models ${where}
      ORDER BY is_default DESC, type DESC, name`,
    params,
  );
  ok(res, rows);
}));

// GET /api/models/:slug -> detail model + statistik pemakaian
router.get('/:slug', asyncHandler(async (req, res) => {
  const { rows } = await db.query(
    `SELECT m.*,
            COUNT(t.id)::int                AS usage_count,
            ROUND(AVG(t.latency_ms))::int   AS avg_latency_ms
       FROM models m
       LEFT JOIN tasks t ON t.model_slug = m.slug
      WHERE m.slug = $1
      GROUP BY m.id`,
    [req.params.slug],
  );
  if (!rows.length) throw new HttpError(404, 'Model tidak ditemukan');
  ok(res, rows[0]);
}));

module.exports = router;
