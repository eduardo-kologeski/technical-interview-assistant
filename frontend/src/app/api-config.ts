type TiaRuntimeConfig = { apiUrl?: string };

declare global {
  interface Window { __TIA_CONFIG__?: TiaRuntimeConfig; }
}

const configuredApiUrl = typeof window !== 'undefined' ? window.__TIA_CONFIG__?.apiUrl?.trim() : undefined;

export const API_BASE_URL = (configuredApiUrl || '/api').replace(/\/$/, '');
