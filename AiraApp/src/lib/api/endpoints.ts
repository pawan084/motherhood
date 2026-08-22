import type { ConsentFeature } from '@/store/slices/consentSlice';
import type { EmergencyProfile } from '@/store/slices/emergencySlice';
import type { Voice } from '@/store/slices/prefsSlice';
import type { TodaySummary } from '@/store/slices/todaySlice';
import type { VideoCategory, VideoTopic } from '@/store/slices/videosSlice';

import { clearToken, ensureToken, request, setToken, upload } from './client';
import type {
  WireCare,
  WireChatHistoryItem,
  WireConsentFeature,
  WireEmergencyProfile,
  WirePrefs,
  WireToday,
  WireTurn,
  WireUser,
  WireVideo,
  WireVideos,
} from './types';

/**
 * Typed endpoints, and the one place snake_case becomes camelCase.
 *
 * Each function returns the shape the store already declares, so a thunk is a
 * call and an assignment rather than a mapping exercise repeated per screen.
 */

// ── identity ─────────────────────────────────────────────────────────────────

export type AppUser = {
  id: string;
  kind: 'device' | 'account';
  email: string | null;
  name: string;
  journey: string;
  language: string;
  onboarded: boolean;
};

const toUser = (u: WireUser): AppUser => ({ ...u });

/** First run. Idempotent via the client's in-flight guard. */
export async function registerDevice(): Promise<string> {
  return ensureToken();
}

export async function me(): Promise<AppUser> {
  const r = await request<{ user: WireUser }>('/account/me');
  return toUser(r.user);
}

/**
 * Create an account, carrying this device's anonymous care data onto it.
 *
 * `device_token` is what makes the carry-over happen: the server promotes the
 * anonymous row in place — same user id — so onboarding answers and care items
 * survive. Sign-UP keeps your data; sign-IN switches to the account's own.
 */
export async function signUp(email: string, password: string): Promise<AppUser> {
  const deviceToken = await ensureToken();
  const r = await request<{ token: string; user: WireUser }>('/account/signup', {
    method: 'POST',
    body: { email, password, device_token: deviceToken },
    anonymous: true,
  });
  await setToken(r.token);
  return toUser(r.user);
}

/**
 * Sign in to an existing account.
 *
 * Deliberately sends NO device token: the server refuses to merge, because
 * folding one person's device notes into an existing account is not
 * recoverable. Warn before calling this when local data exists.
 */
export async function signIn(email: string, password: string): Promise<AppUser> {
  const r = await request<{ token: string; user: WireUser }>('/account/login', {
    method: 'POST',
    body: { email, password },
    anonymous: true,
  });
  await setToken(r.token);
  return toUser(r.user);
}

export async function signInWithGoogle(idToken: string): Promise<AppUser> {
  const deviceToken = await ensureToken();
  const r = await request<{ token: string; user: WireUser }>('/account/google', {
    method: 'POST',
    body: { id_token: idToken, device_token: deviceToken },
    anonymous: true,
  });
  await setToken(r.token);
  return toUser(r.user);
}

/** Signs out everywhere — the server bumps token_version, killing every token. */
export async function signOut(): Promise<void> {
  try {
    await request('/account/logout', { method: 'POST' });
  } finally {
    // Even if the call fails, this device must stop holding a credential the
    // person asked it to forget.
    await clearToken();
  }
}

export async function updateProfile(patch: {
  name?: string;
  journey?: string;
  language?: string;
}): Promise<AppUser> {
  const r = await request<{ user: WireUser }>('/account/profile', {
    method: 'PATCH',
    body: patch,
  });
  return toUser(r.user);
}

// ── passwordless sign-in ─────────────────────────────────────────────────────

export type CodeRequestResult = {
  expiresIn: number;
  resendIn: number;
  /** "smtp" once mail is configured; "development" when it is not. */
  delivery: 'smtp' | 'development';
  /**
   * Returned ONLY in development, when no mail provider is configured, so the
   * flow can be completed locally without a mailbox. Production 503s instead of
   * claiming an email was sent that was not.
   */
  devCode: string | null;
};

export async function requestLoginCode(
  email: string,
  purpose: 'signin' | 'signup',
): Promise<CodeRequestResult> {
  const r = await request<{
    expires_in: number;
    resend_in: number;
    delivery: string;
    dev_code?: string;
  }>('/account/code/request', {
    method: 'POST',
    body: { email, purpose },
    anonymous: true,
  });
  return {
    expiresIn: r.expires_in,
    resendIn: r.resend_in,
    delivery: r.delivery === 'smtp' ? 'smtp' : 'development',
    devCode: r.dev_code ?? null,
  };
}

export type CodeVerifyResult = {
  user: AppUser;
  isNewUser: boolean;
  /** Where to go next — decided by the SERVER, not guessed from the entry mode. */
  needsOnboarding: boolean;
  /** True when this device has anonymous history the account does not. */
  mergeRequired: boolean;
};

export async function verifyLoginCode(
  email: string,
  code: string,
): Promise<CodeVerifyResult> {
  // Sent so the server can carry this device's anonymous history onto the
  // account, and tell us whether a merge review is needed.
  const deviceToken = await ensureToken();
  const r = await request<{
    token: string;
    user: WireUser;
    is_new_user: boolean;
    needs_onboarding: boolean;
    merge_required: boolean;
  }>('/account/code/verify', {
    method: 'POST',
    body: { email, code, device_token: deviceToken },
    anonymous: true,
  });
  await setToken(r.token);
  return {
    user: toUser(r.user),
    isNewUser: r.is_new_user,
    needsOnboarding: r.needs_onboarding,
    mergeRequired: r.merge_required,
  };
}

// ── today / journey ──────────────────────────────────────────────────────────

const toSummary = (t: WireToday): TodaySummary => ({
  name: t.name,
  journey: t.journey as TodaySummary['journey'],
  weeks: t.weeks,
  weeksReported: t.weeks_reported,
  dayOfWeek: null,
  contextLine: t.context_line,
  weeksToGo: t.weeks === null ? null : Math.max(0, 40 - t.weeks),
  progress: t.weeks === null ? 0 : Math.min(1, t.weeks / 40),
  nextMilestone: t.next_action?.title ?? '',
  upToDate: true,
});

export async function getToday(): Promise<TodaySummary> {
  return toSummary(await request<WireToday>('/v1/today'));
}

/** The whole onboarding answer set, in one call. Sets `onboarded: true`. */
export async function submitOnboarding(payload: {
  journey: string;
  name?: string;
  language?: string;
  priorities?: string[];
  weeks?: number | null;
}): Promise<TodaySummary> {
  const body: Record<string, unknown> = {
    journey: payload.journey,
    priorities: payload.priorities ?? [],
  };
  if (payload.name) body.name = payload.name;
  if (payload.language) body.language = payload.language;
  // `null` is a real answer ("not sure yet") and must not be sent as a week.
  if (typeof payload.weeks === 'number') body.weeks = payload.weeks;

  return toSummary(await request<WireToday>('/v1/onboarding', { method: 'POST', body }));
}

/**
 * Correct the week, priorities, or due date.
 *
 * Prefer `dueDate`: the server derives the week from it, so it cannot drift.
 * A reported week is carried forward, which is how re-saving an editor
 * prefilled from today's count silently advances a pregnancy.
 */
export async function updateCareContext(patch: {
  weeks?: number | null;
  priorities?: string[];
  dueDate?: string;
}): Promise<TodaySummary> {
  const body: Record<string, unknown> = {};
  if (patch.weeks !== undefined) body.weeks = patch.weeks;
  if (patch.priorities) body.priorities = patch.priorities;
  if (patch.dueDate) body.due_date = patch.dueDate;
  return toSummary(
    await request<WireToday>('/v1/care/context', { method: 'PATCH', body }),
  );
}

// ── care ─────────────────────────────────────────────────────────────────────

export type CareSummary = {
  appointments: WireCare['appointments'];
  medicinesDue: WireCare['medicines_due'];
  reminders: WireCare['reminders'];
  documentsCount: number;
  planTotal: number;
  planOnTrack: number;
};

export async function getCare(): Promise<CareSummary> {
  const c = await request<WireCare>('/v1/care');
  return {
    appointments: c.appointments,
    medicinesDue: c.medicines_due,
    reminders: c.reminders,
    documentsCount: c.documents_count,
    planTotal: c.care_plan.total,
    planOnTrack: c.care_plan.on_track,
  };
}

export const addReminder = (b: { title: string; time?: string; repeat?: string }) =>
  request('/v1/care/reminders', { method: 'POST', body: b });

export const addMedicine = (b: {
  name: string;
  dose?: string;
  schedule?: string;
  time?: string;
}) => request('/v1/care/medicines', { method: 'POST', body: b });

export const addAppointment = (b: { doctor: string; place?: string; when?: string }) =>
  request('/v1/care/appointments', { method: 'POST', body: b });

/** A mood check-in. `feeling` is where the quick-log popup's mood lands. */
export const addCheckin = (b: {
  feeling?: string;
  sleep_hours?: number;
  note?: string;
}) => request('/v1/care/checkin', { method: 'POST', body: b });

export const addSymptom = (b: { what: string; severity?: string; started?: string }) =>
  request('/v1/care/symptom', { method: 'POST', body: b });

/** Toggleable, unlike a dose: a reminder ticked by mistake must be reversible. */
export const setReminderDone = (id: string, done: boolean) =>
  request(`/v1/care/reminders/${id}/done`, { method: 'POST', body: { done } });

export const markMedicineTaken = (id: string) =>
  request(`/v1/care/medicines/${id}/taken`, { method: 'POST' });

export const updateCareItem = (id: string, fields: Record<string, unknown>) =>
  request(`/v1/care/items/${id}`, { method: 'PATCH', body: fields });

export const deleteCareItem = (id: string) =>
  request(`/v1/care/items/${id}`, { method: 'DELETE' });

export const getTimeline = () =>
  request<{ items: WireCare['reminders'] }>('/v1/care/timeline');

export const uploadDocument = (
  file: { uri: string; name: string; type: string },
  kind: string,
) => upload('/v1/care/documents', file, { kind });

// ── chat ─────────────────────────────────────────────────────────────────────

export type TurnResult = {
  safety: { level: 'green' | 'amber' | 'red'; categories: string[]; degraded: boolean };
  urgent: boolean;
  reply: string | null;
  trustLabel: 'wellness' | 'watchful' | null;
  actionCard: { tool: string; title: string; detail: string } | null;
  disclaimerNeeded: boolean;
  urgentHelp: {
    headline: string;
    message: string;
    careTeamName: string | null;
    careTeamPhone: string | null;
    emergencyContactName: string | null;
    emergencyContactPhone: string | null;
  } | null;
};

/**
 * One safety-gated turn.
 *
 * Roles MUST be `user` / `assistant`. Anything else is silently dropped by the
 * server's history sanitiser — which is how a client can end up sending half a
 * conversation and never see an error.
 */
export async function chatTurn(
  message: string,
  history: { role: 'user' | 'assistant'; content: string }[],
): Promise<TurnResult> {
  const t = await request<WireTurn>('/v1/chat/turn', {
    method: 'POST',
    body: { message, history },
  });
  return {
    safety: t.safety,
    urgent: t.urgent,
    reply: t.reply,
    trustLabel: t.trust_label,
    actionCard: t.action_card,
    disclaimerNeeded: Boolean(t.disclaimer_needed),
    urgentHelp: t.urgent_help
      ? {
          headline: t.urgent_help.headline,
          message: t.urgent_help.message,
          careTeamName: t.urgent_help.care_team.name,
          careTeamPhone: t.urgent_help.care_team.phone,
          emergencyContactName: t.urgent_help.emergency_contact.name,
          emergencyContactPhone: t.urgent_help.emergency_contact.phone,
        }
      : null,
  };
}

export async function chatHistory(limit = 50) {
  const r = await request<{ items: WireChatHistoryItem[] }>(
    `/v1/chat/history?limit=${limit}`,
  );
  return r.items.map((i) => ({
    ts: i.ts,
    role: i.role,
    text: i.text,
    safetyLevel: i.safety_level,
  }));
}

// ── videos ───────────────────────────────────────────────────────────────────

function durationLabel(v: WireVideo): string {
  const lo = Math.max(1, Math.round(v.duration.min_seconds / 60));
  const hi = Math.max(lo, Math.round(v.duration.max_seconds / 60));
  return lo === hi ? `${lo} min` : `${lo}–${hi} min`;
}

const toVideo = (v: WireVideo): VideoTopic => ({
  id: v.id,
  slug: v.slug,
  title: v.title,
  category: v.category,
  categoryLabel: v.category_label,
  journeys: v.journeys,
  timing: {
    type: v.timing.type as VideoTopic['timing']['type'],
    startWeek: v.timing.start_week,
    endWeek: v.timing.end_week,
  },
  description: v.description,
  durationLabel: durationLabel(v),
  languages: v.languages,
  playable: v.playable,
  mediaUrl: v.media_url,
  mediaIsPlaceholder: v.media_is_placeholder,
  saved: Boolean(v.saved),
});

export async function getVideos(): Promise<{
  items: VideoTopic[];
  weekVideo: VideoTopic | null;
  categories: VideoCategory[];
  savedIds: string[];
}> {
  const v = await request<WireVideos>('/v1/videos');
  return {
    items: v.items.map(toVideo),
    weekVideo: v.week_video ? toVideo(v.week_video) : null,
    categories: v.categories,
    savedIds: v.saved_ids,
  };
}

export const saveVideo = (id: string) =>
  request(`/v1/videos/${id}/save`, { method: 'POST' });

export const unsaveVideo = (id: string) =>
  request(`/v1/videos/${id}/save`, { method: 'DELETE' });

// ── consent ──────────────────────────────────────────────────────────────────

const toConsent = (f: WireConsentFeature): ConsentFeature => ({ ...f });

export async function getConsent(): Promise<ConsentFeature[]> {
  const r = await request<{ features: WireConsentFeature[] }>('/v1/consent');
  return r.features.map(toConsent);
}

export async function setConsent(
  feature: string,
  granted: boolean,
): Promise<ConsentFeature[]> {
  const r = await request<{ features: WireConsentFeature[] }>('/v1/consent', {
    method: 'POST',
    body: { feature, granted },
  });
  return r.features.map(toConsent);
}

export async function consentHistory() {
  const r = await request<{
    history: { feature: string; granted: boolean; ts: number; note: string }[];
  }>('/v1/consent/history');
  return r.history;
}

// ── prefs ────────────────────────────────────────────────────────────────────

export async function getPrefs(): Promise<{ voice: Voice; spokenReplies: boolean }> {
  const p = await request<WirePrefs>('/v1/prefs');
  return { voice: p.voice as Voice, spokenReplies: p.spoken_replies };
}

export async function setPrefs(patch: {
  voice?: Voice;
  spokenReplies?: boolean;
}): Promise<{ voice: Voice; spokenReplies: boolean }> {
  const body: Record<string, unknown> = {};
  if (patch.voice) body.voice = patch.voice;
  if (patch.spokenReplies !== undefined) body.spoken_replies = patch.spokenReplies;
  const p = await request<WirePrefs>('/v1/prefs', { method: 'PUT', body });
  return { voice: p.voice as Voice, spokenReplies: p.spoken_replies };
}

// ── emergency profile ────────────────────────────────────────────────────────

const toEmergency = (e: WireEmergencyProfile): EmergencyProfile => ({
  bloodGroup: e.blood_group ?? '',
  allergies: e.allergies ?? '',
  hospital: e.hospital ?? '',
  notes: e.notes ?? '',
  careTeamName: e.care_team_name ?? '',
  careTeamPhone: e.care_team_phone ?? '',
  emergencyContactName: e.emergency_contact_name ?? '',
  emergencyContactPhone: e.emergency_contact_phone ?? '',
});

export async function getEmergencyProfile(): Promise<EmergencyProfile> {
  return toEmergency(await request<WireEmergencyProfile>('/v1/emergency-profile'));
}

/**
 * REPLACES the stored profile with what it is sent.
 *
 * So never call this from a form that failed to load — it would write empty
 * fields over a real care-team number, on the one screen that matters in an
 * emergency.
 */
export async function putEmergencyProfile(
  p: EmergencyProfile,
): Promise<EmergencyProfile> {
  const body: WireEmergencyProfile = {
    blood_group: p.bloodGroup,
    allergies: p.allergies,
    hospital: p.hospital,
    notes: p.notes,
    care_team_name: p.careTeamName,
    care_team_phone: p.careTeamPhone,
    emergency_contact_name: p.emergencyContactName,
    emergency_contact_phone: p.emergencyContactPhone,
  };
  return toEmergency(
    await request<WireEmergencyProfile>('/v1/emergency-profile', {
      method: 'PUT',
      body,
    }),
  );
}

// ── privacy ──────────────────────────────────────────────────────────────────

export const exportAccount = () =>
  request<{ user_id: string; exported_at: number; data: unknown }>('/v1/account/export');

/** Irreversible. The server requires this exact string. */
export const DELETE_CONFIRMATION = 'DELETE MY DATA';

export const deleteAccount = () =>
  request<{ ok: boolean }>('/v1/account/delete', {
    method: 'POST',
    body: { confirm: DELETE_CONFIRMATION },
  });
