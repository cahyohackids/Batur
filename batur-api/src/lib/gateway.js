// Panggil gateway AI (OpenAI-compatible) untuk mode cloud agent.
// Kalau gateway tidak dikonfigurasi atau error → fallback ke ruleParser.
const https = require('https');
const http  = require('http');
const { parse: ruleParse } = require('./ruleParser');

const TIMEOUT_MS = 20_000;

function buildSystemPrompt() {
  const now = new Date().toLocaleString('id-ID', {
    timeZone: 'Asia/Jakarta',
    dateStyle: 'full',
    timeStyle: 'short',
  });
  return `Kamu adalah Batur, asisten AI di ponsel Android. Waktu sekarang: ${now} WIB.
Pengguna memberi perintah dalam bahasa sehari-hari. Tugasmu: pilih skill yang paling cocok dan panggil fungsinya dengan argumen yang tepat.
Kalau tidak ada skill yang cocok, balas teks biasa: bahasa Indonesia santai, maksimal 3 kalimat, teks polos tanpa Markdown (tanpa **, #, atau daftar berpoin).
Jangan menyebut nama model, penyedia, atau gateway yang kamu pakai.
Untuk draft_whatsapp dan save_note, tulis isi persis sesuai maksud pengguna; jangan menambah kata, sapaan, atau emoji.
Jangan bertanya balik. Eksekusi langsung.`;
}

function httpPost(url, payload, apiKey) {
  return new Promise((resolve, reject) => {
    const body = JSON.stringify(payload);
    const parsed = new URL(url);
    const isHttps = parsed.protocol === 'https:';
    const options = {
      hostname: parsed.hostname,
      port: parsed.port || (isHttps ? 443 : 80),
      path: parsed.pathname + (parsed.search || ''),
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Content-Length': Buffer.byteLength(body),
        ...(apiKey ? { Authorization: `Bearer ${apiKey}` } : {}),
      },
      timeout: TIMEOUT_MS,
    };
    const lib = isHttps ? https : http;
    const req = lib.request(options, (res) => {
      let data = '';
      res.on('data', (c) => { data += c; });
      res.on('end', () => {
        if (res.statusCode >= 400) {
          reject(new Error(`Gateway HTTP ${res.statusCode}`));
        } else {
          try { resolve(JSON.parse(data)); } catch { reject(new Error('Response gateway bukan JSON')); }
        }
      });
    });
    req.on('timeout', () => { req.destroy(); reject(new Error('Gateway timeout')); });
    req.on('error', reject);
    req.write(body);
    req.end();
  });
}

async function callGateway(command, activeSkills) {
  const baseUrl = (process.env.GATEWAY_BASE_URL || '').trim();
  const apiKey  = process.env.GATEWAY_API_KEY || '';
  const model   = process.env.GATEWAY_MODEL   || '';

  if (!baseUrl || !model) {
    return { ...ruleParse(command), mode: 'fallback' };
  }

  const tools = activeSkills.map((s) => ({
    type: 'function',
    function: { name: s.function_name, description: s.description, parameters: s.parameters },
  }));

  try {
    const result = await httpPost(`${baseUrl}/v1/chat/completions`, {
      model,
      messages: [
        { role: 'system', content: buildSystemPrompt() },
        { role: 'user',   content: command },
      ],
      tools: tools.length ? tools : undefined,
      tool_choice: tools.length ? 'auto' : undefined,
      max_tokens: 512,
    }, apiKey);

    const choice = result.choices?.[0];
    if (!choice) throw new Error('Gateway tidak mengembalikan choice');

    const msg = choice.message;

    // Tool call path
    if (msg.tool_calls?.length) {
      const tc   = msg.tool_calls[0];
      const name = tc.function?.name ?? '';
      const args = (() => {
        try { return JSON.parse(tc.function?.arguments ?? '{}'); } catch { return {}; }
      })();

      const skill = activeSkills.find((s) => s.function_name === name);
      if (!skill) {
        return { type: 'text', text: 'Maaf, Batur tidak bisa menjalankan itu sekarang.', mode: 'cloud' };
      }
      return {
        type: 'tool_call',
        skill: skill.slug,
        function_call: { name, arguments: args },
        mode: 'cloud',
      };
    }

    // Text path
    const text = msg.content ?? 'Maaf, tidak ada respons.';
    return { type: 'text', text, mode: 'cloud' };

  } catch (err) {
    console.error('[gateway] error, fallback ke rule parser:', err.message);
    return { ...ruleParse(command), mode: 'fallback' };
  }
}

module.exports = { callGateway };
