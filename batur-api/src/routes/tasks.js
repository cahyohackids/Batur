const router = require('express').Router();
const db = require('../db');
const { ok, asyncHandler, HttpError, toInt } = require('../lib/http');

const VALID_MODES   = new Set(['on_device', 'cloud', 'fallback']);
const VALID_STATUS  = new Set(['success', 'cancelled', 'failed', 'answered']);
const VALID_STEPS   = new Set(['input', 'reasoning', 'tool_call', 'confirmation', 'execution', 'response', 'error']);

// GET /api/tasks/stats?device_id=
router.get('/stats', asyncHandler(async (req, res) => {
  const { device_id } = req.query;
  if (!device_id) throw new HttpError(400, 'device_id wajib diisi');

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
    [device_id],
  );
  ok(res, rows[0]);
}));

// GET /api/tasks?device_id=&status=&mode=&limit=20&offset=0
router.get('/', asyncHandler(async (req, res) => {
  const { device_id, status, mode } = req.query;
  if (!device_id) throw new HttpError(400, 'device_id wajib diisi');

  const limit  = toInt(req.query.limit,  20, { min: 1, max: 100 });
  const offset = toInt(req.query.offset,  0, { min: 0 });

  const where = ['device_id = $1'];
  const params = [device_id];

  if (status) {
    if (!VALID_STATUS.has(status)) throw new HttpError(400, `status tidak valid: ${status}`);
    params.push(status); where.push(`status = $${params.length}`);
  }
  if (mode) {
    if (!VALID_MODES.has(mode)) throw new HttpError(400, `mode tidak valid: ${mode}`);
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
  const id = toInt(req.params.id, NaN);
  if (Number.isNaN(id)) throw new HttpError(400, 'id harus berupa angka');

  const [{ rows: task }, { rows: steps }] = await Promise.all([
    db.query('SELECT * FROM tasks WHERE id = $1', [id]),
    db.query('SELECT type, content, created_at FROM task_steps WHERE task_id = $1 ORDER BY step_order', [id]),
  ]);
  if (!task.length) throw new HttpError(404, 'Tugas tidak ditemukan');
  ok(res, { ...task[0], steps });
}));

// POST /api/tasks — simpan tugas + langkah (dalam satu transaksi)
router.post('/', asyncHandler(async (req, res) => {
  const { device_id, command, mode, model_slug, skill_slug, function_call,
          status, result_text, latency_ms, steps } = req.body;

  if (!device_id || !command || !mode || !status) {
    throw new HttpError(400, 'Field wajib: device_id, command, mode, status');
  }
  if (!VALID_MODES.has(mode))   throw new HttpError(400, `mode tidak valid: ${mode}`);
  if (!VALID_STATUS.has(status)) throw new HttpError(400, `status tidak valid: ${status}`);

  if (Array.isArray(steps) && steps.length > 0) {
    for (const [i, s] of steps.entries()) {
      if (!VALID_STEPS.has(s.type)) throw new HttpError(400, `steps[${i}].type tidak valid: ${s.type}`);
      if (typeof s.content !== 'string' || !s.content.trim()) {
        throw new HttpError(400, `steps[${i}].content wajib berupa string non-kosong`);
      }
    }
  }

  const client = await db.pool.connect();
  try {
    await client.query('BEGIN');
    const { rows } = await client.query(
      `INSERT INTO tasks (device_id, command, mode, model_slug, skill_slug, function_call,
                          status, result_text, latency_ms)
       VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9) RETURNING *`,
      [device_id, command, mode, model_slug ?? null, skill_slug ?? null,
       function_call ? JSON.stringify(function_call) : null,
       status, result_text ?? null, latency_ms ?? null],
    );
    const taskId = rows[0].id;

    if (Array.isArray(steps) && steps.length > 0) {
      const placeholders = steps.map((_, i) => `($${i * 4 + 1}, $${i * 4 + 2}, $${i * 4 + 3}, $${i * 4 + 4})`).join(',');
      const flatParams = steps.flatMap((s, i) => [taskId, i + 1, s.type, s.content]);
      await client.query(
        `INSERT INTO task_steps (task_id, step_order, type, content) VALUES ${placeholders}`,
        flatParams,
      );
    }

    await client.query('COMMIT');
    ok(res, { ...rows[0], steps: steps ?? [] }, 201);
  } catch (err) {
    await client.query('ROLLBACK');
    throw err;
  } finally {
    client.release();
  }
}));

// DELETE /api/tasks/:id
router.delete('/:id', asyncHandler(async (req, res) => {
  const id = toInt(req.params.id, NaN);
  if (Number.isNaN(id)) throw new HttpError(400, 'id harus berupa angka');
  const { rowCount } = await db.query('DELETE FROM tasks WHERE id = $1', [id]);
  if (!rowCount) throw new HttpError(404, 'Tugas tidak ditemukan');
  ok(res, { id, deleted: true });
}));

module.exports = router;
