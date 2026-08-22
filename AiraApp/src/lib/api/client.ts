import AsyncStorage from '@react-native-async-storage/async-storage';
import { Platform } from 'react-native';

/**
 * Transport for the Aira backend.
 *
 * Everything the app knows about HTTP lives here: the base URL, the two auth
 * headers, the error shape, and the token lifecycle. Screens and slices see
 * typed camelCase objects and never a fetch call.
 */

/**
 * Where the backend is.
 *
 * `10.0.2.2` is the host machine as seen from the Android emulator — `localhost`
 * there is the emulator itself, which is the most common reason a freshly
 * cloned RN app cannot reach a backend that is plainly running. A physical
 * device needs the LAN IP, so this is overridable.
 */
const DEFAULT_BASE = Platform.select({
  android: 'http://10.0.2.2:8000',
  default: 'http://127.0.0.1:8000',
});

export const API_BASE = process.env.EXPO_PUBLIC_API_URL || DEFAULT_BASE;

/**
 * The coarse edge gate.
 *
 * Production mandates `APP_SHARED_SECRET`, and without this header EVERY route
 * 401s — including `/device/register`, so the app cannot even reach first run.
 * It ships inside the bundle by design: a gate against casual traffic, not a
 * secret. The real authorisation is the per-user session token.
 */
const APP_TOKEN = process.env.EXPO_PUBLIC_APP_TOKEN || '';

const TOKEN_KEY = 'aira_session_token';

export class ApiError extends Error {
  status: number;
  constructor(message: string, status: number) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
  }
}

// ── token ────────────────────────────────────────────────────────────────────

let cachedToken: string | null = null;

export async function getToken(): Promise<string | null> {
  if (cachedToken) return cachedToken;
  cachedToken = await AsyncStorage.getItem(TOKEN_KEY);
  return cachedToken;
}

export async function setToken(token: string): Promise<void> {
  cachedToken = token;
  await AsyncStorage.setItem(TOKEN_KEY, token);
}

export async function clearToken(): Promise<void> {
  cachedToken = null;
  await AsyncStorage.removeItem(TOKEN_KEY);
}

/**
 * Mint an anonymous identity, once.
 *
 * The in-flight promise is not an optimisation. Several screens mount together
 * on first load; without it each races to register, and the app ends up with
 * several anonymous users — only one of which the token points at. The rest
 * hold care data nobody can ever reach again.
 */
let registering: Promise<string> | null = null;

export async function ensureToken(): Promise<string> {
  const existing = await getToken();
  if (existing) return existing;

  if (!registering) {
    registering = (async () => {
      const res = await fetch(`${API_BASE}/device/register`, {
        method: 'POST',
        headers: APP_TOKEN ? { 'X-App-Token': APP_TOKEN } : {},
      });
      if (!res.ok) throw new ApiError('device register failed', res.status);
      const data = (await res.json()) as { token: string };
      await setToken(data.token);
      return data.token;
    })().finally(() => {
      registering = null;
    });
  }
  return registering;
}

// ── request ──────────────────────────────────────────────────────────────────

type RequestOptions = {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
  body?: unknown;
  /** Skip `ensureToken` — for routes that MINT a session rather than use one. */
  anonymous?: boolean;
};

export async function request<T>(path: string, opts: RequestOptions = {}): Promise<T> {
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  if (APP_TOKEN) headers['X-App-Token'] = APP_TOKEN;

  if (!opts.anonymous) {
    headers.Authorization = `Bearer ${await ensureToken()}`;
  }

  const res = await fetch(`${API_BASE}${path}`, {
    method: opts.method ?? 'GET',
    headers,
    body: opts.body === undefined ? undefined : JSON.stringify(opts.body),
  });

  if (res.status === 401) {
    // Revoked, or the account was deleted. Drop it so the next call registers
    // afresh instead of looping on a dead credential.
    await clearToken();
  }

  if (!res.ok) {
    const detail = await res.json().catch(() => ({}));
    throw new ApiError(
      (detail as { detail?: string }).detail || `HTTP ${res.status}`,
      res.status,
    );
  }

  if (res.status === 204) return undefined as T;
  return (await res.json()) as T;
}

/**
 * Multipart upload — bypasses `request` because setting Content-Type by hand
 * strips the boundary the server needs to parse the body.
 */
export async function upload<T>(
  path: string,
  file: { uri: string; name: string; type: string },
  fields: Record<string, string> = {},
): Promise<T> {
  const token = await ensureToken();
  const form = new FormData();
  form.append('file', file as unknown as Blob);
  for (const [k, v] of Object.entries(fields)) form.append(k, v);

  const headers: Record<string, string> = { Authorization: `Bearer ${token}` };
  if (APP_TOKEN) headers['X-App-Token'] = APP_TOKEN;

  const res = await fetch(`${API_BASE}${path}`, { method: 'POST', headers, body: form });
  if (!res.ok) {
    const detail = await res.json().catch(() => ({}));
    throw new ApiError(
      (detail as { detail?: string }).detail || `HTTP ${res.status}`,
      res.status,
    );
  }
  return (await res.json()) as T;
}

/** Unauthenticated liveness probe — also reports whether the LLM is configured. */
export async function health(): Promise<{ ok: boolean; llmConfigured: boolean }> {
  const res = await fetch(`${API_BASE}/health`);
  if (!res.ok) throw new ApiError('health check failed', res.status);
  const d = (await res.json()) as { ok: boolean; llm_configured: boolean };
  return { ok: d.ok, llmConfigured: d.llm_configured };
}
