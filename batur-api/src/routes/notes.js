const router = require('express').Router();
const db = require('../db');
const { ok, asyncHandler, HttpError } = require('../lib/http');

// GET /api/notes?device_id=
router.get('/', asyncHandler(async (req, res) => {
  const { device_id } = req.query;
  if (!device_id) throw new HttpError(400, 'device_id wajib diisi');
  const { rows } = await db.query(
    `SELECT id, content, task_id, created_at FROM notes
      WHERE device_id = $1 ORDER BY created_at DESC`,
    [device_id],
  );
  ok(res, rows);
}));

// POST /api/notes
router.post('/', asyncHandler(async (req, res) => {
  const { device_id, content, task_id } = req.body;
  if (!device_id || typeof device_id !== 'string') throw new HttpError(400, 'device_id wajib diisi');
  if (!content   || typeof content   !== 'string') throw new HttpError(400, 'content wajib diisi');
  const { rows } = await db.query(
    `INSERT INTO notes (device_id, content, task_id) VALUES ($1, $2, $3) RETURNING *`,
    [device_id, content.trim(), task_id ?? null],
  );
  ok(res, rows[0], 201);
}));

// DELETE /api/notes/:id
router.delete('/:id', asyncHandler(async (req, res) => {
  const id = Number.parseInt(req.params.id, 10);
  if (Number.isNaN(id)) throw new HttpError(400, 'id harus berupa angka');
  const { rowCount } = await db.query('DELETE FROM notes WHERE id = $1', [id]);
  if (!rowCount) throw new HttpError(404, 'Catatan tidak ditemukan');
  ok(res, { id, deleted: true });
}));

module.exports = router;
