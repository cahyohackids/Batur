const test = require('node:test');
const assert = require('node:assert/strict');
const { normalizeText } = require('../src/lib/tts');

test('normalizeText membuang emoji, markdown, dan spasi berlebih', () => {
  assert.equal(normalizeText('  **Halo**  dunia 👋🏽\n\n'), 'Halo dunia');
  assert.equal(normalizeText('Jam 05.00, oke?'), 'Jam 05.00, oke?');
  assert.equal(normalizeText('a\u0000b​c'), 'a b c');
  assert.equal(normalizeText('❤️ 🎉'), '');
});
