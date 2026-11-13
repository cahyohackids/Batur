const router = require('express').Router();
const db = require('../db');
const { ok, asyncHandler, HttpError } = require('../lib/http');
const { requireDevice } = require('../lib/auth');
const { rateLimit, byDevice } = require('../lib/rateLimit');
const { callGateway } = require('../lib/gateway');
const { activeSkillsFor } = require('./skills');
const v = require('../lib/validate');

const DAILY_QUOTA = Number.parseInt(process.env.AGENT_DAILY_QUOTA || '50', 10);

/** Tambah pemakaian hari ini (WIB) dan kembalikan totalnya. */
async function countUsage(deviceId) {
  const { rows } = await db.query(
    `INSERT INTO agent_usage (device_id, day, count)
     VALUES ($1, (NOW() AT TIME ZONE 'Asia/Jakarta')::date, 1)
     ON CONFLICT (device_id, day) DO UPDATE SET count = agent_usage.count + 1
     RETURNING count`,
    [deviceId],
  );
  return rows[0].count;
}

// POST /api/agent/cloud
// Body: { command: string }
// Response: { mode, type: "tool_call"|"text", function_call?, skill?, text?, latency_ms, quota }
router.post(
  '/cloud',
  requireDevice,
  rateLimit({ windowMs: 60 * 1000, max: 15, key: byDevice, message: 'Terlalu cepat. Tunggu sebentar lalu coba lagi.' }),
  asyncHandler(async (req, res) => {
    const command = v.requiredString(req.body?.command, 'command', 500);

    const used = await countUsage(req.deviceId);
    if (used > DAILY_QUOTA) {
      throw new HttpError(
        429,
        `Kuota mode cloud hari ini (${DAILY_QUOTA} perintah) sudah habis. Coba lagi besok, atau pakai model on-device di tab Model.`,
        'quota_exceeded',
      );
    }

    const skills = await activeSkillsFor(req.deviceId);
    const start = Date.now();
    const result = await callGateway(command, skills);
    const latency_ms = Date.now() - start;

    ok(res, { ...result, latency_ms, quota: { used, limit: DAILY_QUOTA } });
  }),
);

module.exports = router;
