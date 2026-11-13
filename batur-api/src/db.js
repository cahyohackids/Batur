const { Pool, types } = require('pg');
require('dotenv').config();

// BIGINT (OID 20) dikembalikan pg sebagai string; ubah ke number.
// Aman di sini karena nilai terbesar (ukuran file model) jauh di bawah 2^53.
types.setTypeParser(20, (value) => Number(value));

if (!process.env.DATABASE_URL) {
  console.error('DATABASE_URL belum diisi. Salin .env.example menjadi .env lalu isi connection string.');
  process.exit(1);
}

// SSL diatur lewat connection string (misalnya ?sslmode=require untuk database cloud).
const pool = new Pool({
  connectionString: process.env.DATABASE_URL,
  max: Number.parseInt(process.env.DB_POOL_MAX || '10', 10),
  connectionTimeoutMillis: 5_000,
  idleTimeoutMillis: 30_000,
});

// Koneksi idle yang putus (database restart) tidak boleh menjatuhkan proses.
pool.on('error', (err) => console.error('[db] koneksi idle error:', err.message));

module.exports = {
  query: (text, params) => pool.query(text, params),
  pool,
};
