import { createSlice, type PayloadAction } from '@reduxjs/toolkit';

type SubmitStatus = 'idle' | 'submitting' | 'succeeded' | 'failed';

/** Must match `accounts.VALID_JOURNEYS` on the server. */
export type Journey = 'pregnant' | 'postpartum' | 'trying' | 'loss';

/**
 * Must match `safety.SUPPORTED_LANGUAGES` EXACTLY.
 *
 * The server 400s anything else, and not out of fussiness: the deterministic
 * safety keyword floor can only read these three. Offering a fourth invites
 * somebody to write in a language nothing is screening — which matters most
 * when the LLM classifier is down and the floor is all there is.
 */
export const LANGUAGES = ['English', 'Hindi', 'Hinglish'] as const;
export type Language = (typeof LANGUAGES)[number];

export const TOTAL_STEPS = 4;

/**
 * The answers, collected across four screens and submitted ONCE.
 *
 * `POST /v1/onboarding` takes the whole set in a single call and sets
 * `onboarded: true` as it goes, so posting per step would mark somebody
 * onboarded halfway through and leave a profile missing fields every later
 * screen reads.
 */
type OnboardingState = {
  step: number;
  journey: Journey | null;
  /** null is a real answer — "not sure yet" — and distinct from unanswered. */
  weeks: number | null;
  weeksAnswered: boolean;
  priorities: string[];
  language: Language | null;
  name: string;
  remindersPerDay: 1 | 2 | 3;
  reminderTimes: string[];
  status: SubmitStatus;
  error: string | null;
};

const initialState: OnboardingState = {
  step: 1,
  journey: null,
  weeks: null,
  weeksAnswered: false,
  priorities: [],
  language: null,
  name: '',
  remindersPerDay: 2,
  reminderTimes: ['09:00', '20:00'],
  status: 'idle',
  error: null,
};

const onboardingSlice = createSlice({
  name: 'onboarding',
  initialState,
  reducers: {
    setOnboardingStep(state, action: PayloadAction<number>) {
      state.step = Math.min(Math.max(1, action.payload), TOTAL_STEPS);
    },
    nextOnboardingStep(state) {
      state.step = Math.min(state.step + 1, TOTAL_STEPS);
    },
    previousOnboardingStep(state) {
      state.step = Math.max(state.step - 1, 1);
    },
    /**
     * Changing the stage clears the answer that was scoped to it: a week means
     * nothing once somebody switches from pregnant to postpartum, and carrying
     * it forward is how a stale 24 ends up on a screen it does not belong on.
     */
    setOnboardingJourney(state, action: PayloadAction<Journey>) {
      if (state.journey !== action.payload) {
        state.weeks = null;
        state.weeksAnswered = false;
        state.priorities = [];
      }
      state.journey = action.payload;
    },
    setOnboardingWeeks(state, action: PayloadAction<number | null>) {
      state.weeks = action.payload;
      state.weeksAnswered = true;
    },
    toggleOnboardingPriority(state, action: PayloadAction<string>) {
      const p = action.payload;
      state.priorities = state.priorities.includes(p)
        ? state.priorities.filter((x) => x !== p)
        : [...state.priorities, p];
    },
    setOnboardingLanguage(state, action: PayloadAction<Language>) {
      state.language = action.payload;
    },
    setOnboardingName(state, action: PayloadAction<string>) {
      state.name = action.payload;
    },
    setOnboardingReminders(
      state,
      action: PayloadAction<{ perDay: 1 | 2 | 3; times: string[] }>,
    ) {
      state.remindersPerDay = action.payload.perDay;
      state.reminderTimes = action.payload.times;
    },
    onboardingSubmitStarted(state) {
      state.status = 'submitting';
      state.error = null;
    },
    onboardingSubmitted(state) {
      state.status = 'succeeded';
    },
    onboardingSubmitFailed(state, action: PayloadAction<string>) {
      state.status = 'failed';
      state.error = action.payload;
    },
    resetOnboardingState() {
      return initialState;
    },
  },
});

export const {
  nextOnboardingStep,
  onboardingSubmitFailed,
  onboardingSubmitStarted,
  onboardingSubmitted,
  previousOnboardingStep,
  resetOnboardingState,
  setOnboardingJourney,
  setOnboardingLanguage,
  setOnboardingName,
  setOnboardingReminders,
  setOnboardingStep,
  setOnboardingWeeks,
  toggleOnboardingPriority,
} = onboardingSlice.actions;

export default onboardingSlice.reducer;
