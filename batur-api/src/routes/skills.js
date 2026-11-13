const router = require('express').Router();
const db = require('../db');
const { ok, asyncHandler, HttpError } = require('../lib/http');

// GET /api/skills?category=waktu&active=true
router.get('/', asyncHandler(async (req, res) => {
  const where = [];
  const params = [];
  if (req.query.category) {
    params.push(req.query.category);
    where.push(`category = $${params.length}`);
  }
  if (req.query.active === 'true' || req.query.active === 'false') {
    params.push(req.query.active === 'true');
    where.push(`is_active = $${params.length}`);
  }
  const { rows } = await db.query(
    `SELECT id, slug, name, description, category, icon, function_name,
            requires_confirmation, is_active,
            jsonb_array_length(example_commands) AS example_count
       FROM skills
      ${where.length ? 'WHERE ' + where.join(' AND ') : ''}
      ORDER BY category, name`,
    params,
  );
  ok(res, rows);
}));

// GET /api/skills/tools -> definisi fungsi skill aktif (format tools OpenAI / LiteRT-LM)
router.get('/tools', asyncHandler(async (req, res) => {
  const { rows } = await db.query(
    `SELECT function_name, description, parameters FROM skills WHERE is_active = TRUE ORDER BY id`,
  );
  ok(res, rows.map((s) => ({
    type: 'function',
    function: { name: s.function_name, description: s.description, parameters: s.parameters },
  })));
}));

// GET /api/skills/:slug -> detail skill + statistik pemakaian
router.get('/:slug', asyncHandler(async (req, res) => {
  const { rows } = await db.query(
    `SELECT s.*,
            COUNT(t.id)::int                                    AS usage_count,
            COUNT(t.id) FILTER (WHERE t.status = 'success')::int AS success_count,
            MAX(t.created_at)                                   AS last_used_at
       FROM skills s
       LEFT JOIN tasks t ON t.skill_slug = s.slug
      WHERE s.slug = $1
      GROUP BY s.id`,
    [req.params.slug],
  );
  if (!rows.length) throw new HttpError(404, 'Skill tidak ditemukan');
  ok(res, rows[0]);
}));

// PATCH /api/skills/:slug  body: { "is_active": false }
router.patch('/:slug', asyncHandler(async (req, res) => {
  if (typeof req.body.is_active !== 'boolean') {
    throw new HttpError(400, 'Field is_active wajib berupa boolean');
  }
  const { rows } = await db.query(
    `UPDATE skills SET is_active = $1, updated_at = NOW() WHERE slug = $2
     RETURNING slug, name, is_active, updated_at`,
    [req.body.is_active, req.params.slug],
  );
  if (!rows.length) throw new HttpError(404, 'Skill tidak ditemukan');
  ok(res, rows[0]);
}));

module.exports = router;
