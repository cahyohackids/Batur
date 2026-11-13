-- =====================================================================
-- Batur API - Data awal (seed)
-- Aman dijalankan ulang: memakai ON CONFLICT DO NOTHING.
-- =====================================================================

-- ---------------------------------------------------------------------
-- SKILLS (6 kemampuan agent)
-- ---------------------------------------------------------------------
INSERT INTO skills (slug, name, description, category, icon, function_name, parameters, example_commands, android_action, permissions, requires_confirmation) VALUES
('set-alarm', 'Pasang Alarm',
 'Memasang alarm di aplikasi Jam bawaan ponsel pada jam yang disebutkan pengguna.',
 'waktu', 'alarm', 'set_alarm',
 '{"type":"object","properties":{"hour":{"type":"integer","minimum":0,"maximum":23,"description":"Jam (format 24 jam)"},"minute":{"type":"integer","minimum":0,"maximum":59,"description":"Menit"},"label":{"type":"string","description":"Label alarm, opsional"}},"required":["hour","minute"]}',
 '["Bangunin aku jam 5 subuh","Pasang alarm jam 06.30 buat kuliah","Alarm jam 9 malam label minum obat"]',
 'AlarmClock.ACTION_SET_ALARM', '["com.android.alarm.permission.SET_ALARM"]', TRUE),

('set-timer', 'Pasang Timer',
 'Menjalankan hitung mundur (timer) dengan durasi tertentu.',
 'waktu', 'hourglass', 'set_timer',
 '{"type":"object","properties":{"seconds":{"type":"integer","minimum":1,"maximum":86400,"description":"Durasi timer dalam detik"},"label":{"type":"string","description":"Label timer, opsional"}},"required":["seconds"]}',
 '["Timer 25 menit buat fokus belajar","Hitung mundur 10 menit","Timer 90 detik masak mi"]',
 'AlarmClock.ACTION_SET_TIMER', '["com.android.alarm.permission.SET_ALARM"]', TRUE),

('create-event', 'Buat Jadwal',
 'Menambahkan acara ke aplikasi Kalender dengan judul, tanggal, dan jam.',
 'produktivitas', 'calendar', 'create_calendar_event',
 '{"type":"object","properties":{"title":{"type":"string","description":"Judul acara"},"date":{"type":"string","description":"Tanggal format YYYY-MM-DD"},"start_time":{"type":"string","description":"Jam mulai format HH:mm"},"duration_minutes":{"type":"integer","minimum":5,"maximum":1440,"description":"Durasi acara dalam menit"},"location":{"type":"string","description":"Lokasi, opsional"}},"required":["title","date","start_time"]}',
 '["Jadwalin responsi PPB tanggal 26 November jam 10","Besok jam 1 siang rapat AnterDips","Catat jadwal asistensi Jumat jam 3 sore di lab"]',
 'Intent.ACTION_INSERT (CalendarContract.Events)', '[]', TRUE),

('save-note', 'Simpan Catatan',
 'Menyimpan catatan singkat ke server Batur sehingga bisa dibuka lagi dari perangkat mana pun.',
 'produktivitas', 'note', 'save_note',
 '{"type":"object","properties":{"content":{"type":"string","description":"Isi catatan"}},"required":["content"]}',
 '["Catat: beli plastik kemasan 2 pak","Simpan catatan ide TA pakai on-device AI","Catat nomor ruang responsi E201"]',
 'POST /api/notes (Batur API)', '["android.permission.INTERNET"]', FALSE),

('draft-whatsapp', 'Draf WhatsApp',
 'Membuka WhatsApp dengan pesan yang sudah terisi. Pengguna tetap menekan tombol kirim sendiri.',
 'komunikasi', 'message', 'draft_whatsapp',
 '{"type":"object","properties":{"phone":{"type":"string","description":"Nomor tujuan format 62xxxxxxxxxx, opsional"},"message":{"type":"string","description":"Isi pesan"}},"required":["message"]}',
 '["Kirim WA ke 6281234567890 bilang aku telat 10 menit","Bikin pesan WA: maaf kak, saya izin asistensi hari ini","Tulis WA ucapan terima kasih ke asisten"]',
 'Intent.ACTION_VIEW (https://wa.me/)', '[]', TRUE),

('open-maps', 'Buka Peta',
 'Mencari lokasi atau rute di aplikasi peta.',
 'navigasi', 'map', 'open_maps',
 '{"type":"object","properties":{"query":{"type":"string","description":"Nama tempat atau alamat"},"navigate":{"type":"boolean","description":"true untuk langsung mulai navigasi"}},"required":["query"]}',
 '["Cari rute ke Gedung Teknik Komputer UNDIP","Tunjukin lokasi Simpang Lima","Navigasi ke stasiun Tawang"]',
 'Intent.ACTION_VIEW (geo: / google.navigation:)', '[]', TRUE)
ON CONFLICT (slug) DO NOTHING;

-- ---------------------------------------------------------------------
-- MODELS
-- Ukuran dan kebutuhan RAM masih perkiraan: sesuaikan dengan file model
-- yang benar-benar dipakai.
-- ---------------------------------------------------------------------
-- FunctionGemma 270M diganti Qwen3 0.6B: file .litertlm FunctionGemma di Hugging Face
-- bersifat gated (perlu login + setuju lisensi), jadi tidak bisa diunduh langsung dari app.
DELETE FROM models WHERE slug = 'functiongemma-270m';

-- Ukuran file diambil dari Hugging Face (litert-community), dicek 13 Nov 2026.
-- Memori puncak dari model card resmi (CPU ~1,7 GB untuk Gemma 4 E2B).
INSERT INTO models (slug, name, type, provider, runtime, gateway_model, size_label, ram_label, supports_tools, description, is_default,
                    download_url, file_name, size_bytes) VALUES
('gemma-4-e2b', 'Gemma 4 E2B', 'on_device', 'Google', 'LiteRT-LM (Android, Kotlin)', NULL,
 '2,6 GB', '±1,7 GB saat jalan; ponsel RAM 6 GB+',
 TRUE, 'Model utama yang berjalan langsung di ponsel tanpa internet. Mendukung function calling untuk memilih skill.', TRUE,
 'https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm',
 'gemma-4-E2B-it.litertlm', 2588147712),
('qwen3-0.6b', 'Qwen3 0.6B', 'on_device', 'Alibaba Qwen', 'LiteRT-LM (Android, Kotlin)', NULL,
 '343 MB', 'ringan; ponsel RAM 4 GB cukup',
 TRUE, 'Model kecil (INT4) sebagai cadangan untuk ponsel dengan RAM atau penyimpanan terbatas. Lebih cepat diunduh, tapi kurang akurat memilih skill dibanding Gemma 4 E2B.', FALSE,
 'https://huggingface.co/litert-community/Qwen3-0.6B/resolve/main/Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm',
 'Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm', 343490560),
('cloud-gateway', 'Batur Cloud', 'cloud', 'Ezkielyna API', 'OpenAI-compatible API', NULL,
 'server', 'tidak memakai RAM ponsel',
 TRUE, 'Model cloud yang dipanggil lewat Batur API ke Ezkielyna API. Dipakai untuk tugas berat atau saat model on-device tidak yakin.', FALSE,
 NULL, NULL, NULL)
ON CONFLICT (slug) DO UPDATE SET
  name = EXCLUDED.name, provider = EXCLUDED.provider, runtime = EXCLUDED.runtime,
  gateway_model = EXCLUDED.gateway_model,
  size_label = EXCLUDED.size_label, ram_label = EXCLUDED.ram_label,
  supports_tools = EXCLUDED.supports_tools, description = EXCLUDED.description,
  download_url = EXCLUDED.download_url, file_name = EXCLUDED.file_name, size_bytes = EXCLUDED.size_bytes;

-- ---------------------------------------------------------------------
-- CONTOH RIWAYAT (device_id = demo-device) agar halaman Riwayat
-- langsung berisi data saat pertama kali dicoba.
-- ---------------------------------------------------------------------
DO $$
DECLARE t1 INT; t2 INT; t3 INT;
BEGIN
  IF NOT EXISTS (SELECT 1 FROM tasks WHERE device_id = 'demo-device') THEN
    INSERT INTO tasks (device_id, command, mode, model_slug, skill_slug, function_call, status, result_text, latency_ms)
    VALUES ('demo-device', 'Bangunin aku jam 5 subuh', 'on_device', 'gemma-4-e2b', 'set-alarm',
            '{"name":"set_alarm","arguments":{"hour":5,"minute":0,"label":"Bangun subuh"}}',
            'success', 'Alarm 05.00 berhasil dipasang.', 820)
    RETURNING id INTO t1;
    INSERT INTO task_steps (task_id, step_order, type, content) VALUES
      (t1, 1, 'input',        'Bangunin aku jam 5 subuh'),
      (t1, 2, 'tool_call',    'set_alarm(hour=5, minute=0, label="Bangun subuh")'),
      (t1, 3, 'confirmation', 'Pengguna menyetujui: Pasang alarm 05.00?'),
      (t1, 4, 'execution',    'AlarmClock.ACTION_SET_ALARM dikirim ke aplikasi Jam'),
      (t1, 5, 'response',     'Alarm 05.00 berhasil dipasang.');

    INSERT INTO tasks (device_id, command, mode, model_slug, skill_slug, function_call, status, result_text, latency_ms)
    VALUES ('demo-device', 'Catat: beli plastik kemasan 2 pak', 'on_device', 'gemma-4-e2b', 'save-note',
            '{"name":"save_note","arguments":{"content":"Beli plastik kemasan 2 pak"}}',
            'success', 'Catatan tersimpan.', 640)
    RETURNING id INTO t2;
    INSERT INTO task_steps (task_id, step_order, type, content) VALUES
      (t2, 1, 'input',     'Catat: beli plastik kemasan 2 pak'),
      (t2, 2, 'tool_call', 'save_note(content="Beli plastik kemasan 2 pak")'),
      (t2, 3, 'execution', 'POST /api/notes -> 201 Created'),
      (t2, 4, 'response',  'Catatan tersimpan.');
    INSERT INTO notes (device_id, content, task_id) VALUES ('demo-device', 'Beli plastik kemasan 2 pak', t2);

    INSERT INTO tasks (device_id, command, mode, model_slug, skill_slug, function_call, status, result_text, latency_ms)
    VALUES ('demo-device', 'Jelaskan singkat apa itu REST API', 'cloud', 'cloud-gateway', NULL, NULL,
            'answered', 'REST API adalah cara aplikasi bertukar data lewat HTTP memakai metode seperti GET, POST, PUT, dan DELETE.', 1950)
    RETURNING id INTO t3;
    INSERT INTO task_steps (task_id, step_order, type, content) VALUES
      (t3, 1, 'input',     'Jelaskan singkat apa itu REST API'),
      (t3, 2, 'reasoning', 'Tidak ada skill yang cocok, permintaan diteruskan ke model cloud.'),
      (t3, 3, 'response',  'REST API adalah cara aplikasi bertukar data lewat HTTP memakai metode seperti GET, POST, PUT, dan DELETE.');
  END IF;
END $$;
