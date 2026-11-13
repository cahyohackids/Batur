const test = require('node:test');
const assert = require('node:assert/strict');
const { parse } = require('../src/lib/ruleParser');

const call = (command) => {
  const r = parse(command);
  assert.equal(r.mode, 'fallback');
  return r;
};

const expectTool = (command, skill, name, args) => {
  const r = call(command);
  assert.equal(r.type, 'tool_call', `"${command}" seharusnya tool_call`);
  assert.equal(r.skill, skill);
  assert.equal(r.function_call.name, name);
  for (const [k, v] of Object.entries(args)) {
    assert.deepEqual(r.function_call.arguments[k], v, `"${command}" argumen ${k}`);
  }
  return r;
};

test('alarm: jam 5 subuh', () => expectTool('Bangunin aku jam 5 subuh', 'set-alarm', 'set_alarm', { hour: 5, minute: 0 }));
test('alarm: format 06.30', () => expectTool('Pasang alarm 06.30', 'set-alarm', 'set_alarm', { hour: 6, minute: 30 }));
test('alarm: jam 7 malam', () => expectTool('Set alarm jam 7 malam', 'set-alarm', 'set_alarm', { hour: 19, minute: 0 }));
test('alarm: jam 3 sore', () => expectTool('alarm jam 3 sore ya', 'set-alarm', 'set_alarm', { hour: 15, minute: 0 }));
test('alarm: setengah 6 pagi', () => expectTool('Bangunin aku jam setengah 6 pagi', 'set-alarm', 'set_alarm', { hour: 5, minute: 30 }));
test('alarm: setengah 8 malam', () => expectTool('alarm setengah 8 malam', 'set-alarm', 'set_alarm', { hour: 19, minute: 30 }));

test('timer: menit', () => expectTool('Set timer 25 menit', 'set-timer', 'set_timer', { seconds: 1500 }));
test('timer: detik', () => expectTool('Timer 90 detik', 'set-timer', 'set_timer', { seconds: 90 }));

test('catatan', () => expectTool('Catat: beli susu dan telur', 'save-note', 'save_note', { content: 'beli susu dan telur' }));

test('whatsapp dengan nomor', () => expectTool(
  'wa ke 081234567890 bilang aku sudah sampai',
  'draft-whatsapp', 'draft_whatsapp',
  { phone: '6281234567890', message: 'aku sudah sampai' },
));

test('maps: navigasi', () => expectTool(
  'Navigasi ke Universitas Diponegoro',
  'open-maps', 'open_maps',
  { query: 'Universitas Diponegoro', navigate: true },
));

test('jadwal: besok + judul bersih', () => {
  const r = expectTool('Jadwalkan rapat himpunan besok jam 9 pagi', 'create-event', 'create_calendar_event',
    { title: 'Rapat himpunan', start_time: '09:00' });
  const today = new Date(new Date().toLocaleString('en-US', { timeZone: 'Asia/Jakarta' }));
  today.setDate(today.getDate() + 1);
  const expected = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-${String(today.getDate()).padStart(2, '0')}`;
  assert.equal(r.function_call.arguments.date, expected);
});

test('jadwal: tanggal + bulan', () => {
  const r = expectTool('Buat jadwal ujian PPB 27 november jam 13.00', 'create-event', 'create_calendar_event',
    { title: 'Ujian PPB', start_time: '13:00' });
  assert.match(r.function_call.arguments.date, /^\d{4}-11-27$/);
});

test('tidak dikenal -> text', () => {
  const r = call('Halo apa kabar?');
  assert.equal(r.type, 'text');
  assert.ok(r.text.length > 0);
});

test('kata "jadwal" tidak salah terdeteksi sebagai WhatsApp', () => {
  const r = call('jadwal kirim laporan besok jam 8 pagi');
  assert.equal(r.skill, 'create-event');
});
