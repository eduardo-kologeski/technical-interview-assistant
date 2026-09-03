import { mkdir, writeFile } from 'node:fs/promises';
import { execFileSync } from 'node:child_process';

const apiUrl = process.env.API_URL?.trim();
if (!apiUrl) {
  console.error('API_URL is required for the Cloudflare Pages production build.');
  process.exit(1);
}
if (!/^https:\/\//i.test(apiUrl)) {
  console.error('API_URL must be an HTTPS URL in production.');
  process.exit(1);
}

execFileSync(process.platform === 'win32' ? 'npx.cmd' : 'npx', ['ng', 'build', '--configuration', 'production'], { stdio: 'inherit' });
const outputDir = 'dist/frontend/browser';
await mkdir(outputDir, { recursive: true });
await writeFile(`${outputDir}/config.js`, `window.__TIA_CONFIG__ = { apiUrl: ${JSON.stringify(apiUrl.replace(/\/$/, '') + '/api')} };\n`);
