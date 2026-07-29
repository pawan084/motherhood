// Typed client for the Aira backend — the single place the web app talks to the
// safety-gated API. Identity is an anonymous device token minted on first use
// and cached in localStorage; every request carries it as a Bearer token.
//
// The important piece is `chatTurn`: the SERVER runs the safety gate, so a red
// message comes back as `{ urgent: true, urgent_help: {...}, reply: null }` and
// the UI routes to the urgent-help screen with a REAL care-team number — fixing
// the prototype's inert "Call care team" button and its missing server gate.
//
// There is deliberately NO client-side safety classifier here. The reference
// HTML prototype ships a "local prototype safety gate"; porting that would
// reintroduce exactly the drift the backend exists to eliminate. The only local
// keyword list is an offline FALLBACK used when the API is unreachable, and it
// fails toward the urgent screen rather than toward an AI answer.

const BASE = import.meta.env.VITE_API_URL || "http://127.0.0.1:8000";
const APP_TOKEN = import.meta.env.VITE_APP_TOKEN || "";
const TOKEN_KEY = "aira_session_token";

function getToken(): string | null {
  return typeof localStorage !== "undefined" ? localStorage.getItem(TOKEN_KEY) : null;
}
function setToken(t: string) {
  if (typeof localStorage !== "undefined") localStorage.setItem(TOKEN_KEY, t);
}

// One in-flight registration at a time: several screens mount together on first
// load, and without this each would race to mint its own anonymous user.
let registering: Promise<string> | null = null;

async function ensureToken(): Promise<string> {
  const existing = getToken();
  if (existing) return existing;
  if (!registering) {
    registering = (async () => {
      const res = await fetch(`${BASE}/device/register`, {
        method: "POST",
        headers: APP_TOKEN ? { "X-App-Token": APP_TOKEN } : {},
      });
      if (!res.ok) throw new Error(`device register failed (${res.status})`);
      const data = await res.json();
      setToken(data.token);
      return data.token as string;
    })().finally(() => { registering = null; });
  }
  return registering;
}

async function req<T>(path: string, opts: RequestInit = {}): Promise<T> {
  const token = await ensureToken();
  const res = await fetch(`${BASE}${path}`, {
    ...opts,
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${token}`,
      ...(APP_TOKEN ? { "X-App-Token": APP_TOKEN } : {}),
      ...(opts.headers || {}),
    },
  });
  if (res.status === 401) {
    // The token was revoked or the account was deleted — drop it so the next
    // call registers afresh instead of looping on a dead credential.
    clearSession();
  }
  if (!res.ok) {
    const body = await res.json().catch(() => ({}));
    throw new Error((body as { detail?: string }).detail || `HTTP ${res.status}`);
  }
  return res.json();
}

// ── types ─────────────────────────────────────────────────────────────────
export type Journey = "trying" | "pregnant" | "postpartum" | "exploring";
export type SafetyLevel = "green" | "amber" | "red";
export type ActionCard = { tool: string; title: string; detail: string } | null;
export type UrgentHelp = {
  headline: string;
  message: string;
  care_team: { name: string | null; phone: string | null };
  emergency_contact: { name: string | null; phone: string | null };
  show_emergency_services: boolean;
};
export type TurnResponse = {
  safety: { level: SafetyLevel; categories: string[]; degraded: boolean };
  urgent: boolean;
  reply: string | null;
  trust_label: "wellness" | "watchful" | null;
  action_card: ActionCard;
  disclaimer_needed?: boolean;
  urgent_help?: UrgentHelp;
};

export type NextAction = { tool: string; title: string; detail: string; minutes?: number };
export type TodayData = {
  name: string;
  journey: Journey;
  context_line: string;
  weeks: number | null;
  next_action: NextAction;
  all_clear: boolean;
  priorities: string[];
};
export type JourneySection = { title: string; text: string };
export type JourneyData = {
  journey: Journey;
  title: string;
  weeks: number | null;
  this_week: string;
  body: string;
  sections: JourneySection[];
};

export type User = {
  id: string; kind: string; email: string | null; name: string;
  journey: Journey | ""; language: string; onboarded: boolean;
};
export type CareItem = {
  id: string; kind: string; done: boolean; created: number;
  [key: string]: unknown;
};
export type CareData = {
  appointments: CareItem[];
  medicines_due: CareItem[];
  documents_count: number;
  reminders: CareItem[];
  care_plan: { total: number; on_track: number };
};
export type MemoryItem = {
  id: string; label: string; value: string; approved: boolean;
  source: string; created: number;
};
export type ConsentFeature = { key: string; label: string; granted: boolean; locked: boolean };
/** Voice options must match `prefs.VOICES` on the backend, which 400s anything else. */
export const VOICES = ["Aira warm", "Aira gentle", "Text only"] as const;
export type Prefs = { voice: string; spoken_replies: boolean };
export type ChatHistoryItem = { ts: number; role: string; text: string; safety_level: string };
export type EmergencyProfile = {
  stage?: string | null; name?: string | null;
  blood_group?: string; allergies?: string; hospital?: string; notes?: string;
  care_team_name?: string; care_team_phone?: string;
  emergency_contact_name?: string; emergency_contact_phone?: string;
};

// ── endpoints ─────────────────────────────────────────────────────────────

// The exact string `POST /v1/account/delete` requires, so an irreversible wipe
// can't be triggered by a stray request. Must match privacy.DELETE_CONFIRMATION.
export const DELETE_CONFIRMATION = "DELETE MY DATA";

/** Unauthenticated liveness probe. Used to seed the header's trust state, so we
 *  don't claim full "Safety checked" screening before knowing whether the LLM
 *  classifier is even configured — with it down, only the keyword floor runs. */
export async function health(): Promise<{ ok: boolean; llm_configured: boolean }> {
  const res = await fetch(`${BASE}/health`);
  if (!res.ok) throw new Error(`health ${res.status}`);
  return res.json();
}

export const AiraAPI = {
  // identity + profile
  me: () => req<{ user: User }>("/account/me"),
  updateProfile: (p: { name?: string; journey?: Journey; language?: string }) =>
    req<{ user: User }>("/account/profile", { method: "PATCH", body: JSON.stringify(p) }),

  // the safety-gated turn
  chatTurn: (message: string, history: { role: string; content: string }[] = []) =>
    req<TurnResponse>("/v1/chat/turn", {
      method: "POST",
      body: JSON.stringify({ message, history }),
    }),
  chatHistory: (limit = 50) =>
    req<{ items: ChatHistoryItem[] }>(`/v1/chat/history?limit=${limit}`),

  onboarding: (payload: {
    journey: Journey; name?: string; language?: string;
    priorities?: string[]; weeks?: number;
  }) => req<TodayData>("/v1/onboarding", { method: "POST", body: JSON.stringify(payload) }),

  today: () => req<TodayData>("/v1/today"),
  journey: () => req<JourneyData>("/v1/journey"),

  // care
  care: () => req<CareData>("/v1/care"),
  reminders: () => req<{ items: CareItem[] }>("/v1/care/reminders"),
  addReminder: (b: { title: string; time?: string; repeat?: string; private_label?: boolean }) =>
    req<CareItem>("/v1/care/reminders", { method: "POST", body: JSON.stringify(b) }),
  // Toggleable, unlike a medicine dose: a reminder ticked by mistake has to be
  // reversible, so this takes the target state rather than being a one-way mark.
  setReminderDone: (id: string, done: boolean) =>
    req<{ ok: boolean; done: boolean }>(`/v1/care/reminders/${id}/done`, {
      method: "POST", body: JSON.stringify({ done }),
    }),
  medicines: () => req<{ items: CareItem[] }>("/v1/care/medicines"),
  addMedicine: (b: { name: string; dose?: string; schedule?: string; time?: string }) =>
    req<CareItem>("/v1/care/medicines", { method: "POST", body: JSON.stringify(b) }),
  markMedicineTaken: (id: string) =>
    req<{ ok: boolean }>(`/v1/care/medicines/${id}/taken`, { method: "POST" }),
  appointments: () => req<{ items: CareItem[] }>("/v1/care/appointments"),
  addAppointment: (b: { doctor: string; place?: string; when?: string; notes?: string }) =>
    req<CareItem>("/v1/care/appointments", { method: "POST", body: JSON.stringify(b) }),
  documents: () => req<{ items: CareItem[] }>("/v1/care/documents"),
  // Multipart, so it bypasses `req` — setting Content-Type by hand would strip
  // the boundary the server needs to parse the body.
  uploadDocument: async (file: File, kind: string) => {
    const token = await ensureToken();
    const form = new FormData();
    form.append("file", file);
    form.append("kind", kind);
    const res = await fetch(`${BASE}/v1/care/documents`, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${token}`,
        ...(APP_TOKEN ? { "X-App-Token": APP_TOKEN } : {}),
      },
      body: form,
    });
    if (!res.ok) {
      const body = await res.json().catch(() => ({}));
      throw new Error((body as { detail?: string }).detail || `HTTP ${res.status}`);
    }
    return res.json() as Promise<CareItem>;
  },
  addCheckin: (b: { feeling?: string; sleep_hours?: number; note?: string }) =>
    req<CareItem>("/v1/care/checkin", { method: "POST", body: JSON.stringify(b) }),
  addSymptom: (b: { what: string; severity?: string; started?: string; pattern?: string }) =>
    req<CareItem>("/v1/care/symptom", { method: "POST", body: JSON.stringify(b) }),

  // memory + consent
  memory: () => req<{ items: MemoryItem[] }>("/v1/memory"),
  addMemory: (b: { label: string; value: string }) =>
    req<MemoryItem>("/v1/memory", { method: "POST", body: JSON.stringify(b) }),
  setMemoryApproved: (id: string, approved: boolean) =>
    req<{ ok: boolean }>(`/v1/memory/${id}`, { method: "PATCH", body: JSON.stringify({ approved }) }),
  forgetMemory: (id: string) => req<{ ok: boolean }>(`/v1/memory/${id}`, { method: "DELETE" }),

  // Voice preference. Stored for real, but spoken replies don't exist in this
  // build — the UI says so rather than implying the setting does something now.
  prefs: () => req<Prefs>("/v1/prefs"),
  setPrefs: (p: Partial<Prefs>) =>
    req<Prefs>("/v1/prefs", { method: "PUT", body: JSON.stringify(p) }),

  consent: () => req<{ features: ConsentFeature[] }>("/v1/consent"),
  setConsent: (feature: string, granted: boolean) =>
    req<{ ok: boolean; features: ConsentFeature[] }>("/v1/consent", {
      method: "POST", body: JSON.stringify({ feature, granted }),
    }),
  consentHistory: () =>
    req<{ history: { feature: string; granted: boolean; ts: number; note: string }[] }>(
      "/v1/consent/history"),

  // emergency profile — the source of truth for the urgent dialer
  emergencyProfile: () => req<EmergencyProfile>("/v1/emergency-profile"),
  putEmergencyProfile: (p: EmergencyProfile) =>
    req<EmergencyProfile>("/v1/emergency-profile", { method: "PUT", body: JSON.stringify(p) }),

  reportAnswer: (message: string, ref?: string) =>
    req("/v1/feedback/report", {
      method: "POST",
      body: JSON.stringify({ kind: "clinical", message, ref }),
    }),
  sendFeedback: (kind: string, message: string) =>
    req("/v1/feedback", { method: "POST", body: JSON.stringify({ kind, message }) }),

  // Everything Aira holds for this user, as one JSON document.
  exportAccount: () => req<{ user_id: string; exported_at: number; data: unknown }>(
    "/v1/account/export"),

  // Irreversible. The backend requires the exact confirmation string, and the
  // session token stops working the moment it succeeds.
  deleteAccount: () => req<{ ok: boolean; deleted: Record<string, number> }>(
    "/v1/account/delete",
    { method: "POST", body: JSON.stringify({ confirm: DELETE_CONFIRMATION }) }),
};

/** Drop the cached session token — call after deleting the account, so the next
 *  request registers a fresh anonymous user instead of reusing a dead token. */
export function clearSession() {
  if (typeof localStorage !== "undefined") localStorage.removeItem(TOKEN_KEY);
}

/** Save an object to the user's device as a JSON file. */
export function downloadJson(filename: string, data: unknown) {
  const url = URL.createObjectURL(
    new Blob([JSON.stringify(data, null, 2)], { type: "application/json" }));
  const a = document.createElement("a");
  a.href = url;
  a.download = filename;
  a.click();
  URL.revokeObjectURL(url);
}

// Build a `tel:` href for the urgent-help "Call care team" button. Falls back to
// null when no number is on file — the UI then shows local-emergency guidance
// instead of an inert button.
export function telHref(phone: string | null | undefined): string | null {
  if (!phone) return null;
  return `tel:${phone.replace(/[^\d+]/g, "")}`;
}
