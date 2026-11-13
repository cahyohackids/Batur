// Uji endpoint terhadap database uji sungguhan.
// Jalankan dengan TEST_DATABASE_URL, misalnya:
//   TEST_DATABASE_URL=postgresql://user@localhost:5432/batur_test npm test
// Tanpa variabel itu, tes ini dilewati (tes parser tetap jalan).
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('fs');
const path = require('path');

const TEST_DB = process.env.TEST_DATABASE_URL;

test('API', { skip: !TEST_DB && 'TEST_DATABASE_URL tidak diisi' }, async (t) => {
  process.env.DATABASE_URL = TEST_DB;
  process.env.GATEWAY_BASE_URL = '';   // pakai rule parser, tanpa memanggil gateway berbayar
  process.env.GATEWAY_MODEL = '';
  process.env.AGENT_DAILY_QUOTA = '3';
  process.env.APP_VERSION_CODE = '2';
  process.env.APP_VERSION_NAME = '1.1.0';
  process.env.TTS_DAILY_QUOTA = '2';
  process.env.TTS_CACHE_DIR = fs.mkdtempSync(path.join(require('os').tmpdir(), 'batur-tts-'));

  const db = require('../src/db');
  for (const file of ['schema.sql', 'seed.sql']) {
    await db.query(fs.readFileSync(path.join(__dirname, '../database', file), 'utf8'));
  }
  const app = require('../src/server');
  const server = app.listen(0, '127.0.0.1');
  await new Promise((r) => server.once('listening', r));
  const base = `http://127.0.0.1:${server.address().port}/api`;

  t.after(async () => {
    server.close();
    await db.pool.end();
  });

  const call = async (method, url, { token, body } = {}) => {
    const res = await fetch(base + url, {
      method,
      headers: {
        ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}),
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: body !== undefined ? (typeof body === 'string' ? body : JSON.stringify(body)) : undefined,
    });
    return { status: res.status, json: await res.json() };
  };

  const register = async () => {
    const r = await call('POST', '/devices/register', { body: {} });
    assert.equal(r.status, 201);
    assert.match(r.json.data.device_id, /^[0-9a-f-]{36}$/);
    return r.json.data;
  };

  const a = await register();
  const b = await register();

  const task = (extra = {}) => ({
    command: 'Pasang alarm jam 6 pagi', mode: 'cloud', status: 'success',
    skill_slug: 'set-alarm', function_call: { name: 'set_alarm', arguments: { hour: 6, minute: 0 } },
    steps: [{ type: 'input', content: 'Pasang alarm jam 6 pagi' }], ...extra,
  });

  await t.test('tanpa token ditolak 401', async () => {
    assert.equal((await call('GET', '/tasks')).status, 401);
    assert.equal((await call('GET', '/tasks', { token: 'x'.repeat(43) })).status, 401);
    assert.equal((await call('POST', '/agent/cloud', { body: { command: 'halo' } })).status, 401);
  });

  await t.test('riwayat hanya bisa dilihat dan dihapus pemiliknya', async () => {
    const created = await call('POST', '/tasks', { token: a.token, body: task() });
    assert.equal(created.status, 201);
    const id = created.json.data.id;

    assert.equal((await call('GET', `/tasks/${id}`, { token: a.token })).status, 200);
    assert.equal((await call('GET', `/tasks/${id}`, { token: b.token })).status, 404);
    assert.equal((await call('DELETE', `/tasks/${id}`, { token: b.token })).status, 404);
    assert.equal((await call('GET', '/tasks', { token: b.token })).json.meta.total, 0);
    assert.equal((await call('DELETE', `/tasks/${id}`, { token: a.token })).status, 200);
  });

  await t.test('client_id membuat kiriman ulang tidak dobel', async () => {
    const client_id = '3f2b8c1e-6a2d-4b7e-9c11-0a1b2c3d4e5f';
    const first = await call('POST', '/tasks', { token: a.token, body: task({ client_id }) });
    const again = await call('POST', '/tasks', { token: a.token, body: task({ client_id }) });
    assert.equal(first.status, 201);
    assert.equal(again.status, 200);
    assert.equal(again.json.data.id, first.json.data.id);
    // Perangkat lain boleh memakai client_id yang sama tanpa bentrok.
    assert.equal((await call('POST', '/tasks', { token: b.token, body: task({ client_id }) })).status, 201);
  });

  await t.test('input tidak valid dijawab 400, bukan 500', async () => {
    const cases = [
      task({ skill_slug: 'tidak-ada' }),
      task({ latency_ms: -1 }),
      task({ latency_ms: 'abc' }),
      task({ steps: [null] }),
      task({ command: '   ' }),
      task({ command: 'x'.repeat(501) }),
      task({ client_id: 'bukan-uuid' }),
    ];
    for (const body of cases) {
      const r = await call('POST', '/tasks', { token: a.token, body });
      assert.equal(r.status, 400, JSON.stringify(body).slice(0, 80));
    }
    assert.equal((await call('POST', '/tasks', { token: a.token, body: '{rusak' })).status, 400);
    assert.equal((await call('GET', '/tasks/99999999999', { token: a.token })).status, 404);
    assert.equal((await call('GET', '/tasks/abc', { token: a.token })).status, 400);
  });

  await t.test('catatan dibatasi per perangkat dan idempoten', async () => {
    const client_id = '9a7b6c5d-4e3f-4a2b-8c1d-0e9f8a7b6c5d';
    const n1 = await call('POST', '/notes', { token: a.token, body: { content: 'Beli galon', client_id } });
    const n2 = await call('POST', '/notes', { token: a.token, body: { content: 'Beli galon', client_id } });
    assert.equal(n1.status, 201);
    assert.equal(n2.json.data.id, n1.json.data.id);
    assert.equal((await call('GET', '/notes', { token: b.token })).json.data.length, 0);
    assert.equal((await call('DELETE', `/notes/${n1.json.data.id}`, { token: b.token })).status, 404);
    assert.equal((await call('POST', '/notes', { token: a.token, body: { content: '  ' } })).status, 400);
  });

  await t.test('skill nonaktif hanya berlaku untuk perangkat itu', async () => {
    const off = await call('PATCH', '/skills/save-note', { token: a.token, body: { is_active: false } });
    assert.equal(off.status, 200);
    assert.equal(off.json.data.is_active, false);
    const isActive = async (token) => (await call('GET', '/skills', { token })).json.data.find((s) => s.slug === 'save-note').is_active;
    assert.equal(await isActive(a.token), false);
    assert.equal(await isActive(b.token), true);
    assert.equal(await isActive(undefined), true);
    const toolsA = (await call('GET', '/skills/tools', { token: a.token })).json.data.map((x) => x.function.name);
    assert.ok(!toolsA.includes('save_note'));
    // Rule parser tidak boleh memakai skill yang dinonaktifkan.
    const r = await call('POST', '/agent/cloud', { token: a.token, body: { command: 'Catat: beli susu' } });
    assert.equal(r.json.data.type, 'text');
    assert.equal((await call('PATCH', '/skills/save-note', { body: { is_active: false } })).status, 401);
  });

  await t.test('agent: validasi panjang dan kuota harian', async () => {
    assert.equal((await call('POST', '/agent/cloud', { token: b.token, body: { command: 'x'.repeat(501) } })).status, 400);
    assert.equal((await call('POST', '/agent/cloud', { token: b.token, body: { command: '   ' } })).status, 400);
    for (let i = 0; i < 3; i++) {
      const r = await call('POST', '/agent/cloud', { token: b.token, body: { command: 'Pasang alarm jam 6 pagi' } });
      assert.equal(r.status, 200);
      assert.equal(r.json.data.function_call.name, 'set_alarm');
    }
    const over = await call('POST', '/agent/cloud', { token: b.token, body: { command: 'Pasang alarm jam 6 pagi' } });
    assert.equal(over.status, 429);
    assert.equal(over.json.code, 'quota_exceeded');
  });

  await t.test('model dan versi tidak membocorkan id model gateway', async () => {
    const detail = await call('GET', '/models/cloud-gateway');
    assert.equal(detail.status, 200);
    assert.ok(!('gateway_model' in detail.json.data));
    assert.equal(detail.json.data.provider, 'Ezkielyna API');
    const ver = await call('GET', '/app/version');
    assert.equal(ver.json.data.version_code, 2);
  });

  await t.test('tts: audio mp3, cache, kuota, dan error', async () => {
    const tts = require('../src/lib/tts');
    const synthesized = [];
    tts.setEngine(async (text, voiceName) => {
      if (text.includes('gagal')) throw new Error('layanan mati');
      synthesized.push([text, voiceName]);
      return Buffer.from(`ID3 ${voiceName} ${text}`);
    });
    t.after(() => tts.setEngine(null));

    const speak = (body, token = a.token) => fetch(`${base}/tts`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
      body: JSON.stringify(body),
    });

    assert.equal((await speak({ text: 'Halo' }, null)).status, 401);
    assert.equal((await speak({ text: 'Halo', voice: 'robot' })).status, 400);
    assert.equal((await speak({ text: '\u{1F600} ** ' })).status, 400);
    assert.equal((await speak({ text: 'x'.repeat(1001) })).status, 400);

    const first = await speak({ text: 'Alarm  **05.00** dipasang \u{1F44D}' });
    assert.equal(first.status, 200);
    assert.equal(first.headers.get('content-type'), 'audio/mpeg');
    assert.equal(first.headers.get('x-tts-cache'), 'miss');
    assert.equal(Buffer.from(await first.arrayBuffer()).toString(), 'ID3 id-ID-GadisNeural Alarm 05.00 dipasang');

    // Teks yang sama setelah dirapikan diambil dari cache dan tidak memakai kuota.
    const again = await speak({ text: 'Alarm 05.00 dipasang', voice: 'perempuan' });
    assert.equal(again.headers.get('x-tts-cache'), 'hit');
    assert.equal(synthesized.length, 1);

    const male = await speak({ text: 'Alarm 05.00 dipasang', voice: 'laki-laki' });
    assert.equal(male.headers.get('x-tts-cache'), 'miss');
    assert.equal(synthesized[1][1], 'id-ID-ArdiNeural');

    const over = await speak({ text: 'Kalimat ketiga' });
    assert.equal(over.status, 429);
    assert.equal((await over.json()).code, 'quota_exceeded');
    // Cache tetap bisa diputar walau kuota habis.
    assert.equal((await speak({ text: 'Alarm 05.00 dipasang' })).status, 200);

    const broken = await speak({ text: 'ini pasti gagal' }, b.token);
    assert.equal(broken.status, 502);
    assert.equal((await broken.json()).success, false);
  });
});
