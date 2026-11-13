require('dotenv').config();
const express = require('express');
const db = require('./db');
const { rateLimit, byIp } = require('./lib/rateLimit');

const app = express();

// Berjalan di belakang Nginx (yang sudah memasang IP asli dari Cloudflare),
// jadi req.ip diambil dari X-Forwarded-For satu hop.
app.set('trust proxy', 1);
app.disable('x-powered-by');

// Log ringkas tiap request (tanpa body dan tanpa header Authorization).
app.use((req, res, next) => {
  const start = Date.now();
  res.on('finish', () => {
    console.log(`${new Date().toISOString()} ${req.ip} ${req.method} ${req.originalUrl.split('?')[0]} ${res.statusCode} ${Date.now() - start}ms`);
  });
  next();
});

app.use(express.json({ limit: '16kb' }));
app.use('/api', rateLimit({ windowMs: 60 * 1000, max: 120, key: byIp, message: 'Terlalu banyak request. Tunggu sebentar.' }));

// Health check
app.get('/api/health', async (req, res) => {
  try {
    const { rows } = await db.query('SELECT NOW() AS time');
    res.json({ success: true, data: { status: 'ok', db_time: rows[0].time } });
  } catch (err) {
    res.status(503).json({ success: false, message: 'Database tidak terhubung' });
  }
});

app.use('/api/devices', require('./routes/devices'));
app.use('/api/app',     require('./routes/app'));
app.use('/api/skills',  require('./routes/skills'));
app.use('/api/models',  require('./routes/models'));
app.use('/api/tasks',   require('./routes/tasks'));
app.use('/api/notes',   require('./routes/notes'));
app.use('/api/agent',   require('./routes/agent'));
app.use('/api/tts',     require('./routes/tts'));

// 404
app.use((req, res) => {
  res.status(404).json({ success: false, message: `Endpoint ${req.method} ${req.path} tidak ditemukan` });
});

// Error handler
app.use((err, req, res, _next) => {
  let status = err.status || err.statusCode || 500;
  let message = err.message;
  let code = err.code && typeof err.code === 'string' && !/^\d/.test(err.code) ? err.code : undefined;

  if (err.type === 'entity.parse.failed') {
    status = 400; message = 'Body harus JSON yang valid'; code = 'validation';
  } else if (err.type === 'entity.too.large') {
    status = 413; message = 'Body terlalu besar'; code = 'validation';
  } else if (typeof err.code === 'string' && /^2[23]/.test(err.code)) {
    // Kode PostgreSQL 22xxx (data tidak valid) dan 23xxx (melanggar constraint) = salah input.
    status = 400; message = 'Data tidak valid'; code = 'validation';
  }

  if (status >= 500) console.error('[error]', err);
  res.status(status).json({
    success: false,
    message: status < 500 ? message : 'Terjadi kesalahan di server',
    ...(code && status < 500 ? { code } : {}),
  });
});

if (require.main === module) {
  const PORT = Number(process.env.PORT) || 3000;
  const HOST = process.env.HOST || '127.0.0.1';
  const server = app.listen(PORT, HOST, () => console.log(`Batur API berjalan di http://${HOST}:${PORT}`));

  // PM2/systemd mengirim SIGTERM saat restart: selesaikan request yang sedang jalan dulu.
  const shutdown = (signal) => {
    console.log(`${signal} diterima, mematikan server…`);
    server.close(() => db.pool.end().finally(() => process.exit(0)));
    setTimeout(() => process.exit(1), 10_000).unref();
  };
  process.on('SIGTERM', () => shutdown('SIGTERM'));
  process.on('SIGINT', () => shutdown('SIGINT'));
}

module.exports = app;
