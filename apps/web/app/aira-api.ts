// Typed client for the Aira backend — the single place the web app talks to the
// safety-gated API. Identity is an anonymous device token minted on first use
// and cached in localStorage; every request carries it as a Bearer token.
//
// The important piece is `chatTurn`: the SERVER runs the safety gate, so a red
// message comes back as `{ urgent: true, urgent_help: {...}, reply: null }` and
// the UI routes to the urgent-help screen with a REAL care-team number — fixing
// the prototype's inert "Call care team" button and its missing server gate.

const BASE = import.meta.env.VITE_API_URL || "http://127.0.0.1:8000";
const APP_TOKEN = import.meta.env.VITE_APP_TOKEN || "";
const TOKEN_KEY = "aira_session_token";

function getToken(): string | null {
  return typeof localStorage !== "undefined" ? localStorage.getItem(TOKEN_KEY) : null;
}
function setToken(t: string) {
  if (typeof localStorage !== "undefined") localStorage.setItem(TOKEN_KEY, t);
}

async function ensureToken(): Promise<string> {
  const existing = getToken();
  if (existing) return existing;
  const res = await fetch(`${BASE}/device/register`, {
    method: "POST",
    headers: APP_TOKEN ? { "X-App-Token": APP_TOKEN } : {},
  });
  if (!res.ok) throw new Error(`device register failed (${res.status})`);
  const data = await res.json();
  setToken(data.token);
  return data.token;
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

// ── endpoints ─────────────────────────────────────────────────────────────
export const AiraAPI = {
  chatTurn: (message: string, history: { role: string; content: string }[] = []) =>
    req<TurnResponse>("/v1/chat/turn", {
      method: "POST",
      body: JSON.stringify({ message, history }),
    }),

  onboarding: (payload: {
    journey: Journey; name?: string; language?: string;
    priorities?: string[]; weeks?: number;
  }) => req("/v1/onboarding", { method: "POST", body: JSON.stringify(payload) }),

  today: () => req<TodayData>("/v1/today"),
  journey: () => req<JourneyData>("/v1/journey"),
  care: () => req("/v1/care"),
  memory: () => req<{ items: unknown[] }>("/v1/memory"),
  consent: () => req<{ features: unknown[] }>("/v1/consent"),
  setConsent: (feature: string, granted: boolean) =>
    req("/v1/consent", { method: "POST", body: JSON.stringify({ feature, granted }) }),

  emergencyProfile: () =>
    req<{
      care_team_name?: string; care_team_phone?: string;
      emergency_contact_name?: string; emergency_contact_phone?: string;
    }>("/v1/emergency-profile"),

  reportAnswer: (message: string, ref?: string) =>
    req("/v1/feedback/report", {
      method: "POST",
      body: JSON.stringify({ kind: "clinical", message, ref }),
    }),
};

// Build a `tel:` href for the urgent-help "Call care team" button. Falls back to
// null when no number is on file — the UI then shows local-emergency guidance
// instead of an inert button.
export function telHref(phone: string | null | undefined): string | null {
  if (!phone) return null;
  return `tel:${phone.replace(/[^\d+]/g, "")}`;
}
