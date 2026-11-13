// Helper respons agar semua endpoint memakai format yang sama:
// { success: true, data, meta? }  atau  { success: false, message, code? }
class HttpError extends Error {
  constructor(status, message, code) {
    super(message);
    this.status = status;
    if (code) this.code = code;
  }
}

const ok = (res, data, status = 200, meta) =>
  res.status(status).json(meta ? { success: true, data, meta } : { success: true, data });

const asyncHandler = (fn) => (req, res, next) => Promise.resolve(fn(req, res, next)).catch(next);

const toInt = (value, fallback, { min = 0, max = Number.MAX_SAFE_INTEGER } = {}) => {
  const n = Number.parseInt(value, 10);
  if (Number.isNaN(n)) return fallback;
  return Math.min(Math.max(n, min), max);
};

module.exports = { HttpError, ok, asyncHandler, toInt };
