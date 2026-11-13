-- =====================================================================
-- Batur API - Skema Database (PostgreSQL)
-- Tugas Akhir Praktikum PPB 2026 - Ahmad Putra Cahyo (21120124140143)
-- =====================================================================

-- Katalog kemampuan (skill) agent. Definisi fungsi (parameters) disimpan
-- sebagai JSON Schema agar aplikasi bisa mengambil daftar tool dari API.
CREATE TABLE IF NOT EXISTS skills (
  id                    SERIAL PRIMARY KEY,
  slug                  VARCHAR(50)  NOT NULL UNIQUE,
  name                  VARCHAR(100) NOT NULL,
  description           TEXT         NOT NULL,
  category              VARCHAR(30)  NOT NULL,           -- waktu, produktivitas, komunikasi, navigasi
  icon                  VARCHAR(50)  NOT NULL DEFAULT 'bolt',
  function_name         VARCHAR(60)  NOT NULL UNIQUE,    -- nama fungsi yang dipanggil model
  parameters            JSONB        NOT NULL DEFAULT '{}'::jsonb,
  example_commands      JSONB        NOT NULL DEFAULT '[]'::jsonb,
  android_action        VARCHAR(120),                    -- intent / mekanisme eksekusi di Android
  permissions           JSONB        NOT NULL DEFAULT '[]'::jsonb,
  requires_confirmation BOOLEAN      NOT NULL DEFAULT TRUE,
  is_active             BOOLEAN      NOT NULL DEFAULT TRUE,
  created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  updated_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Daftar model AI: on-device (LiteRT-LM) dan cloud (via gateway).
CREATE TABLE IF NOT EXISTS models (
  id             SERIAL PRIMARY KEY,
  slug           VARCHAR(50)  NOT NULL UNIQUE,
  name           VARCHAR(100) NOT NULL,
  type           VARCHAR(20)  NOT NULL CHECK (type IN ('on_device', 'cloud')),
  provider       VARCHAR(60)  NOT NULL,
  runtime        VARCHAR(60)  NOT NULL,                  -- LiteRT-LM / OpenAI-compatible API
  gateway_model  VARCHAR(100),                           -- id model di gateway (khusus cloud)
  size_label     VARCHAR(40),
  ram_label      VARCHAR(40),
  supports_tools BOOLEAN      NOT NULL DEFAULT TRUE,
  description    TEXT         NOT NULL,
  is_default     BOOLEAN      NOT NULL DEFAULT FALSE,
  is_active      BOOLEAN      NOT NULL DEFAULT TRUE,
  download_url   TEXT,                                   -- khusus on_device: file .litertlm
  file_name      VARCHAR(160),
  size_bytes     BIGINT,
  created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Untuk database yang dibuat sebelum kolom unduhan ditambahkan.
ALTER TABLE models ADD COLUMN IF NOT EXISTS download_url TEXT;
ALTER TABLE models ADD COLUMN IF NOT EXISTS file_name    VARCHAR(160);
ALTER TABLE models ADD COLUMN IF NOT EXISTS size_bytes   BIGINT;

-- Riwayat tugas yang dijalankan agent.
CREATE TABLE IF NOT EXISTS tasks (
  id            SERIAL PRIMARY KEY,
  device_id     VARCHAR(64)  NOT NULL,
  command       TEXT         NOT NULL,
  mode          VARCHAR(20)  NOT NULL CHECK (mode IN ('on_device', 'cloud', 'fallback')),
  model_slug    VARCHAR(50)  REFERENCES models(slug) ON UPDATE CASCADE ON DELETE SET NULL,
  skill_slug    VARCHAR(50)  REFERENCES skills(slug) ON UPDATE CASCADE ON DELETE SET NULL,
  function_call JSONB,                                   -- {"name": "...", "arguments": {...}}
  status        VARCHAR(20)  NOT NULL CHECK (status IN ('success', 'cancelled', 'failed', 'answered')),
  result_text   TEXT,
  latency_ms    INTEGER      CHECK (latency_ms IS NULL OR latency_ms >= 0),
  created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Jejak langkah agent per tugas (ditampilkan di halaman Detail Riwayat).
CREATE TABLE IF NOT EXISTS task_steps (
  id          SERIAL PRIMARY KEY,
  task_id     INTEGER     NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
  step_order  INTEGER     NOT NULL,
  type        VARCHAR(20) NOT NULL CHECK (type IN ('input', 'reasoning', 'tool_call', 'confirmation', 'execution', 'response', 'error')),
  content     TEXT        NOT NULL,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE (task_id, step_order)
);

-- Catatan yang dibuat lewat skill "Simpan Catatan".
CREATE TABLE IF NOT EXISTS notes (
  id         SERIAL PRIMARY KEY,
  device_id  VARCHAR(64) NOT NULL,
  content    TEXT        NOT NULL,
  task_id    INTEGER     REFERENCES tasks(id) ON DELETE SET NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_tasks_device_created ON tasks (device_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_task_steps_task      ON task_steps (task_id, step_order);
CREATE INDEX IF NOT EXISTS idx_notes_device_created ON notes (device_id, created_at DESC);

-- =====================================================================
-- v1.1: identitas perangkat, preferensi skill per perangkat, kuota agent,
-- dan idempotensi (client_id) untuk antrean offline aplikasi.
-- =====================================================================

-- Setiap HP mendaftar sekali; server hanya menyimpan hash token.
CREATE TABLE IF NOT EXISTS devices (
  id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  token_hash   CHAR(64)    NOT NULL UNIQUE,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  last_seen_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Skill aktif/nonaktif per perangkat (default mengikuti skills.is_active).
CREATE TABLE IF NOT EXISTS device_skill_prefs (
  device_id  UUID        NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
  skill_slug VARCHAR(50) NOT NULL REFERENCES skills(slug) ON UPDATE CASCADE ON DELETE CASCADE,
  is_active  BOOLEAN     NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  PRIMARY KEY (device_id, skill_slug)
);

-- Jumlah perintah mode cloud per perangkat per hari (WIB), untuk kuota harian.
CREATE TABLE IF NOT EXISTS agent_usage (
  device_id UUID    NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
  day       DATE    NOT NULL,
  count     INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (device_id, day)
);

-- Pemakaian suara cloud (POST /api/tts) per perangkat per hari (WIB); cache tidak dihitung.
CREATE TABLE IF NOT EXISTS tts_usage (
  device_id UUID    NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
  day       DATE    NOT NULL,
  count     INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (device_id, day)
);

-- client_id dikirim aplikasi untuk tiap tugas/catatan; kirim ulang dari antrean
-- offline tidak membuat data dobel.
ALTER TABLE tasks ADD COLUMN IF NOT EXISTS client_id UUID;
ALTER TABLE notes ADD COLUMN IF NOT EXISTS client_id UUID;
CREATE UNIQUE INDEX IF NOT EXISTS uq_tasks_device_client ON tasks (device_id, client_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_notes_device_client ON notes (device_id, client_id);
