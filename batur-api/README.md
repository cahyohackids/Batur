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

Salin `.env.example` → `.env` dan isi:

Port 3000 dipakai karena port 5000 di macOS diambil AirPlay Receiver. Pada PostgreSQL Homebrew, user default adalah user Mac (tanpa password), bukan `postgres`.

```
PORT=3000
DATABASE_URL=postgresql://<user-mac>@localhost:5432/batur

# Opsional: gateway AI (OpenAI-compatible). Kosongkan untuk pakai fallback aturan.
GATEWAY_BASE_URL=
GATEWAY_API_KEY=
GATEWAY_MODEL=
```

---

## Menjalankan

```bash
npm install
npm run db:init   # buat tabel + isi data awal (aman dijalankan ulang)
npm start         # produksi
npm run dev       # development (auto-restart)
npm test          # uji rule parser (15 kasus)
```

---

## Endpoint

| Method | Path | Fungsi |
|--------|------|--------|
| GET | `/api/health` | Cek server + koneksi DB |
| GET | `/api/skills?category=&active=` | List skill |
| GET | `/api/skills/tools` | Definisi fungsi skill aktif (format tools OpenAI) |
| GET | `/api/skills/:slug` | Detail skill + statistik |
| PATCH | `/api/skills/:slug` | Aktifkan / nonaktifkan skill |
| GET | `/api/models?type=` | List model |
| GET | `/api/models/:slug` | Detail model + statistik |
| GET | `/api/tasks?device_id=&status=&mode=&limit=&offset=` | List riwayat |
| GET | `/api/tasks/stats?device_id=` | Statistik tugas |
| GET | `/api/tasks/:id` | Detail tugas + langkah |
| POST | `/api/tasks` | Simpan tugas + langkah |
| DELETE | `/api/tasks/:id` | Hapus tugas |
| GET | `/api/notes?device_id=` | List catatan |
| POST | `/api/notes` | Simpan catatan |
| DELETE | `/api/notes/:id` | Hapus catatan |
| POST | `/api/agent/cloud` | Proses perintah via gateway AI (cadangan: rule parser) |

Semua respons berbentuk `{ "success": true, "data": ..., "meta"?: ... }`; error berbentuk `{ "success": false, "message": "..." }`.

---

## Tabel database

Skema ada di `database/schema.sql`, data awal di `database/seed.sql`.

| Tabel | Isi |
|-------|-----|
| `skills` | 6 skill agent: slug, nama, kategori, definisi fungsi (JSON Schema), butuh konfirmasi, aktif/tidak |
| `models` | Model on-device (Gemma 4 E2B, Qwen3 0.6B) dan cloud; untuk on-device ada `download_url`, `file_name`, `size_bytes` dipakai fitur unduh di app |
| `tasks` | Riwayat perintah per `device_id`: mode, model, skill, `function_call`, status, hasil, latensi |
| `task_steps` | Jejak agent tiap tugas: input, penalaran, pemanggilan fungsi, konfirmasi, eksekusi, respons |
| `notes` | Catatan dari skill Simpan Catatan |

---

## Contoh curl

```bash
# Health
curl http://localhost:3000/api/health

# List skill
curl http://localhost:3000/api/skills

# Proses perintah (mode cloud / fallback)
curl -X POST http://localhost:3000/api/agent/cloud \
  -H "Content-Type: application/json" \
  -d '{"command":"Pasang alarm jam 6 pagi","device_id":"demo-device"}'

# Simpan tugas
curl -X POST http://localhost:3000/api/tasks \
  -H "Content-Type: application/json" \
  -d '{
    "device_id":"demo-device",
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
