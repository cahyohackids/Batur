# Batur API

REST API untuk aplikasi **Batur — Asisten AI On-Device** (Tugas Akhir Praktikum PPB 2026).

| Item | Nilai |
|------|-------|
| Nama | Ahmad Putra Cahyo |
| NIM  | 21120124140143 |
| Prodi | Teknik Komputer, Universitas Diponegoro |
| Praktikum | PPB 2026, Kelompok 27, Shift 4 |

---

## Setup PostgreSQL lokal

```bash
# Install (Homebrew, Mac)
brew install postgresql@16
brew services start postgresql@16

# Tambahkan ke PATH (tambahkan ke ~/.zshrc / ~/.bash_profile)
export PATH="/opt/homebrew/opt/postgresql@16/bin:$PATH"

# Buat database
createdb batur
```

---

## Konfigurasi

Salin `.env.example` ke `.env`, lalu isi. Semua variabel dijelaskan di file itu.

Port 3000 dipakai karena port 5000 di macOS diambil AirPlay Receiver. Pada PostgreSQL Homebrew, user default adalah user Mac (tanpa password), bukan `postgres`. Server mendengarkan di `127.0.0.1`; isi `HOST=0.0.0.0` kalau mau diuji dari HP di Wi-Fi yang sama.

Mode cloud memakai model yang dikunci di `GATEWAY_MODEL`. Aplikasi tidak bisa memilih model dan tidak pernah menerima namanya.

---

## Menjalankan

```bash
npm install
npm run db:init   # buat tabel + isi data awal (aman dijalankan ulang)
npm start         # produksi
npm run dev       # development (auto-restart)
npm test          # uji rule parser, validasi gateway, dan perapian teks suara
```

Uji endpoint lengkap (registrasi, isolasi data antar-perangkat, validasi, kuota) butuh database kosong khusus uji:

```bash
createdb batur_test
TEST_DATABASE_URL=postgresql://<user-mac>@localhost:5432/batur_test npm test
```

---

## Autentikasi perangkat

Setiap HP mendaftar sekali lewat `POST /api/devices/register` dan menerima `token`. Token dikirim di header:

```
Authorization: Bearer <token>
```

Server hanya menyimpan hash SHA-256 token. Riwayat, catatan, statistik, preferensi skill, dan kuota dibatasi ke perangkat pemilik token. Data perangkat lain dijawab 404.

| Batas | Nilai |
|-------|-------|
| Request umum | 120 per menit per IP |
| Registrasi | 10 per jam per IP |
| Mode cloud | 15 per menit dan `AGENT_DAILY_QUOTA` (default 50) per hari per perangkat |
| Suara (TTS) | 20 per menit dan `TTS_DAILY_QUOTA` (default 200) sintesis baru per hari per perangkat; audio dari cache tidak dihitung |
| Panjang perintah | 500 karakter |
| Body request | 16 KB |

Error memakai kode di field `code`: `unauthorized` (401), `validation` (400), `rate_limited` dan `quota_exceeded` (429).

---

## Endpoint

Kolom **Token**: wajib, opsional (hasil disesuaikan dengan perangkat kalau ada), atau tidak perlu.

| Method | Path | Token | Fungsi |
|--------|------|-------|--------|
| GET | `/api/health` | - | Cek server + koneksi DB |
| POST | `/api/devices/register` | - | Daftarkan perangkat, dapat `device_id` + `token` |
| GET | `/api/app/version` | - | Versi APK terbaru (untuk banner update) |
| GET | `/api/skills?category=&active=` | opsional | List skill (status aktif per perangkat) |
| GET | `/api/skills/tools` | opsional | Definisi fungsi skill aktif (format tools OpenAI) |
| GET | `/api/skills/:slug` | opsional | Detail skill + statistik pemakaian perangkat |
| PATCH | `/api/skills/:slug` | wajib | Aktifkan / nonaktifkan skill untuk perangkat ini saja |
| GET | `/api/models?type=` | - | List model |
| GET | `/api/models/:slug` | opsional | Detail model + statistik |
| GET | `/api/tasks?status=&mode=&limit=&offset=` | wajib | List riwayat |
| GET | `/api/tasks/stats` | wajib | Statistik tugas |
| GET | `/api/tasks/:id` | wajib | Detail tugas + langkah |
| POST | `/api/tasks` | wajib | Simpan tugas + langkah (idempoten dengan `client_id`) |
| DELETE | `/api/tasks/:id` | wajib | Hapus tugas |
| GET | `/api/notes` | wajib | List catatan |
| POST | `/api/notes` | wajib | Simpan catatan (idempoten dengan `client_id`) |
| DELETE | `/api/notes/:id` | wajib | Hapus catatan |
| POST | `/api/agent/cloud` | wajib | Proses perintah via gateway AI (cadangan: rule parser) |
| POST | `/api/tts` | wajib | Bacakan teks (maks 1000 karakter) dengan suara `perempuan` atau `laki-laki`; balasan `audio/mpeg` |

Semua respons berbentuk `{ "success": true, "data": ..., "meta"?: ... }`; error berbentuk `{ "success": false, "message": "...", "code"?: "..." }`.

`/api/tts` adalah satu-satunya endpoint yang membalas berkas, bukan JSON (errornya tetap JSON). Suaranya memakai suara neural Indonesia dari layanan Read Aloud Microsoft Edge lewat library [`node-edge-tts`](https://github.com/SchneeHertz/node-edge-tts). Itu layanan tidak resmi yang bisa berubah atau diblokir sewaktu-waktu, jadi aplikasi selalu punya cadangan TTS bawaan Android. Audio disimpan di `TTS_CACHE_DIR` (kunci: hash suara + teks), sehingga kalimat yang sama tidak disintesis ulang. `TTS_ENABLED=false` mematikan endpoint ini.

`client_id` (UUID) dikirim aplikasi untuk tiap tugas dan catatan. Kalau antrean offline mengirim ulang data yang sama, server mengembalikan data lama (200), bukan membuat duplikat.

---

## Tabel database

Skema ada di `database/schema.sql`, data awal di `database/seed.sql`.

| Tabel | Isi |
|-------|-----|
| `skills` | 6 skill agent: slug, nama, kategori, definisi fungsi (JSON Schema), butuh konfirmasi, aktif default |
| `models` | Model on-device (Gemma 4 E2B, Qwen3 0.6B) dan cloud; untuk on-device ada `download_url`, `file_name`, `size_bytes` dipakai fitur unduh di app |
| `devices` | Perangkat terdaftar: `id`, hash token, waktu terakhir terlihat |
| `device_skill_prefs` | Skill aktif/nonaktif per perangkat |
| `agent_usage` | Jumlah perintah mode cloud per perangkat per hari (kuota) |
| `tts_usage` | Jumlah sintesis suara baru per perangkat per hari (kuota) |
| `tasks` | Riwayat perintah per perangkat: mode, model, skill, `function_call`, status, hasil, latensi, `client_id` |
| `task_steps` | Jejak agent tiap tugas: input, penalaran, pemanggilan fungsi, konfirmasi, eksekusi, respons |
| `notes` | Catatan dari skill Simpan Catatan |

---

## Deploy (VPS)

Server publik: `https://batur.ezkielyna.store` (Cloudflare proxy → Nginx → Node di `127.0.0.1:3100`, dijalankan PM2 dengan nama `batur-api`).

| Bagian | Lokasi |
|--------|--------|
| Kode | `/opt/batur/api` (`.env` chmod 600) |
| Database | container Docker `batur-postgres` (PostgreSQL 16) di `127.0.0.1:5440`, data di `/opt/batur/pgdata` |
| Nginx | `/etc/nginx/sites-available/batur.ezkielyna.store` |
| Sertifikat | Let's Encrypt lewat DNS-01 Cloudflare (`/opt/batur/cf_acme_hook.py`), diperpanjang otomatis |
| Backup | `/etc/cron.daily/batur-backup`, `pg_dump` harian, disimpan 7 hari di `/opt/batur/backups` |
| Cache suara | `/opt/batur/tts-cache` (maks 2000 file mp3, yang lama dihapus otomatis) |

Update kode:

```bash
rsync -az --delete --exclude node_modules --exclude .env --exclude .cache ./ vps-store:/opt/batur/api/
ssh vps-store 'cd /opt/batur/api && npm ci --omit=dev && npm run db:init && pm2 restart batur-api'
```

---

## Contoh curl

```bash
B=https://batur.ezkielyna.store/api

# Health
curl $B/health

# Daftar perangkat, simpan token
TOKEN=$(curl -s -X POST $B/devices/register -H "Content-Type: application/json" -d '{}' | jq -r .data.token)

# Proses perintah (mode cloud / fallback)
curl -X POST $B/agent/cloud \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"command":"Pasang alarm jam 6 pagi"}'

# Bacakan teks, simpan sebagai mp3
curl -X POST $B/tts -o batur.mp3 \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"text":"Alarm jam 6 pagi sudah dipasang.","voice":"perempuan"}'

# Simpan tugas
curl -X POST $B/tasks \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{
    "client_id":"3f2b8c1e-6a2d-4b7e-9c11-0a1b2c3d4e5f",
    "command":"Pasang alarm jam 6 pagi",
    "mode":"cloud",
    "skill_slug":"set-alarm",
    "status":"success",
    "result_text":"Alarm 06.00 berhasil dipasang.",
    "latency_ms":320,
    "steps":[
      {"type":"input","content":"Pasang alarm jam 6 pagi"},
      {"type":"tool_call","content":"set_alarm(hour=6, minute=0)"},
      {"type":"confirmation","content":"Pengguna menyetujui"},
      {"type":"execution","content":"AlarmClock.ACTION_SET_ALARM"},
      {"type":"response","content":"Alarm 06.00 berhasil dipasang."}
    ]
  }'
```
