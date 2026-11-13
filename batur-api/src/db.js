const { Pool, types } = require('pg');
require('dotenv').config();

// BIGINT (OID 20) dikembalikan pg sebagai string; ubah ke number.
// Aman di sini karena nilai terbesar (ukuran file model) jauh di bawah 2^53.
types.setTypeParser(20, (value) => Number(value));

if (!process.env.DATABASE_URL) {
  console.error('DATABASE_URL belum diisi. Salin .env.example menjadi .env lalu isi connection string.');
  process.exit(1);
}

const needsSsl = /sslmode=require/.test(process.env.DATABASE_URL);

const pool = new Pool({
  connectionString: process.env.DATABASE_URL,
  ssl: needsSsl ? { rejectUnauthorized: false } : false,
});

module.exports = {
  query: (text, params) => pool.query(text, params),
  pool,
};
