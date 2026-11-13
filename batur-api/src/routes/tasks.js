const router = require('express').Router();
const db = require('../db');
const { ok, asyncHandler, HttpError, toInt } = require('../lib/http');
const { requireDevice } = require('../lib/auth');
const v = require('../lib/validate');

const VALID_MODES   = new Set(['on_device', 'cloud', 'fallback']);
const VALID_STATUS  = new Set(['success', 'cancelled', 'failed', 'answered']);
const VALID_STEPS   = new Set(['input', 'reasoning', 'tool_call', 'confirmation', 'execution', 'response', 'error']);
const MAX_STEPS = 20;

// Semua endpoint riwayat hanya melihat data milik perangkat pemanggil.
router.use(requireDevice);

// GET /api/tasks/stats
router.get('/stats', asyncHandler(async (req, res) => {
  const { rows } = await db.query(
    `SELECT
       COUNT(*)::int                                              AS total,
       COUNT(*) FILTER (WHERE status = 'success')::int          AS success,
       COUNT(*) FILTER (WHERE mode = 'on_device')::int          AS on_device,
       COUNT(*) FILTER (WHERE mode = 'cloud')::int              AS cloud,
       COUNT(*) FILTER (WHERE mode = 'fallback')::int           AS fallback,
       ROUND(AVG(latency_ms) FILTER (WHERE latency_ms IS NOT NULL))::int AS avg_latency_ms,
       (SELECT skill_slug FROM tasks
         WHERE device_id = $1 AND skill_slug IS NOT NULL
         GROUP BY skill_slug ORDER BY COUNT(*) DESC LIMIT 1) AS top_skill
     FROM tasks WHERE device_id = $1`,
    [req.deviceId],
  );
  ok(res, rows[0]);
}));

// GET /api/tasks?status=&mode=&limit=20&offset=0
router.get('/', asyncHandler(async (req, res) => {
  const { status, mode } = req.query;
  const limit  = toInt(req.query.limit,  20, { min: 1, max: 100 });
  const offset = toInt(req.query.offset,  0, { min: 0, max: 100_000 });

  const where = ['device_id = $1'];
  const params = [req.deviceId];
  if (status) {
    v.oneOf(status, 'status', VALID_STATUS);
    params.push(status); where.push(`status = $${params.length}`);
  }
  if (mode) {
    v.oneOf(mode, 'mode', VALID_MODES);
    params.push(mode); where.push(`mode = $${params.length}`);
  }
  const whereClause = 'WHERE ' + where.join(' AND ');

  const [{ rows }, { rows: cnt }] = await Promise.all([
    db.query(
      `SELECT id, command, mode, skill_slug, status, result_text, latency_ms, created_at
         FROM tasks ${whereClause}
        ORDER BY created_at DESC
        LIMIT ${limit} OFFSET ${offset}`,
      params,
    ),
    db.query(`SELECT COUNT(*)::int AS total FROM tasks ${whereClause}`, params),
  ]);

  ok(res, rows, 200, { total: cnt[0].total, limit, offset });
}));

// GET /api/tasks/:id — detail + steps
router.get('/:id', asyncHandler(async (req, res) => {
  const id = v.pathId(req.params.id);
  const { rows: task } = await db.query(
    `SELECT id, command, mode, model_slug, skill_slug, function_call, status,
            result_text, latency_ms, created_at
       FROM tasks WHERE id = $1 AND device_id = $2`,
    [id, req.deviceId],
  );
  if (!task.length) throw new HttpError(404, 'Tugas tidak ditemukan');
  const { rows: steps } = await db.query(
    'SELECT type, content, created_at FROM task_steps WHERE task_id = $1 ORDER BY step_order',
    [id],
  );
  ok(res, { ...task[0], steps });
}));

function parseTaskBody(body) {
  if (!body || typeof body !== 'object') throw new HttpError(400, 'Body harus JSON', 'validation');
  const steps = body.steps ?? [];
  if (!Array.isArray(steps)) throw new HttpError(400, 'steps harus berupa array', 'validation');
  if (steps.length > MAX_STEPS) throw new HttpError(400, `steps maksimal ${MAX_STEPS}`, 'validation');
  const parsedSteps = steps.map((s, i) => {
    if (!s || typeof s !== 'object') throw new HttpError(400, `steps[${i}] tidak valid`, 'validation');
    v.oneOf(s.type, `steps[${i}].type`, VALID_STEPS);
    return { type: s.type, content: v.requiredString(s.content, `steps[${i}].content`, 2000) };
  });

  const fc = body.function_call;
  if (fc !== undefined && fc !== null && (typeof fc !== 'object' || Array.isArray(fc))) {
    throw new HttpError(400, 'function_call harus berupa objek', 'validation');
  }
  const fcJson = fc ? JSON.stringify(fc) : null;
  if (fcJson && fcJson.length > 4000) throw new HttpError(400, 'function_call terlalu besar', 'validation');

  return {
    command:     v.requiredString(body.command, 'command', 500),
    mode:        v.oneOf(body.mode, 'mode', VALID_MODES),
    status:      v.oneOf(body.status, 'status', VALID_STATUS),
    model_slug:  v.optionalString(body.model_slug, 'model_slug', 50),
    skill_slug:  v.optionalString(body.skill_slug, 'skill_slug', 50),
    result_text: v.optionalString(body.result_text, 'result_text', 4000),
    latency_ms:  v.optionalInt(body.latency_ms, 'latency_ms', 0, 10_000_000),
    client_id:   v.optionalUuid(body.client_id, 'client_id'),
    function_call: fcJson,
    steps: parsedSteps,
  };
}

// POST /api/tasks — simpan tugas + langkah (dalam satu transaksi).
// Kalau client_id sudah pernah disimpan perangkat ini, data lama dikembalikan (200), tidak dobel.
router.post('/', asyncHandler(async (req, res) => {
  const t = parseTaskBody(req.body);

  if (t.client_id) {
    const existing = await findByClientId(req.deviceId, t.client_id);
    if (existing) return ok(res, existing, 200);
  }

  // Slug yang tidak dikenal ditolak dengan 400 (bukan error foreign key 500).
  const [{ rows: skillOk }, { rows: modelOk }] = await Promise.all([
    t.skill_slug ? db.query('SELECT 1 FROM skills WHERE slug = $1', [t.skill_slug]) : { rows: [1] },
    t.model_slug ? db.query('SELECT 1 FROM models WHERE slug = $1', [t.model_slug]) : { rows: [1] },
  ]);
  if (!skillOk.length) throw new HttpError(400, `skill_slug tidak dikenal: ${t.skill_slug}`, 'validation');
  if (!modelOk.length) throw new HttpError(400, `model_slug tidak dikenal: ${t.model_slug}`, 'validation');

  const client = await db.pool.connect();
  let clientError;
  try {
    await client.query('BEGIN');
    const { rows } = await client.query(
      `INSERT INTO tasks (device_id, command, mode, model_slug, skill_slug, function_call,
                          status, result_text, latency_ms, client_id)
       VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10)
       ON CONFLICT (device_id, client_id) DO NOTHING
       RETURNING id, command, mode, model_slug, skill_slug, function_call, status,
                 result_text, latency_ms, created_at`,
      [req.deviceId, t.command, t.mode, t.model_slug, t.skill_slug, t.function_call,
       t.status, t.result_text, t.latency_ms, t.client_id],
    );

    if (!rows.length) {
      // Kiriman ganda yang datang bersamaan: baris lain sudah menang.
      await client.query('ROLLBACK');
      return ok(res, await findByClientId(req.deviceId, t.client_id), 200);
    }

    const taskId = rows[0].id;
    if (t.steps.length) {
      const placeholders = t.steps.map((_, i) => `($${i * 4 + 1}, $${i * 4 + 2}, $${i * 4 + 3}, $${i * 4 + 4})`).join(',');
      const flat = t.steps.flatMap((s, i) => [taskId, i + 1, s.type, s.content]);
      await client.query(
        `INSERT INTO task_steps (task_id, step_order, type, content) VALUES ${placeholders}`,
        flat,
      );
    }
    await client.query('COMMIT');
    ok(res, { ...rows[0], steps: t.steps }, 201);
  } catch (err) {
    clientError = err;
    await client.query('ROLLBACK').catch(() => {});
    throw err;
  } finally {
    // Klien yang rusak dibuang dari pool, bukan dipakai ulang.
    client.release(clientError?.code?.startsWith?.('08') ? clientError : undefined);
  }
}));

async function findByClientId(deviceId, clientId) {
  const { rows } = await db.query(
    `SELECT id, command, mode, model_slug, skill_slug, function_call, status,
            result_text, latency_ms, created_at
       FROM tasks WHERE device_id = $1 AND client_id = $2`,
    [deviceId, clientId],
  );
  if (!rows.length) return null;
  const { rows: steps } = await db.query(
    'SELECT type, content FROM task_steps WHERE task_id = $1 ORDER BY step_order',
    [rows[0].id],
  );
  return { ...rows[0], steps };
}

// DELETE /api/tasks/:id
router.delete('/:id', asyncHandler(async (req, res) => {
  const id = v.pathId(req.params.id);
  const { rowCount } = await db.query('DELETE FROM tasks WHERE id = $1 AND device_id = $2', [id, req.deviceId]);
  if (!rowCount) throw new HttpError(404, 'Tugas tidak ditemukan');
  ok(res, { id, deleted: true });
}));

module.exports = router;
