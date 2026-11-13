const router = require('express').Router();
const db = require('../db');
const { ok, asyncHandler, HttpError } = require('../lib/http');
const { callGateway } = require('../lib/gateway');

// POST /api/agent/cloud
// Body: { command: string, device_id: string }
// Response: { mode, type: "tool_call"|"text", function_call?, skill?, text?, latency_ms }
router.post('/cloud', asyncHandler(async (req, res) => {
  const { command, device_id } = req.body;
  if (!command   || typeof command    !== 'string') throw new HttpError(400, 'command wajib diisi');
  if (!device_id || typeof device_id  !== 'string') throw new HttpError(400, 'device_id wajib diisi');

  const { rows: skills } = await db.query(
    `SELECT slug, function_name, description, parameters FROM skills WHERE is_active = TRUE ORDER BY id`,
  );

  const start = Date.now();
  const result = await callGateway(command.trim(), skills);
  const latency_ms = Date.now() - start;

  ok(res, { ...result, latency_ms });
}));

module.exports = router;
