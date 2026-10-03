import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, readFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { apiOrigin, configure } from './cloudflare-config.mjs';

test('cloud builds require a public HTTPS origin without secret-bearing URL parts', () => {
  for (const value of [undefined, '', 'http://api.example.test', 'https://localhost',
    'https://127.0.0.1', 'https://[::1]', 'https://user:pass@api.example.test',
    'https://api.example.test/api', 'https://api.example.test/?secret=value', 'https://api.example.test/#x']) {
    assert.throws(() => apiOrigin(value));
  }
  assert.equal(apiOrigin('https://api.example.test/'), 'https://api.example.test');
});

test('Cloudflare artifacts agree on API origin and SPA fallback', () => {
  const output = mkdtempSync(join(tmpdir(), 'tia-cloud-'));
  try {
    configure(output, 'https://api.example.test');
    assert.match(readFileSync(join(output, 'runtime-config.js'), 'utf8'), /"apiOrigin":"https:\/\/api.example.test"/);
    assert.equal(readFileSync(join(output, '_redirects'), 'utf8'), '/* /index.html 200\n');
    assert.match(readFileSync(join(output, '_headers'), 'utf8'), /connect-src 'self' https:\/\/api.example.test;/);
  } finally { rmSync(output, { recursive: true }); }
});
