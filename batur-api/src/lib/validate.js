// Validasi input sederhana. Semua pelanggaran dilempar sebagai HttpError 400
// supaya klien dapat pesan yang jelas, bukan error 500 dari database.
const { HttpError } = require('./http');

const INT4_MAX = 2_147_483_647;
const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

const bad = (message) => new HttpError(400, message, 'validation');

/** String wajib, di-trim, tidak boleh kosong, maksimal `max` karakter. */
function requiredString(value, field, max) {
  if (typeof value !== 'string' || !value.trim()) throw bad(`${field} wajib diisi`);
  const v = value.trim();
  if (v.length > max) throw bad(`${field} maksimal ${max} karakter`);
  return v;
}

/** String opsional: null/undefined -> null. */
function optionalString(value, field, max) {
  if (value === undefined || value === null) return null;
  if (typeof value !== 'string') throw bad(`${field} harus berupa teks`);
  const v = value.trim();
  if (v.length > max) throw bad(`${field} maksimal ${max} karakter`);
  return v || null;
}

/** Bilangan bulat opsional dalam rentang [min, max]. */
function optionalInt(value, field, min, max) {
  if (value === undefined || value === null) return null;
  if (!Number.isInteger(value) || value < min || value > max) {
    throw bad(`${field} harus bilangan bulat ${min}..${max}`);
  }
  return value;
}

/** id dari path (/:id). Di luar rentang int4 dianggap tidak ada. */
function pathId(raw) {
  if (!/^\d{1,20}$/.test(String(raw))) throw bad('id harus berupa angka');
  const id = Number(raw);
  if (id < 1 || id > INT4_MAX) throw new HttpError(404, 'Data tidak ditemukan');
  return id;
}

function optionalUuid(value, field) {
  if (value === undefined || value === null) return null;
  if (typeof value !== 'string' || !UUID_RE.test(value)) throw bad(`${field} harus UUID`);
  return value.toLowerCase();
}

function oneOf(value, field, allowed) {
  if (!allowed.has(value)) throw bad(`${field} tidak valid: ${String(value).slice(0, 40)}`);
  return value;
}

module.exports = { requiredString, optionalString, optionalInt, pathId, optionalUuid, oneOf, INT4_MAX, UUID_RE };
