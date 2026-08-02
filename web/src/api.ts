/** API client. Every call to the backend goes through this module.
 *
 * Identity: on first load the app mints a guest device token
 * (`POST /device/register`) and persists it in localStorage — the web
 * equivalent of sayli's Keychain flow. All learner routes send it as
 * `X-Device-Token`. (Account sessions arrive with the sign-in work; the
 * guest surface is complete without them.)
 */

const API = (import.meta.env.VITE_API_URL as string | undefined) ?? "http://127.0.0.1:8001";

const TOKEN_KEY = "aira.device_token";

export type CareContext = {
  stage: "trying_to_conceive" | "pregnant" | "postpartum";
  due_date: string | null;
  birth_date: string | null;
  display_name: string;
  language: string;
  week: number | null;
};

export type AppConfig = {
  journey_stages: string[];
  languages: string[];
  card_types: string[];
  safety_gate_enabled: boolean;
  ai_disclaimer: string;
};

export type Card = { type: string; title: string; subtitle: string };

export type UrgentHelpPayload = {
  headline: string;
  body: string;
  actions: { id: string; label: string }[];
};

export type StreamHandlers = {
  onGate: (decision: string, label: string, urgentHelp?: UrgentHelpPayload, message?: string) => void;
  onDelta: (text: string) => void;
  onCards: (cards: Card[]) => void;
  onError: (message: string) => void;
  onDone: () => void;
};

let deviceToken: string | null = localStorage.getItem(TOKEN_KEY);

export async function ensureDeviceToken(): Promise<string> {
  if (deviceToken) return deviceToken;
  const r = await fetch(`${API}/device/register`, { method: "POST" });
  if (!r.ok) throw new Error(`device register failed: ${r.status}`);
  const body = (await r.json()) as { device_token: string };
  deviceToken = body.device_token;
  localStorage.setItem(TOKEN_KEY, deviceToken);
  return deviceToken;
}

function headers(): Record<string, string> {
  const h: Record<string, string> = { "Content-Type": "application/json" };
  if (deviceToken) h["X-Device-Token"] = deviceToken;
  return h;
}

export async function getConfig(): Promise<AppConfig> {
  const r = await fetch(`${API}/config`);
  if (!r.ok) throw new Error(`config failed: ${r.status}`);
  return r.json();
}

export async function getCareContext(): Promise<CareContext | null> {
  const r = await fetch(`${API}/care-context`, { headers: headers() });
  if (!r.ok) throw new Error(`care-context failed: ${r.status}`);
  return (await r.json()).context;
}

export async function putCareContext(body: {
  stage: string;
  due_date?: string;
  birth_date?: string;
  display_name?: string;
  language?: string;
}): Promise<CareContext> {
  const r = await fetch(`${API}/care-context`, {
    method: "PUT",
    headers: headers(),
    body: JSON.stringify(body),
  });
  if (!r.ok) {
    const detail = (await r.json().catch(() => null))?.detail;
    throw new Error(typeof detail === "string" ? detail : `save failed: ${r.status}`);
  }
  return (await r.json()).context;
}

/** POST /respond_stream and dispatch its SSE events. EventSource can't POST,
 * so this parses the stream by hand. Event order is guaranteed by the
 * backend: gate first, so the caller knows the turn's safety status before
 * any token arrives. */
export async function respondStream(
  text: string,
  history: { role: "user" | "assistant"; content: string }[],
  handlers: StreamHandlers,
): Promise<void> {
  const r = await fetch(`${API}/respond_stream`, {
    method: "POST",
    headers: headers(),
    body: JSON.stringify({ text, history }),
  });
  if (!r.ok || !r.body) {
    handlers.onError("Aira can't respond right now. If anything feels urgent, contact your care team.");
    handlers.onDone();
    return;
  }
  const reader = r.body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";
  for (;;) {
    const { done, value } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });
    let index;
    while ((index = buffer.indexOf("\n\n")) >= 0) {
      const frame = buffer.slice(0, index);
      buffer = buffer.slice(index + 2);
      if (!frame.startsWith("data: ")) continue;
      const event = JSON.parse(frame.slice(6));
      switch (event.type) {
        case "gate":
          handlers.onGate(event.decision, event.label, event.urgent_help, event.message);
          break;
        case "delta":
          handlers.onDelta(event.text);
          break;
        case "cards":
          handlers.onCards(event.cards);
          break;
        case "error":
          handlers.onError(event.message);
          break;
        case "done":
          handlers.onDone();
          return;
      }
    }
  }
  handlers.onDone();
}
