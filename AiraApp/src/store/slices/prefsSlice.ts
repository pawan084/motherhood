import { createSlice, type PayloadAction } from '@reduxjs/toolkit';

type LoadStatus = 'idle' | 'loading' | 'succeeded' | 'failed';

/** Must match `prefs.VOICES` on the server, which 400s anything else. */
export const VOICES = ['Aira warm', 'Aira gentle', 'Text only'] as const;
export type Voice = (typeof VOICES)[number];

export type Appearance = 'system' | 'light' | 'dark';

/**
 * Accessibility settings.
 *
 * DEVICE-LOCAL, deliberately: there is no server field for any of them, and
 * they describe this phone rather than this person — somebody who needs larger
 * text here may not on a tablet. Persist them locally; do not invent an
 * endpoint.
 */
export type AccessibilityPrefs = {
  largerText: boolean;
  reduceMotion: boolean;
  highContrast: boolean;
  videoCaptions: boolean;
  extendedTapTime: boolean;
};

type PrefsState = {
  status: LoadStatus;
  /** Server-backed: GET/PUT /v1/prefs */
  voice: Voice;
  /**
   * Stored for real, but there is no speech synthesis in this build — so a
   * screen showing this must say so rather than implying the switch does
   * something now.
   */
  spokenReplies: boolean;
  /** Device-local from here down. */
  appearance: Appearance;
  accessibility: AccessibilityPrefs;
  remindersPerDay: 1 | 2 | 3;
  reminderTimes: string[];
  quietHours: { start: string; end: string };
  /** Set while reminders are paused — the reversible alternative to off. */
  remindersPausedUntil: number | null;
  saving: boolean;
  error: string | null;
};

const initialState: PrefsState = {
  status: 'idle',
  voice: 'Aira warm',
  spokenReplies: false,
  appearance: 'system',
  accessibility: {
    largerText: false,
    reduceMotion: false,
    highContrast: false,
    videoCaptions: true,
    extendedTapTime: false,
  },
  remindersPerDay: 2,
  reminderTimes: ['09:00', '20:00'],
  quietHours: { start: '22:00', end: '07:00' },
  remindersPausedUntil: null,
  saving: false,
  error: null,
};

const prefsSlice = createSlice({
  name: 'prefs',
  initialState,
  reducers: {
    prefsLoadStarted(state) {
      state.status = 'loading';
      state.error = null;
    },
    prefsLoaded(
      state,
      action: PayloadAction<{ voice: Voice; spokenReplies: boolean }>,
    ) {
      state.status = 'succeeded';
      state.voice = action.payload.voice;
      state.spokenReplies = action.payload.spokenReplies;
      state.error = null;
    },
    prefsLoadFailed(state, action: PayloadAction<string>) {
      state.status = 'failed';
      state.error = action.payload;
    },
    prefsSaveStarted(state) {
      state.saving = true;
    },
    prefsSaved(state, action: PayloadAction<{ voice: Voice; spokenReplies: boolean }>) {
      state.saving = false;
      state.voice = action.payload.voice;
      state.spokenReplies = action.payload.spokenReplies;
    },
    prefsSaveFailed(state, action: PayloadAction<string>) {
      state.saving = false;
      state.error = action.payload;
    },
    setAppearance(state, action: PayloadAction<Appearance>) {
      state.appearance = action.payload;
    },
    setAccessibilityPref(
      state,
      action: PayloadAction<{ key: keyof AccessibilityPrefs; value: boolean }>,
    ) {
      state.accessibility[action.payload.key] = action.payload.value;
    },
    setReminderCadence(
      state,
      action: PayloadAction<{ perDay: 1 | 2 | 3; times: string[] }>,
    ) {
      state.remindersPerDay = action.payload.perDay;
      state.reminderTimes = action.payload.times;
    },
    setQuietHours(state, action: PayloadAction<{ start: string; end: string }>) {
      state.quietHours = action.payload;
    },
    /** Reversible by design — `null` resumes. */
    pauseReminders(state, action: PayloadAction<number | null>) {
      state.remindersPausedUntil = action.payload;
    },
    resetPrefsState() {
      return initialState;
    },
  },
});

export const {
  pauseReminders,
  prefsLoaded,
  prefsLoadFailed,
  prefsLoadStarted,
  prefsSaved,
  prefsSaveFailed,
  prefsSaveStarted,
  resetPrefsState,
  setAccessibilityPref,
  setAppearance,
  setQuietHours,
  setReminderCadence,
} = prefsSlice.actions;

export default prefsSlice.reducer;
