import { createSlice, type PayloadAction } from '@reduxjs/toolkit';

type LoadStatus = 'idle' | 'loading' | 'succeeded' | 'failed';
type JourneyStage = 'pregnant' | 'postpartum' | 'trying' | 'loss';

export type TodaySummary = {
  name: string;
  journey: JourneyStage;
  weeks: number | null;
  weeksReported: number | null;
  dayOfWeek: number | null;
  contextLine: string;
  weeksToGo: number | null;
  progress: number;
  nextMilestone: string;
  upToDate: boolean;
};

export type WaterDay = {
  day: number;
  fill: number;
  today?: boolean;
};

export type TodayWater = {
  litres: number;
  goalLitres: number;
  averageLitres: number;
  week: WaterDay[];
};

export type TodayCare = {
  complete: number;
  total: number;
};

export type TodayVideo = {
  title: string;
  blurb: string;
  weekLabel: string;
  duration: string;
  reviewed: string;
};

type TodayState = {
  status: LoadStatus;
  summary: TodaySummary | null;
  water: TodayWater | null;
  care: TodayCare | null;
  moodKey: string | null;
  moodTrend: number[];
  weekVideo: TodayVideo | null;
  daysSinceCheckIn: number;
  lastSyncedMinutesAgo: number | null;
  askNotificationPermission: boolean;
  shieldHintVisible: boolean;
  stagePickerOpen: boolean;
  error: string | null;
};

const initialState: TodayState = {
  status: 'idle',
  summary: null,
  water: null,
  care: null,
  moodKey: null,
  moodTrend: [],
  weekVideo: null,
  daysSinceCheckIn: 0,
  lastSyncedMinutesAgo: null,
  askNotificationPermission: false,
  shieldHintVisible: true,
  stagePickerOpen: false,
  error: null,
};

const todaySlice = createSlice({
  name: 'today',
  initialState,
  reducers: {
    todayLoadStarted(state) {
      state.status = 'loading';
      state.error = null;
    },
    todayLoaded(
      state,
      action: PayloadAction<{
        summary: TodaySummary;
        water: TodayWater;
        care: TodayCare;
        moodKey: string | null;
        moodTrend: number[];
        weekVideo: TodayVideo;
        daysSinceCheckIn: number;
        lastSyncedMinutesAgo: number;
        askNotificationPermission: boolean;
      }>,
    ) {
      state.status = 'succeeded';
      state.summary = action.payload.summary;
      state.water = action.payload.water;
      state.care = action.payload.care;
      state.moodKey = action.payload.moodKey;
      state.moodTrend = action.payload.moodTrend;
      state.weekVideo = action.payload.weekVideo;
      state.daysSinceCheckIn = action.payload.daysSinceCheckIn;
      state.lastSyncedMinutesAgo = action.payload.lastSyncedMinutesAgo;
      state.askNotificationPermission = action.payload.askNotificationPermission;
      state.error = null;
    },
    todayLoadFailed(state, action: PayloadAction<string>) {
      state.status = 'failed';
      state.error = action.payload;
    },
    setTodayMood(state, action: PayloadAction<string | null>) {
      state.moodKey = action.payload;
    },
    setTodayCareProgress(state, action: PayloadAction<TodayCare>) {
      state.care = action.payload;
    },
    setTodayWater(state, action: PayloadAction<TodayWater>) {
      state.water = action.payload;
    },
    updateTodayStage(
      state,
      action: PayloadAction<{ journey: JourneyStage; weeksReported: number | null }>,
    ) {
      if (!state.summary) return;

      state.summary.journey = action.payload.journey;
      state.summary.weeksReported = action.payload.weeksReported;
    },
    setAskNotificationPermission(state, action: PayloadAction<boolean>) {
      state.askNotificationPermission = action.payload;
    },
    setShieldHintVisible(state, action: PayloadAction<boolean>) {
      state.shieldHintVisible = action.payload;
    },
    setStagePickerOpen(state, action: PayloadAction<boolean>) {
      state.stagePickerOpen = action.payload;
    },
    resetTodayState() {
      return initialState;
    },
  },
});

export const {
  resetTodayState,
  setAskNotificationPermission,
  setShieldHintVisible,
  setStagePickerOpen,
  setTodayCareProgress,
  setTodayMood,
  setTodayWater,
  todayLoaded,
  todayLoadFailed,
  todayLoadStarted,
  updateTodayStage,
} = todaySlice.actions;

export default todaySlice.reducer;
