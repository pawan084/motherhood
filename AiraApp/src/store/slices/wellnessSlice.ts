import { createSlice, type PayloadAction } from '@reduxjs/toolkit';

type LoadStatus = 'idle' | 'loading' | 'succeeded' | 'failed';
type ActionStatus = 'idle' | 'loading' | 'succeeded' | 'failed';

export type WellnessDay = {
  label: string;
  score: number;
  active?: boolean;
};

export type WellnessMetric = {
  id: string;
  label: string;
  value: string;
  icon: string;
};

export type ShareRecipient = {
  id: string;
  name: string;
  channel: 'whatsapp' | 'sms' | 'email' | 'system';
  detail: string;
};

type ShareOptions = {
  journeyWeek: boolean;
  careConsistency: boolean;
  moodCheckins: boolean;
};

type WellnessReport = {
  weekLabel: string;
  journeyName: string;
  consistencyPercent: number;
  previousConsistencyPercent: number | null;
  summary: string;
  babyComparison: string;
  weeksToGo: number | null;
  waterGoal: string;
  moodCheckins: string;
  days: WellnessDay[];
  metrics: WellnessMetric[];
  insight: string;
  disclaimer: string;
};

type WellnessState = {
  status: LoadStatus;
  report: WellnessReport | null;
  shareOptions: ShareOptions;
  selectedRecipient: ShareRecipient | null;
  shareStatus: ActionStatus;
  saveImageStatus: ActionStatus;
  error: string | null;
};

const initialState: WellnessState = {
  status: 'idle',
  report: null,
  shareOptions: {
    journeyWeek: true,
    careConsistency: true,
    moodCheckins: false,
  },
  selectedRecipient: null,
  shareStatus: 'idle',
  saveImageStatus: 'idle',
  error: null,
};

const wellnessSlice = createSlice({
  name: 'wellness',
  initialState,
  reducers: {
    wellnessReportLoadStarted(state) {
      state.status = 'loading';
      state.error = null;
    },
    wellnessReportLoaded(state, action: PayloadAction<WellnessReport>) {
      state.status = 'succeeded';
      state.report = action.payload;
      state.error = null;
    },
    wellnessReportLoadFailed(state, action: PayloadAction<string>) {
      state.status = 'failed';
      state.error = action.payload;
    },
    setWellnessShareOption(
      state,
      action: PayloadAction<{ key: keyof ShareOptions; value: boolean }>,
    ) {
      state.shareOptions[action.payload.key] = action.payload.value;
    },
    setWellnessShareRecipient(state, action: PayloadAction<ShareRecipient | null>) {
      state.selectedRecipient = action.payload;
    },
    wellnessShareStarted(state) {
      state.shareStatus = 'loading';
      state.error = null;
    },
    wellnessShareSucceeded(state) {
      state.shareStatus = 'succeeded';
      state.error = null;
    },
    wellnessShareFailed(state, action: PayloadAction<string>) {
      state.shareStatus = 'failed';
      state.error = action.payload;
    },
    wellnessSaveImageStarted(state) {
      state.saveImageStatus = 'loading';
      state.error = null;
    },
    wellnessSaveImageSucceeded(state) {
      state.saveImageStatus = 'succeeded';
      state.error = null;
    },
    wellnessSaveImageFailed(state, action: PayloadAction<string>) {
      state.saveImageStatus = 'failed';
      state.error = action.payload;
    },
    resetWellnessState() {
      return initialState;
    },
  },
});

export const {
  resetWellnessState,
  setWellnessShareOption,
  setWellnessShareRecipient,
  wellnessReportLoaded,
  wellnessReportLoadFailed,
  wellnessReportLoadStarted,
  wellnessSaveImageFailed,
  wellnessSaveImageStarted,
  wellnessSaveImageSucceeded,
  wellnessShareFailed,
  wellnessShareStarted,
  wellnessShareSucceeded,
} = wellnessSlice.actions;

export default wellnessSlice.reducer;
