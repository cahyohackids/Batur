const express = require('express');
const cors = require('cors');
require('dotenv').config();

const app = express();

app.use(cors());
app.use(express.json({ limit: '100kb' }));

// Health check
const db = require('./db');
app.get('/api/health', async (req, res) => {
  try {
    const { rows } = await db.query('SELECT NOW() AS time');
    res.json({ success: true, data: { status: 'ok', db_time: rows[0].time } });
  } catch (err) {
    res.status(503).json({ success: false, message: 'Database tidak terhubung' });
  }
});

app.use('/api/skills', require('./routes/skills'));
app.use('/api/models', require('./routes/models'));
app.use('/api/tasks',  require('./routes/tasks'));
app.use('/api/notes',  require('./routes/notes'));
app.use('/api/agent',  require('./routes/agent'));

// 404
app.use((req, res) => {
  res.status(404).json({ success: false, message: `Endpoint ${req.method} ${req.path} tidak ditemukan` });
});

// Error handler
app.use((err, req, res, _next) => {
  const status = err.status || 500;
  if (status >= 500) console.error('[error]', err);
  res.status(status).json({
    success: false,
    message: status < 500 ? err.message : 'Terjadi kesalahan di server',
  });
});

const PORT = Number(process.env.PORT) || 3000;
app.listen(PORT, () => console.log(`Batur API berjalan di http://localhost:${PORT}`));
