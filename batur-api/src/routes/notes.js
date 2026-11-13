const router = require('express').Router();
const db = require('../db');
const { ok, asyncHandler, HttpError } = require('../lib/http');
const { requireDevice } = require('../lib/auth');
const v = require('../lib/validate');

router.use(requireDevice);

const COLUMNS = 'id, content, task_id, created_at';

// GET /api/notes
router.get('/', asyncHandler(async (req, res) => {
  const { rows } = await db.query(
    `SELECT ${COLUMNS} FROM notes WHERE device_id = $1 ORDER BY created_at DESC LIMIT 500`,
    [req.deviceId],
  );
  ok(res, rows);
}));

// POST /api/notes  body: { content, task_id?, client_id? }
router.post('/', asyncHandler(async (req, res) => {
  const body = req.body ?? {};
  const content  = v.requiredString(body.content, 'content', 2000);
  const clientId = v.optionalUuid(body.client_id, 'client_id');
  let taskId = v.optionalInt(body.task_id, 'task_id', 1, v.INT4_MAX);

  // task_id hanya boleh menunjuk tugas milik perangkat sendiri.
  if (taskId) {
    const { rows } = await db.query('SELECT 1 FROM tasks WHERE id = $1 AND device_id = $2', [taskId, req.deviceId]);
    if (!rows.length) taskId = null;
  }

  const { rows } = await db.query(
    `INSERT INTO notes (device_id, content, task_id, client_id) VALUES ($1, $2, $3, $4)
     ON CONFLICT (device_id, client_id) DO NOTHING
     RETURNING ${COLUMNS}`,
    [req.deviceId, content, taskId, clientId],
  );
  if (rows.length) return ok(res, rows[0], 201);

  const { rows: existing } = await db.query(
    `SELECT ${COLUMNS} FROM notes WHERE device_id = $1 AND client_id = $2`,
    [req.deviceId, clientId],
  );
  ok(res, existing[0], 200);
}));

// DELETE /api/notes/:id
router.delete('/:id', asyncHandler(async (req, res) => {
  const id = v.pathId(req.params.id);
  const { rowCount } = await db.query('DELETE FROM notes WHERE id = $1 AND device_id = $2', [id, req.deviceId]);
  if (!rowCount) throw new HttpError(404, 'Catatan tidak ditemukan');
  ok(res, { id, deleted: true });
}));

module.exports = router;
