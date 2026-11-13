const router = require('express').Router();
const db = require('../db');
const { ok, asyncHandler, HttpError } = require('../lib/http');
const { requireDevice, optionalDevice } = require('../lib/auth');
const v = require('../lib/validate');

// Status aktif sebuah skill dilihat per perangkat: preferensi perangkat kalau ada,
// kalau belum pernah diubah mengikuti default katalog (skills.is_active).
const EFFECTIVE_ACTIVE = 'COALESCE(p.is_active, s.is_active)';
const JOIN_PREFS = 'LEFT JOIN device_skill_prefs p ON p.skill_slug = s.slug AND p.device_id = $1';

/** Skill aktif untuk perangkat (atau default katalog kalau deviceId null). */
async function activeSkillsFor(deviceId) {
  const { rows } = await db.query(
    `SELECT s.slug, s.function_name, s.description, s.parameters
       FROM skills s ${JOIN_PREFS}
      WHERE ${EFFECTIVE_ACTIVE} = TRUE
      ORDER BY s.id`,
    [deviceId ?? null],
  );
  return rows;
}

router.use(optionalDevice);

// GET /api/skills?category=waktu&active=true
router.get('/', asyncHandler(async (req, res) => {
  const params = [req.deviceId ?? null];
  const where = [];
  if (req.query.category) {
    params.push(String(req.query.category).slice(0, 30));
    where.push(`s.category = $${params.length}`);
  }
  if (req.query.active === 'true' || req.query.active === 'false') {
    params.push(req.query.active === 'true');
    where.push(`${EFFECTIVE_ACTIVE} = $${params.length}`);
  }
  const { rows } = await db.query(
    `SELECT s.id, s.slug, s.name, s.description, s.category, s.icon, s.function_name,
            s.requires_confirmation, ${EFFECTIVE_ACTIVE} AS is_active,
            jsonb_array_length(s.example_commands) AS example_count
       FROM skills s ${JOIN_PREFS}
      ${where.length ? 'WHERE ' + where.join(' AND ') : ''}
      ORDER BY s.category, s.name`,
    params,
  );
  ok(res, rows);
}));

// GET /api/skills/tools -> definisi fungsi skill aktif (format tools OpenAI / LiteRT-LM)
router.get('/tools', asyncHandler(async (req, res) => {
  const skills = await activeSkillsFor(req.deviceId);
  ok(res, skills.map((s) => ({
    type: 'function',
    function: { name: s.function_name, description: s.description, parameters: s.parameters },
  })));
}));

// GET /api/skills/:slug -> detail skill + statistik pemakaian perangkat ini
router.get('/:slug', asyncHandler(async (req, res) => {
  const { rows } = await db.query(
    `SELECT s.id, s.slug, s.name, s.description, s.category, s.icon, s.function_name,
            s.parameters, s.example_commands, s.android_action, s.permissions,
            s.requires_confirmation, ${EFFECTIVE_ACTIVE} AS is_active, s.created_at, s.updated_at,
            COUNT(t.id)::int                                    AS usage_count,
            COUNT(t.id) FILTER (WHERE t.status = 'success')::int AS success_count,
            MAX(t.created_at)                                   AS last_used_at
       FROM skills s ${JOIN_PREFS}
       LEFT JOIN tasks t ON t.skill_slug = s.slug AND t.device_id = $1::text
      WHERE s.slug = $2
      GROUP BY s.id, p.is_active`,
    [req.deviceId ?? null, String(req.params.slug).slice(0, 50)],
  );
  if (!rows.length) throw new HttpError(404, 'Skill tidak ditemukan');
  ok(res, rows[0]);
}));

// PATCH /api/skills/:slug  body: { "is_active": false }
// Hanya mengubah preferensi perangkat pemanggil, tidak memengaruhi pengguna lain.
router.patch('/:slug', requireDevice, asyncHandler(async (req, res) => {
  if (typeof req.body?.is_active !== 'boolean') {
    throw new HttpError(400, 'Field is_active wajib berupa boolean', 'validation');
  }
  const slug = v.requiredString(req.params.slug, 'slug', 50);
  const { rows: skill } = await db.query('SELECT slug, name FROM skills WHERE slug = $1', [slug]);
  if (!skill.length) throw new HttpError(404, 'Skill tidak ditemukan');

  const { rows } = await db.query(
    `INSERT INTO device_skill_prefs (device_id, skill_slug, is_active) VALUES ($1, $2, $3)
     ON CONFLICT (device_id, skill_slug) DO UPDATE SET is_active = EXCLUDED.is_active, updated_at = NOW()
     RETURNING is_active, updated_at`,
    [req.deviceId, slug, req.body.is_active],
  );
  ok(res, { slug, name: skill[0].name, is_active: rows[0].is_active, updated_at: rows[0].updated_at });
}));

module.exports = router;
module.exports.activeSkillsFor = activeSkillsFor;
