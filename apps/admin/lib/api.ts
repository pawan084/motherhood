// Typed client for the Aira backend admin API.
//
// Auth uses an httpOnly `admin_session` cookie the backend sets on login — the
// token is never read by this SPA. CSRF is a double-submit: the backend also
// sets a non-httpOnly `csrf_token` cookie, which we echo in `X-CSRF-Token` on
// every mutating request. We only persist the non-sensitive `role` for UI gating.
const BASE = process.env.NEXT_PUBLIC_API_URL || "http://127.0.0.1:8000";
export const API_BASE = BASE;
const ROLE_KEY = "aira_admin_role";

export function getRole(): string | null {
  return typeof window !== "undefined" ? localStorage.getItem(ROLE_KEY) : null;
}
export function setRole(r: string | null) {
  if (typeof window === "undefined") return;
  if (r) localStorage.setItem(ROLE_KEY, r);
  else localStorage.removeItem(ROLE_KEY);
}

const ROLE_RANK: Record<string, number> = { viewer: 0, support: 1, owner: 2 };
export function roleAtLeast(min: string, role: string | null = getRole()): boolean {
  return (ROLE_RANK[role || ""] ?? -1) >= (ROLE_RANK[min] ?? 99);
}

function getCsrfToken(): string | null {
  if (typeof document === "undefined") return null;
  const m = document.cookie.match(/(?:^|;\s*)csrf_token=([^;]*)/);
  return m ? decodeURIComponent(m[1]) : null;
}

const MUTATING = new Set(["POST", "PUT", "DELETE", "PATCH"]);
function csrfHeaders(method?: string): Record<string, string> {
  if (!MUTATING.has((method || "GET").toUpperCase())) return {};
  const token = getCsrfToken();
  return token ? { "X-CSRF-Token": token } : {};
}

export async function api<T>(path: string, opts: RequestInit = {}): Promise<T> {
  const method = (opts.method || "GET").toUpperCase();
  // If the CSRF cookie is gone (expired), a mutating request can only 403 —
  // treat the session as ended and send the admin back to login.
  if (MUTATING.has(method) && typeof document !== "undefined" && !getCsrfToken()) {
    setRole(null);
    window.location.href = "/login";
    throw new Error("session expired");
  }
  const res = await fetch(`${BASE}/admin${path}`, {
    ...opts,
    credentials: "include",
    headers: {
      "Content-Type": "application/json",
      ...csrfHeaders(opts.method),
      ...(opts.headers || {}),
    },
  });
  if (res.status === 401) {
    setRole(null);
    if (typeof window !== "undefined") window.location.href = "/login";
    throw new Error("unauthorized");
  }
  if (!res.ok) {
    if (res.status === 429) throw new Error("Rate limited — wait a moment and retry.");
    const body = await res.json().catch(() => ({}));
    throw new Error((body as any).detail || `HTTP ${res.status}`);
  }
  return res.json();
}

export async function login(email: string, password: string) {
  let res: Response;
  try {
    res = await fetch(`${BASE}/admin/login`, {
      method: "POST",
      credentials: "include",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email, password }),
    });
  } catch {
    throw new Error("Couldn't reach the server — check your connection and try again.");
  }
  if (res.status === 401) throw new Error("Invalid credentials");
  if (!res.ok) throw new Error(`Couldn't reach the server (HTTP ${res.status}).`);
  const data = await res.json();
  setRole(data.role ?? null);
  return data as { role: string; email: string };
}

export async function logout() {
  try {
    await fetch(`${BASE}/admin/logout`, {
      method: "POST",
      credentials: "include",
      headers: { ...csrfHeaders("POST") },
    });
  } finally {
    setRole(null);
  }
}

// ── response types ──────────────────────────────────────────────────────────
export type Overview = {
  metrics: {
    total_users: number; accounts: number; dau: number; wau: number; mau: number;
    chat_turns: number; new_users_by_day: Record<string, number>;
    events_by_name: Record<string, number>; range_days: number;
  };
  safety: { total: number; red: number; amber: number; unreviewed: number; degraded: number };
  feedback: { total: number; open: number; reports: number };
};
export type UserRow = {
  id: string; kind: string; email: string | null; name: string; journey: string;
  language: string; onboarded: boolean; created: number; last_seen: number | null;
};
export type SafetyFlag = {
  id: number; ts: number; user_id: string; level: string; categories: string[];
  // Empty (and `message_redacted: true`) for admins below `support` — the
  // backend withholds the user's verbatim health text from `viewer`.
  message: string; message_redacted: boolean;
  degraded: boolean; reviewed: boolean; reviewed_by: string; note: string;
};
export type FeedbackRow = {
  id: string; user_id: string; kind: string; message: string; ref: string;
  ts: number; status: string; handled_by: string;
};
export type ContentEntry = {
  key: string; journey: string; title: string; body: string; version: number;
  status: string; reviewed_by: string; reviewed_at: number | null; updated: number | null;
};
export type PromptRow = {
  key: string; text: string; model: string; updated: number | null;
  updated_by: string; is_default: boolean; has_default: boolean;
  /** This prompt is a safety control, not copy — see SAFETY_CRITICAL_PROMPTS. */
  owner_only: boolean;
  /** Whether THIS admin may change it. Sent by the server rather than worked
   *  out here: a list of protected keys kept in the console is one that drifts
   *  the first time somebody adds to the server's, and it drifts silently in
   *  the permissive direction. */
  can_edit: boolean;
};
export type System = {
  env: string; db: { engine: string };
  providers: { name: string; status: string; ms: number | null; error?: string }[];
  auth: { app_token_required: boolean; google_signin: boolean };
};
export type VideoAdminItem = {
  id: string; slug: string; title: string;
  category: string; category_label: string; journeys: string[];
  timing: { type: string; start_week: number | null; end_week: number | null };
  content_format: string;
  duration: { min_seconds: number; max_seconds: number };
  description: string; safety_level: string;
  in_app_actions: string[]; languages: string[];
  status: string;
  clinical_review: {
    required: boolean; specialties: string[]; status: string;
    reviewed_by?: string | null; reviewed_at?: number | null;
  };
  playable: boolean;
};
export type VideosAdminResp = {
  items: VideoAdminItem[];
  summary: { total: number; published: number; approved: number; pending: number; urgent: number };
};
