/**
 * The wire shapes, exactly as the backend sends them — snake_case and all.
 *
 * These are deliberately separate from the app's own types. The server speaks
 * snake_case, the app speaks camelCase, and the mapping happens once in
 * `endpoints.ts` rather than every screen reaching into a raw payload and
 * guessing. It also means a field the server renames breaks in one file.
 */

export type WireUser = {
  id: string;
  kind: 'device' | 'account';
  email: string | null;
  name: string;
  journey: string;
  language: string;
  onboarded: boolean;
};

export type WireNextAction = {
  tool: string;
  title: string;
  detail: string;
  minutes?: number;
};

export type WireToday = {
  name: string;
  journey: string;
  context_line: string;
  /** What Aira has counted forward to — what every screen shows. */
  weeks: number | null;
  /**
   * What the person last TYPED, and what an editor must prefill with.
   * Prefilling from `weeks` nudges the pregnancy forward by however long it has
   * been since they told us.
   */
  weeks_reported: number | null;
  due_date: string | null;
  next_action: WireNextAction;
  priorities: string[];
};

export type WireCareItem = {
  id: string;
  kind: string;
  done: boolean;
  created: number;
  [key: string]: unknown;
};

export type WireCare = {
  appointments: WireCareItem[];
  medicines_due: WireCareItem[];
  documents_count: number;
  reminders: WireCareItem[];
  care_plan: { total: number; on_track: number };
};

export type WireSafety = {
  level: 'green' | 'amber' | 'red';
  categories: string[];
  /** True when only the keyword floor ran — the classifier was unreachable. */
  degraded: boolean;
};

export type WireUrgentHelp = {
  headline: string;
  message: string;
  care_team: { name: string | null; phone: string | null };
  emergency_contact: { name: string | null; phone: string | null };
  show_emergency_services: boolean;
};

export type WireTurn = {
  safety: WireSafety;
  urgent: boolean;
  reply: string | null;
  trust_label: 'wellness' | 'watchful' | null;
  action_card: { tool: string; title: string; detail: string } | null;
  disclaimer_needed?: boolean;
  urgent_help?: WireUrgentHelp;
};

export type WireChatHistoryItem = {
  ts: number;
  role: string;
  text: string;
  safety_level: string;
};

export type WireVideo = {
  id: string;
  slug: string;
  title: string;
  category: string;
  category_label: string;
  journeys: string[];
  timing: { type: string; start_week: number | null; end_week: number | null };
  description: string;
  duration: { min_seconds: number; max_seconds: number };
  languages: string[];
  playable: boolean;
  media_url: string | null;
  media_is_placeholder: boolean;
  saved?: boolean;
};

export type WireVideos = {
  items: WireVideo[];
  week_video: WireVideo | null;
  categories: { key: string; label: string }[];
  saved_ids: string[];
};

export type WireConsentFeature = {
  key: string;
  label: string;
  granted: boolean;
  locked: boolean;
  available: boolean;
};

export type WirePrefs = { voice: string; spoken_replies: boolean };

export type WireEmergencyProfile = {
  blood_group?: string;
  allergies?: string;
  hospital?: string;
  notes?: string;
  care_team_name?: string;
  care_team_phone?: string;
  emergency_contact_name?: string;
  emergency_contact_phone?: string;
};
