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

// ── Care ────────────────────────────────────────────────────────────────────

export type Medicine = {
  id: string;
  name: string;
  dose: string;
  time_of_day: string;
  notes: string;
  taken_today: boolean;
};

export type CareDocument = {
  id: string;
  kind: string;
  filename: string;
  size: number;
  created: number;
};

export type Appointment = {
  id: string;
  title: string;
  when_ts: number;
  location: string;
  notes: string;
};

export type PlanItem = { id: string; title: string; done: boolean };

async function json<T>(r: Response): Promise<T> {
  if (!r.ok) {
    const detail = (await r.json().catch(() => null))?.detail;
    throw new Error(typeof detail === "string" ? detail : `request failed: ${r.status}`);
  }
  return r.json();
}

export async function listMedicines(): Promise<Medicine[]> {
  return (await json<{ medicines: Medicine[] }>(await fetch(`${API}/medicines`, { headers: headers() }))).medicines;
}
export async function addMedicine(body: { name: string; dose?: string; time_of_day?: string }): Promise<void> {
  await json(await fetch(`${API}/medicines`, { method: "POST", headers: headers(), body: JSON.stringify(body) }));
}
export async function markMedicineTaken(id: string): Promise<void> {
  await json(await fetch(`${API}/medicines/${id}/taken`, { method: "POST", headers: headers() }));
}
export async function deleteMedicine(id: string): Promise<void> {
  await json(await fetch(`${API}/medicines/${id}`, { method: "DELETE", headers: headers() }));
}

export async function listDocuments(): Promise<CareDocument[]> {
  return (await json<{ documents: CareDocument[] }>(await fetch(`${API}/documents`, { headers: headers() }))).documents;
}
export async function uploadDocument(file: File, kind: string): Promise<void> {
  const form = new FormData();
  form.append("file", file);
  form.append("kind", kind);
  const h: Record<string, string> = {};
  const token = localStorage.getItem("aira.device_token");
  if (token) h["X-Device-Token"] = token;
  await json(await fetch(`${API}/documents`, { method: "POST", headers: h, body: form }));
}
export function documentUrl(id: string): string {
  return `${API}/documents/${id}`;
}
export async function fetchDocumentBlob(id: string): Promise<Blob> {
  const r = await fetch(`${API}/documents/${id}`, { headers: headers() });
  if (!r.ok) throw new Error(`download failed: ${r.status}`);
  return r.blob();
}
export async function deleteDocument(id: string): Promise<void> {
  await json(await fetch(`${API}/documents/${id}`, { method: "DELETE", headers: headers() }));
}

export async function listAppointments(): Promise<Appointment[]> {
  return (await json<{ appointments: Appointment[] }>(await fetch(`${API}/appointments`, { headers: headers() })))
    .appointments;
}
export async function addAppointment(body: {
  title: string;
  when_ts: number;
  location?: string;
}): Promise<void> {
  await json(await fetch(`${API}/appointments`, { method: "POST", headers: headers(), body: JSON.stringify(body) }));
}
export async function deleteAppointment(id: string): Promise<void> {
  await json(await fetch(`${API}/appointments/${id}`, { method: "DELETE", headers: headers() }));
}

export async function listPlan(): Promise<PlanItem[]> {
  return (await json<{ items: PlanItem[] }>(await fetch(`${API}/care-plan`, { headers: headers() }))).items;
}
export async function addPlanItem(title: string): Promise<void> {
  await json(await fetch(`${API}/care-plan`, { method: "POST", headers: headers(), body: JSON.stringify({ title }) }));
}
export async function togglePlanItem(id: string): Promise<void> {
  await json(await fetch(`${API}/care-plan/${id}/toggle`, { method: "POST", headers: headers() }));
}
export async function deletePlanItem(id: string): Promise<void> {
  await json(await fetch(`${API}/care-plan/${id}`, { method: "DELETE", headers: headers() }));
}

// ── Privacy ─────────────────────────────────────────────────────────────────

export async function getConsents(): Promise<Record<string, boolean>> {
  return (await json<{ consents: Record<string, boolean> }>(
    await fetch(`${API}/consents`, { headers: headers() }),
  )).consents;
}
export async function putConsents(consents: Record<string, boolean>): Promise<Record<string, boolean>> {
  return (await json<{ consents: Record<string, boolean> }>(
    await fetch(`${API}/consents`, { method: "PUT", headers: headers(), body: JSON.stringify(consents) }),
  )).consents;
}
export async function exportData(): Promise<Blob> {
  const r = await fetch(`${API}/export`, { headers: headers() });
  if (!r.ok) throw new Error(`export failed: ${r.status}`);
  return new Blob([await r.text()], { type: "application/json" });
}
export async function deleteLearnerData(): Promise<void> {
  await json(await fetch(`${API}/learner-data`, { method: "DELETE", headers: headers() }));
}

export type JourneyContent = {
  id: string;
  title: string;
  yourself: string;
  baby: string;
  prepare: string;
};

export type JourneyResponse = {
  stage: string;
  current_week: number | null;
  shown_week: number | null;
  content: JourneyContent | null;
  disclaimer: string;
};

export async function getJourney(week?: number): Promise<JourneyResponse> {
  const url = week ? `${API}/journey?week=${week}` : `${API}/journey`;
  const r = await fetch(url, { headers: headers() });
  if (!r.ok) throw new Error(`journey failed: ${r.status}`);
  return r.json();
}

export type MemoryItem = { id: string; kind: string; content: string; updated: number };

export async function getMemory(): Promise<MemoryItem[]> {
  const r = await fetch(`${API}/memory`, { headers: headers() });
  if (!r.ok) throw new Error(`memory failed: ${r.status}`);
  return (await r.json()).items;
}

export async function forgetMemoryItem(id: string): Promise<void> {
  const r = await fetch(`${API}/memory/${id}`, { method: "DELETE", headers: headers() });
  if (!r.ok) throw new Error(`forget failed: ${r.status}`);
}

export async function forgetAllMemory(): Promise<void> {
  const r = await fetch(`${API}/memory`, { method: "DELETE", headers: headers() });
  if (!r.ok) throw new Error(`forget failed: ${r.status}`);
}

export type VoiceTurn = {
  decision: string;
  transcript: string | null;
  safety?: { decision: string; label: string };
  urgent_help?: UrgentHelpPayload;
  message?: string;
  reply: string | null;
  cards: Card[];
};

/** One voice turn: audio blob -> transcript + the same gated payload as text. */
export async function respondVoice(
  blob: Blob,
  history: { role: "user" | "assistant"; content: string }[],
): Promise<VoiceTurn> {
  const form = new FormData();
  form.append("file", blob, "turn.webm");
  form.append("history", JSON.stringify(history));
  const h: Record<string, string> = {};
  if (deviceToken) h["X-Device-Token"] = deviceToken;
  const r = await fetch(`${API}/respond_voice`, { method: "POST", headers: h, body: form });
  if (!r.ok) throw new Error(`voice turn failed: ${r.status}`);
  return r.json();
}

/** Aira's reply as MP3. Returns null on failure — voice is an enhancement,
 * the text reply already rendered. */
export async function speak(text: string): Promise<Blob | null> {
  try {
    const r = await fetch(`${API}/speak`, {
      method: "POST",
      headers: headers(),
      body: JSON.stringify({ text }),
    });
    if (!r.ok) return null;
    return await r.blob();
  } catch {
    return null;
  }
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
