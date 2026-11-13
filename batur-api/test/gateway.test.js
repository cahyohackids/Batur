const test = require('node:test');
const assert = require('node:assert/strict');
const { validateArgs, completionsUrl, maskModelName } = require('../src/lib/gateway');

const alarm = {
  type: 'object',
  properties: {
    hour: { type: 'integer', minimum: 0, maximum: 23 },
    minute: { type: 'integer', minimum: 0, maximum: 59 },
    label: { type: 'string' },
  },
  required: ['hour', 'minute'],
};

test('validateArgs: argumen valid dirapikan', () => {
  assert.deepEqual(validateArgs({ hour: '5', minute: 30.0, label: ' Subuh ' }, alarm), { hour: 5, minute: 30, label: 'Subuh' });
});

test('validateArgs: argumen tidak valid ditolak', () => {
  assert.equal(validateArgs(null, alarm), null);
  assert.equal(validateArgs([], alarm), null);
  assert.equal(validateArgs({ hour: 25, minute: 0 }, alarm), null);
  assert.equal(validateArgs({ hour: 5 }, alarm), null);
  assert.equal(validateArgs({ hour: 5.5, minute: 0 }, alarm), null);
  assert.equal(validateArgs({ hour: 'lima', minute: 0 }, alarm), null);
});

test('completionsUrl tidak menggandakan /v1', () => {
  for (const base of ['https://x.id', 'https://x.id/', 'https://x.id/v1', 'https://x.id/v1/']) {
    assert.equal(completionsUrl(base), 'https://x.id/v1/chat/completions');
  }
});

test('maskModelName menyembunyikan nama model', () => {
  assert.equal(maskModelName('Aku DeepSeek-V4.1 dari deepseek.', 'model-rahasia'), 'Aku Batur dari Batur');
  assert.equal(maskModelName('pakai model-rahasia ya', 'model-rahasia'), 'pakai Batur ya');
});
