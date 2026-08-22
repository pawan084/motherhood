import { createSlice, type PayloadAction } from '@reduxjs/toolkit';

type LoadStatus = 'idle' | 'loading' | 'succeeded' | 'failed';
type SubmitStatus = 'idle' | 'submitting' | 'succeeded' | 'failed';

export type MoodKey = 'great' | 'okay' | 'tired' | 'anxious' | 'low' | 'unwell';

export type MoodLog = {
  id: string;
  mood: MoodKey;
  note?: string | null;
  loggedAt: string;
};

export type MoodWeekDay = {
  date: string;
  dayLabel: string;
  mood: MoodKey | null;
  selected?: boolean;
};

export type MoodMonthSummary = {
  mood: MoodKey;
  label: string;
  count: number;
  ratio: number;
};

type MoodState = {
  status: LoadStatus;
  submitStatus: SubmitStatus;
  todayMood: MoodKey | null;
  draftMood: MoodKey | null;
  draftNote: string;
  week: MoodWeekDay[];
  month: MoodMonthSummary[];
  logs: MoodLog[];
  checkInsLast30Days: number;
  summary: string;
  error: string | null;
};

const initialState: MoodState = {
  status: 'idle',
  submitStatus: 'idle',
  todayMood: null,
  draftMood: null,
  draftNote: '',
  week: [],
  month: [],
  logs: [],
  checkInsLast30Days: 0,
  summary: '',
  error: null,
};

const moodSlice = createSlice({
  name: 'mood',
  initialState,
  reducers: {
    moodsLoadStarted(state) {
      state.status = 'loading';
      state.error = null;
    },
    moodsLoaded(
      state,
      action: PayloadAction<{
        todayMood: MoodKey | null;
        week: MoodWeekDay[];
        month: MoodMonthSummary[];
        logs: MoodLog[];
        checkInsLast30Days: number;
        summary: string;
      }>,
    ) {
      state.status = 'succeeded';
      state.todayMood = action.payload.todayMood;
      state.draftMood = action.payload.todayMood;
      state.week = action.payload.week;
      state.month = action.payload.month;
      state.logs = action.payload.logs;
      state.checkInsLast30Days = action.payload.checkInsLast30Days;
      state.summary = action.payload.summary;
      state.error = null;
    },
    moodsLoadFailed(state, action: PayloadAction<string>) {
      state.status = 'failed';
      state.error = action.payload;
    },
    setDraftMood(state, action: PayloadAction<MoodKey | null>) {
      state.draftMood = action.payload;
      state.error = null;
    },
    setDraftMoodNote(state, action: PayloadAction<string>) {
      state.draftNote = action.payload;
      state.error = null;
    },
    moodSubmitStarted(state) {
      state.submitStatus = 'submitting';
      state.error = null;
    },
    moodSubmitSucceeded(state, action: PayloadAction<MoodLog>) {
      state.submitStatus = 'succeeded';
      state.todayMood = action.payload.mood;
      state.draftMood = action.payload.mood;
      state.draftNote = '';
      state.logs = [action.payload, ...state.logs.filter((log) => log.id !== action.payload.id)];
      state.error = null;
    },
    moodSubmitFailed(state, action: PayloadAction<string>) {
      state.submitStatus = 'failed';
      state.error = action.payload;
    },
    deleteMoodLog(state, action: PayloadAction<string>) {
      state.logs = state.logs.filter((log) => log.id !== action.payload);
    },
    resetMoodState() {
      return initialState;
    },
  },
});

export const {
  deleteMoodLog,
  moodSubmitFailed,
  moodSubmitStarted,
  moodSubmitSucceeded,
  moodsLoaded,
  moodsLoadFailed,
  moodsLoadStarted,
  resetMoodState,
  setDraftMood,
  setDraftMoodNote,
} = moodSlice.actions;

export default moodSlice.reducer;
