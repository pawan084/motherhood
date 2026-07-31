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

/** Google client ID, if this deployment has one. Empty means Google Sign-In is
 *  not configured — the UI must then say so rather than offering a button that
 *  can only fail (`POST /account/google` 503s without a server-side client ID). */
export const GOOGLE_CLIENT_ID = import.meta.env.VITE_GOOGLE_CLIENT_ID || "";

function getToken(): string | null {
  return typeof localStorage !== "undefined" ? localStorage.getItem(TOKEN_KEY) : null;
}
function setToken(t: string) {
  if (typeof localStorage !== "undefined") localStorage.setItem(TOKEN_KEY, t);
}

// The emergency profile, kept on the device so "Available offline" is true.
//
// Keyed by session token: a shared computer that signs out and back in as
// somebody else must not show the first person's care-team number. Clearing the
// session leaves the entry orphaned rather than readable, since the key can no
// longer be derived.
const EMERGENCY_KEY = "aira_emergency_profile";

function emergencyCacheKey(): string | null {
  const token = getToken();
  return token ? `${EMERGENCY_KEY}:${token.slice(-24)}` : null;
}

function writeEmergencyCache(p: unknown) {
  const key = emergencyCacheKey();
  if (!key || typeof localStorage === "undefined") return;
  try {
    localStorage.setItem(key, JSON.stringify(p));
  } catch {
    // A full or blocked store is not a reason to fail the request that
    // succeeded; the caller already has the live copy.
  }
}

function readEmergencyCache(): Record<string, string> | null {
  const key = emergencyCacheKey();
  if (!key || typeof localStorage === "undefined") return null;
  try {
    const raw = localStorage.getItem(key);
    return raw ? (JSON.parse(raw) as Record<string, string>) : null;
  } catch {
    return null;
  }
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
export type Journey = "trying" | "pregnant" | "postpartum" | "loss" | "exploring";
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
  /** The week Aira has counted forward to — what every screen shows. */
  weeks: number | null;
  /** The week the person last TYPED, and what an editor must prefill with.
   *
   *  Prefilling from `weeks` instead would nudge the date forward by however
   *  long it had been since they told us: open the editor at week 25 after a
   *  week has passed, press save without touching it, and the pregnancy has
   *  silently gained seven days. */
  weeks_reported: number | null;
  next_action: NextAction;
  priorities: string[];
};
export type JourneySection = { title: string; text: string };
/** What is worth a call rather than a wait, for a pregnancy week.
 *
 *  `reviewed` is false while the copy is the backend's in-code seed, which was
 *  drafted from the app's own red-flag list rather than written by a clinician.
 *  It turns true once an admin publishes an edit. The screen shows the
 *  difference — claiming review it has not had would be the largest overclaim
 *  in the product. */
export type CallTip = { title: string; body: string; reviewed: boolean };
export type JourneyData = {
  journey: Journey;
  title: string;
  weeks: number | null;
  this_week: string;
  body: string;
  sections: JourneySection[];
  /** Absent for anyone not pregnant, and for a pregnancy with no known week —
   *  the signals differ by stage, so without a week there is nothing honest to
   *  say. */
  call_tip?: CallTip;
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
export type ConsentFeature = {
  key: string; label: string; granted: boolean;
  /** A permanent policy denial (health data for ads) — a statement, not a control. */
  locked: boolean;
  /** False when the feature doesn't exist in this build, so the consent governs
   *  nothing and the server refuses to record a grant for it. */
  available: boolean;
};
/** Voice options must match `prefs.VOICES` on the backend, which 400s anything else. */
export const VOICES = ["Aira warm", "Aira gentle", "Text only"] as const;
export type Prefs = { voice: string; spoken_replies: boolean };

export type PartnerScopes = {
  appointments: boolean;
  reminders: boolean;
  /** Off by default. Even granted, this yields counts — never the text of a
   *  symptom log or a private check-in note. */
  health_details: boolean;
};
export type PartnerInvite = {
  id: string; code: string; scopes: PartnerScopes; expires: number; share_text: string;
};
export type PartnerInviteRow = {
  id: string;
  /** Present only while the invite is still redeemable — the server stops
   *  echoing a spent code. */
  code?: string;
  scopes: PartnerScopes;
  state: "pending" | "accepted" | "revoked" | "expired";
  created: number; expires: number; accepted: number | null;
};
export type PartnerShare = {
  invite_id: string;
  shared_by: string;
  scopes: PartnerScopes;
  data: {
    appointments?: CareItem[];
    reminders?: CareItem[];
    medicines?: CareItem[];
    symptom_count?: number;
    checkin_count?: number;
    documents_count?: number;
  };
};
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

/**
 * Exchange a Google ID token for an Aira session.
 *
 * Sends the current anonymous device token too: the backend promotes that user
 * in place when it can, so everything they did before signing in — onboarding,
 * care items, memory — carries over instead of being stranded on an account
 * they can no longer reach.
 *
 * Bypasses `req` because it must NOT call ensureToken(): the whole point is to
 * replace the session, and the device token is a body field here, not auth.
 */
export async function signInWithGoogle(idToken: string): Promise<User> {
  const res = await fetch(`${BASE}/account/google`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      ...(APP_TOKEN ? { "X-App-Token": APP_TOKEN } : {}),
    },
    body: JSON.stringify({ id_token: idToken, device_token: getToken() }),
  });
  if (!res.ok) {
    const body = await res.json().catch(() => ({}));
    throw new Error((body as { detail?: string }).detail
      || (res.status === 503 ? "Google Sign-In isn't configured on the server."
                             : `Sign-in failed (${res.status})`));
  }
  const data = await res.json();
  setToken(data.token);
  return data.user as User;
}

/** Matches accounts.MIN_PASSWORD_LENGTH; the server rejects anything shorter. */
export const MIN_PASSWORD_LENGTH = 12;

/**
 * Post to an auth endpoint that MINTS a session rather than using one.
 *
 * Deliberately bypasses `req`, for the same reason `signInWithGoogle` does: it
 * must not call ensureToken(), because the whole point is to replace the
 * session. Calling ensureToken() here would register a throwaway anonymous user
 * on every sign-in attempt, including the failed ones.
 */
async function authPost(path: string, body: unknown, fallback: string): Promise<User> {
  const res = await fetch(`${BASE}${path}`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      ...(APP_TOKEN ? { "X-App-Token": APP_TOKEN } : {}),
    },
    body: JSON.stringify(body),
  });
  if (!res.ok) {
    const detail = await res.json().catch(() => ({}));
    throw new Error((detail as { detail?: string }).detail || `${fallback} (${res.status})`);
  }
  const data = await res.json();
  setToken(data.token);
  return data.user as User;
}

/**
 * Create an email/password account, carrying this browser's anonymous care
 * data with it.
 *
 * `device_token` is what makes that carry-over happen: the backend promotes the
 * anonymous row in place — same user id — so onboarding answers, care items and
 * memory all survive. Sign-UP keeps your data; sign-IN switches to the
 * account's own, which is why the dialog warns before the latter.
 */
export async function signUp(email: string, password: string): Promise<User> {
  return authPost("/account/signup", { email, password, device_token: getToken() },
                  "Couldn't create your account");
}

export async function signIn(email: string, password: string): Promise<User> {
  return authPost("/account/login", { email, password }, "Couldn't sign you in");
}

/**
 * Sign out everywhere, then forget the token locally.
 *
 * The server bumps token_version, so every issued token stops verifying — a
 * shared or lost device can't keep the session alive. Dropping the local copy
 * afterwards means the next request registers a fresh anonymous user, which is
 * the state the app is designed to run in anyway.
 */
export async function signOut(): Promise<void> {
  try {
    await req("/account/logout", { method: "POST" });
  } finally {
    // Even if the call fails — offline, revoked already — this browser must
    // stop holding a credential the user has asked it to forget.
    clearSession();
  }
}

/** How much care data is on this device's anonymous session, so the sign-in
 *  path can say what signing in would leave behind rather than discovering it
 *  afterwards. */
export async function localCareItemCount(): Promise<number> {
  try {
    const [care, timeline] = await Promise.all([AiraAPI.care(), AiraAPI.timeline()]);
    return care.appointments.length + care.medicines_due.length +
      care.reminders.length + care.documents_count + timeline.items.length;
  } catch {
    return 0;
  }
}

// Educational video library (served by GET /v1/videos). Snake_case mirrors the
// backend payload; the `saved` flag is per-item for the signed-in user.
export type VideoTiming = { type: "gestational_week" | "on_demand"; start_week: number | null; end_week: number | null };
export type VideoTopic = {
  id: string; slug: string; title: string;
  category: string; category_label: string;
  journeys: string[]; timing: VideoTiming;
  content_format: string;
  duration: { min_seconds: number; max_seconds: number };
  description: string;
  safety_level: "standard" | "clinical" | "urgent";
  in_app_actions: string[]; languages: string[];
  status: string;
  clinical_review: { required: boolean; specialties: string[]; status: string };
  playable: boolean;
  /** Where the video is, when there is one. Null for every topic today — the
   *  topics are written, not filmed. `playable` already requires this to be
   *  present, so the two move together. */
  media_url: string | null;
  /** True when media_url is the backend's stand-in rather than a produced
   *  video, so the screen can say so instead of presenting it as the real
   *  thing. False in any build without demo media switched on. */
  media_is_placeholder: boolean;
  saved?: boolean;
};
export type VideosResponse = {
  items: VideoTopic[];
  week_video: VideoTopic | null;
  categories: { key: string; label: string }[];
  saved_ids: string[];
};
/** "2–4 min" from a topic's recommended duration range. */
/** An API-relative path made absolute, so a served placeholder can be opened.
 *  Anything already absolute is returned untouched. */
export function mediaHref(path: string): string {
  return /^https?:\/\//.test(path) ? path : `${BASE}${path.startsWith("/") ? "" : "/"}${path}`;
}

export function videoDurationLabel(v: VideoTopic): string {
  const lo = Math.max(1, Math.round(v.duration.min_seconds / 60));
  const hi = Math.max(lo, Math.round(v.duration.max_seconds / 60));
  return lo === hi ? `${lo} min` : `${lo}–${hi} min`;
}

export const AiraAPI = {
  // identity + profile
  me: () => req<{ user: User }>("/account/me"),
  updateProfile: (p: { name?: string; journey?: Journey; language?: string }) =>
    req<{ user: User }>("/account/profile", { method: "PATCH", body: JSON.stringify(p) }),

  /** Correct the pregnancy week, or change what Aira focuses on.
   *
   *  The endpoint has existed since care context did; web simply never called
   *  it, so a week mistyped during onboarding could not be corrected here at
   *  all. Setting it restarts the clock server-side, counting forward from what
   *  was just entered. */
  updateCareContext: (p: { weeks?: number | null; priorities?: string[] }) =>
    req<TodayData>("/v1/care/context", { method: "PATCH", body: JSON.stringify(p) }),

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
  // Every care kind was create-only: a typo was permanent, a cancelled
  // appointment stayed forever, and a stopped medicine went on reading as due.
  updateCareItem: (id: string, fields: Record<string, unknown>) =>
    req<CareItem>(`/v1/care/items/${id}`, { method: "PATCH", body: JSON.stringify(fields) }),
  deleteCareItem: (id: string) =>
    req<{ ok: boolean }>(`/v1/care/items/${id}`, { method: "DELETE" }),
  /** Check-ins and symptom logs — the "timeline" the tools have always named. */
  timeline: () => req<{ items: CareItem[] }>("/v1/care/timeline"),

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

  // Partner access. Gated server-side on `partner_access` consent, which
  // defaults to off — creating an invite 403s until the user turns it on, and
  // turning it back off cuts every accepted partner off on their next request.
  partnerInvites: () => req<{ items: PartnerInviteRow[] }>("/v1/partner/invites"),
  createPartnerInvite: (scopes: PartnerScopes) =>
    req<PartnerInvite>("/v1/partner/invite", { method: "POST", body: JSON.stringify(scopes) }),
  revokePartnerInvite: (id: string) =>
    req<{ ok: boolean }>(`/v1/partner/invites/${id}/revoke`, { method: "POST" }),
  acceptPartnerInvite: (code: string) =>
    req<{ ok: boolean; shared_by: string }>("/v1/partner/accept", {
      method: "POST", body: JSON.stringify({ code: code.trim() }),
    }),
  partnerShared: () => req<{ items: PartnerShare[] }>("/v1/partner/shared"),

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
  //
  // Kept in localStorage as well as fetched. The panel is badged "Available
  // offline" and was not: with no connection the editor rendered every field
  // blank, on the one screen holding the care-team number, the emergency
  // contact and the allergies — wanted at exactly the moment someone may have
  // neither signal nor patience.
  //
  // Blank was also dangerous rather than merely unhelpful. The endpoint replaces
  // the stored profile with what it is sent, so saving from a form that failed
  // to load would have written six empty fields over a real one.
  emergencyProfile: async (): Promise<EmergencyProfile> => {
    const p = await req<EmergencyProfile>("/v1/emergency-profile");
    writeEmergencyCache(p);
    return p;
  },
  /** The last profile seen on this device, or null if there has never been one. */
  cachedEmergencyProfile: readEmergencyCache,
  putEmergencyProfile: async (p: EmergencyProfile) => {
    const saved = await req<EmergencyProfile>("/v1/emergency-profile", {
      method: "PUT", body: JSON.stringify(p),
    });
    writeEmergencyCache(saved);
    return saved;
  },

  reportAnswer: (message: string, ref?: string) =>
    req("/v1/feedback/report", {
      method: "POST",
      body: JSON.stringify({ kind: "clinical", message, ref }),
    }),
  sendFeedback: (kind: string, message: string) =>
    req("/v1/feedback", { method: "POST", body: JSON.stringify({ kind, message }) }),

  // Educational video library. The server resolves journey + gestational week
  // from the caller's own profile + care context, so no params are needed.
  videos: () => req<VideosResponse>("/v1/videos"),
  savedVideos: () => req<{ items: VideoTopic[] }>("/v1/videos/saved"),
  saveVideo: (id: string) =>
    req<{ saved: boolean; video_id: string }>(`/v1/videos/${id}/save`, { method: "POST" }),
  unsaveVideo: (id: string) =>
    req<{ saved: boolean; video_id: string }>(`/v1/videos/${id}/save`, { method: "DELETE" }),

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

/** Provision a one-tap demo: a FRESH anonymous user (so it never merges with
 *  whatever is already in this browser), onboarded as a week-24 pregnancy and
 *  pre-filled with a little care data, so the app opens looking lived-in. Uses
 *  the ordinary endpoints — no special demo account on the server to keep in
 *  sync, and every demo session is isolated. */
export async function demoSignIn(): Promise<void> {
  clearSession();
  await AiraAPI.onboarding({
    journey: "pregnant",
    name: "Demo",
    language: "English",
    weeks: 24,
    priorities: ["Better sleep", "Nutrition", "Visit preparation"],
  });
  // Best-effort sample content — a failed row shouldn't block entering the demo.
  await Promise.allSettled([
    AiraAPI.addMedicine({ name: "Prenatal vitamin", dose: "1 tablet", schedule: "Daily", time: "9:00 AM" }),
    AiraAPI.addReminder({ title: "Drink a glass of water", time: "2:00 PM", repeat: "Daily" }),
    AiraAPI.addAppointment({ doctor: "Dr. Nadia Rahman", place: "Riverside Clinic", when: "Thu 14 Aug, 10:30 AM" }),
    AiraAPI.addCheckin({ feeling: "Tired", sleep_hours: 6, note: "woke at 3am" }),
  ]);
}

/** Save an object to the user's device as a JSON file. */
/**
 * Download everything as a zip: the records plus the Care Vault's actual files.
 *
 * The JSON export called itself "everything Aira holds" while the documents —
 * someone's scans and prescriptions — stayed on the server. Listing a file in
 * an export is not exporting it.
 */
export async function downloadAccountArchive(): Promise<void> {
  const token = await ensureToken();
  const res = await fetch(`${BASE}/v1/account/export.zip`, {
    headers: {
      Authorization: `Bearer ${token}`,
      ...(APP_TOKEN ? { "X-App-Token": APP_TOKEN } : {}),
    },
  });
  if (!res.ok) throw new Error(`Export failed (${res.status})`);
  const url = URL.createObjectURL(await res.blob());
  const a = document.createElement("a");
  a.href = url;
  a.download = "aira-export.zip";
  a.click();
  URL.revokeObjectURL(url);
}

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
