// Membuat tabel dan mengisi data awal: npm run db:init
const fs = require('fs');
const path = require('path');
const db = require('../db');

(async () => {
  try {
    for (const file of ['schema.sql', 'seed.sql']) {
      const sql = fs.readFileSync(path.join(__dirname, '../../database', file), 'utf8');
      await db.query(sql);
      console.log(`OK  ${file}`);
    }
    console.log('Database siap dipakai.');
  } catch (err) {
    console.error('Gagal inisialisasi database:', err.message);
    process.exitCode = 1;
  } finally {
    await db.pool.end();
  }
})();
