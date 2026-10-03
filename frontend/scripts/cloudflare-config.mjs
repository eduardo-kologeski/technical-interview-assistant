import { writeFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

export function apiOrigin(value) {
  let url;
  try { url = new URL(value); } catch { throw new Error('API_URL must be the public HTTPS backend origin.'); }
  if (url.protocol !== 'https:' || url.username || url.password || url.pathname !== '/'
      || url.search || url.hash || /^(localhost|127\..*|\[::1\])$/i.test(url.hostname)) {
    throw new Error('API_URL must be an HTTPS origin without credentials, path, query or fragment.');
  }
  return url.origin;
}

export function configure(output, value) {
  const origin = apiOrigin(value);
  writeFileSync(resolve(output, 'runtime-config.js'), `window.TIA_CONFIG = ${JSON.stringify({ apiOrigin: origin })};\n`);
  writeFileSync(resolve(output, '_redirects'), '/* /index.html 200\n');
  writeFileSync(resolve(output, '_headers'), `/*
  X-Content-Type-Options: nosniff
  Referrer-Policy: strict-origin-when-cross-origin
  X-Frame-Options: DENY
  Permissions-Policy: camera=(), microphone=(), geolocation=()
  Content-Security-Policy: default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self' data:; connect-src 'self' ${origin}; object-src 'none'; base-uri 'self'; frame-ancestors 'none'; form-action 'self'
/runtime-config.js
  Cache-Control: no-store
/index.html
  Cache-Control: no-cache
`);
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  configure(resolve('dist/frontend/browser'), process.env.API_URL);
}
