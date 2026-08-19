/**
 * Static stand-in data for the Today screen.
 *
 * Every field is named after the one the API returns, so wiring it later is a
 * swap rather than a rewrite:
 *
 *   GET /v1/today   → name, journey, weeks, weeks_reported, due_date,
 *                     context_line, next_action, priorities
 *   GET /v1/care    → reminders, medicines_due, care_plan { total, on_track }
 *   GET /v1/videos  → week_video
 *
 * `weeks` vs `weeksReported` is not a duplicate: `weeks` is what Aira has
 * counted forward to and what every screen shows; `weeksReported` is what the
 * person last typed and what an editor must prefill with. Prefilling an editor
 * from `weeks` silently pushes the pregnancy forward by however long it has
 * been since they told us.
 */
export const today = {
  name: 'pawan',
  journey: 'pregnant' as const,
  weeks: 24,
  weeksReported: 24,
  dayOfWeek: 5,
  /**
   * One line from the server (`context_line`), not assembled here. What it says
   * is week- and journey-specific — "Baby is about the size of a papaya",
   * "Movement becomes a rhythm · ~30.0 cm" — and composing it client-side would
   * mean two clients writing subtly different copy for the same week.
   */
  contextLine: 'Baby is about the size of a papaya · 16 weeks to go',
  weeksToGo: 16,
  /** 0–1, how far through the 40 weeks. */
  progress: 24 / 40,
  nextMilestone: 'Next: baby may hear your voice',
  upToDate: true,
};

export const water = {
  litres: 1.6,
  goalLitres: 2.5,
  averageLitres: 1.8,
  /** The last seven days. `day` is the date, `fill` is 0–1 of that day's goal. */
  week: [
    { day: 21, fill: 0.7 },
    { day: 22, fill: 0.5 },
    { day: 23, fill: 0.9 },
    { day: 24, fill: 0.64, today: true },
    { day: 25, fill: 0 },
    { day: 26, fill: 0 },
    { day: 27, fill: 0 },
  ],
};

/**
 * Today's care list.
 *
 * `total: 0` is not a missing value — it is a real state with its own screen. A
 * first session, or simply a day with nothing scheduled, needs an honest design
 * rather than a stack of cards showing zeroes.
 *
 * Set `total: 0` to preview it.
 */
export const care = { complete: 1, total: 3 };

/** Drives the "Last synchronized ..." line under the empty state. */
export const lastSyncedMinutesAgo = 2;

/**
 * Days since the last check-in.
 *
 * Three or more switches Today into its re-engagement state. The threshold is a
 * product decision, not a technical one: often enough to notice somebody has
 * stepped away, rarely enough that it does not fire on a normal weekend.
 *
 * Set to 3 to preview it.
 */
export const daysSinceCheckIn = 0;

/**
 * Whether the notification soft ask is still outstanding.
 *
 * True until the person answers it once, either way — this is a
 * pre-permission screen, so it must not reappear after a "Not now" and nag.
 * Persist the answer (AsyncStorage is already a dependency) rather than
 * recomputing it, and never show it in the same session as the re-engagement
 * card: somebody who has been away is already being asked for one thing.
 */
export const askNotificationPermission = true;

/**
 * The reminder times onboarding step 4 settles on.
 *
 * Scheduled on the device, not the server — there is no job runner behind the
 * API, and for water and vitamin nudges local is the right answer anyway: the
 * phone knows the time, works offline, and the times never have to leave it.
 */
export const reminderTimes = ['09:00', '20:00'];

export const MOODS = [
  { key: 'great', label: 'Great' },
  { key: 'okay', label: 'Okay' },
  { key: 'tired', label: 'Tired' },
  { key: 'anxious', label: 'Anxious' },
  { key: 'low', label: 'Low' },
  { key: 'unwell', label: 'Unwell' },
] as const;

/** Seven days of mood, 0–1, oldest first. Drives the sparkline. */
export const moodTrend = [0.35, 0.55, 0.85, 0.5, 0.62, 0.28, 0.6];

export const weekVideo = {
  title: 'Changes in your body this week',
  blurb: 'Calm, reviewed guidance for what may feel different now.',
  weekLabel: 'Week 24',
  duration: '5:45',
  /**
   * Every topic in the catalogue returns `playable: false` and a null
   * `media_url` today — the 100 topics are written, not filmed. The card says
   * "reviewed", never "watch now", because only the first one is true.
   */
  reviewed: 'Educational content · reviewed August 2026 · not medical advice',
};
