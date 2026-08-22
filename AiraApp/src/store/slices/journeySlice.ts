import { createSlice, type PayloadAction } from '@reduxjs/toolkit';

type LoadStatus = 'idle' | 'loading' | 'succeeded' | 'failed';
type JourneyStage = 'pregnant' | 'postpartum' | 'trying' | 'loss';

export type JourneyWeek = {
  week: number;
  label?: string;
  selected?: boolean;
  current?: boolean;
};

export type JourneyPathNode = {
  id: string;
  week: number;
  title: string;
  body: string;
  eyebrow?: string;
  state: 'current' | 'completed' | 'upNext' | 'locked' | 'future';
  unlockWeek?: number | null;
  route?: string | null;
};

export type JourneyEditorialCard = {
  id: string;
  title: string;
  body: string;
  eyebrow: string;
};

type JourneyState = {
  status: LoadStatus;
  stage: JourneyStage;
  currentWeek: number | null;
  selectedWeek: number | null;
  weeks: JourneyWeek[];
  heroTitle: string;
  heroBody: string;
  progress: number;
  weeksToGo: number | null;
  path: JourneyPathNode[];
  editorialCards: JourneyEditorialCard[];
  error: string | null;
};

const initialState: JourneyState = {
  status: 'idle',
  stage: 'pregnant',
  currentWeek: null,
  selectedWeek: null,
  weeks: [],
  heroTitle: '',
  heroBody: '',
  progress: 0,
  weeksToGo: null,
  path: [],
  editorialCards: [],
  error: null,
};

const journeySlice = createSlice({
  name: 'journey',
  initialState,
  reducers: {
    journeyLoadStarted(state) {
      state.status = 'loading';
      state.error = null;
    },
    journeyLoaded(
      state,
      action: PayloadAction<{
        stage: JourneyStage;
        currentWeek: number | null;
        selectedWeek: number | null;
        weeks: JourneyWeek[];
        heroTitle: string;
        heroBody: string;
        progress: number;
        weeksToGo: number | null;
        path: JourneyPathNode[];
        editorialCards: JourneyEditorialCard[];
      }>,
    ) {
      state.status = 'succeeded';
      state.stage = action.payload.stage;
      state.currentWeek = action.payload.currentWeek;
      state.selectedWeek = action.payload.selectedWeek;
      state.weeks = action.payload.weeks;
      state.heroTitle = action.payload.heroTitle;
      state.heroBody = action.payload.heroBody;
      state.progress = action.payload.progress;
      state.weeksToGo = action.payload.weeksToGo;
      state.path = action.payload.path;
      state.editorialCards = action.payload.editorialCards;
      state.error = null;
    },
    journeyLoadFailed(state, action: PayloadAction<string>) {
      state.status = 'failed';
      state.error = action.payload;
    },
    setSelectedJourneyWeek(state, action: PayloadAction<number>) {
      state.selectedWeek = action.payload;
      state.weeks = state.weeks.map((week) => ({
        ...week,
        selected: week.week === action.payload,
      }));
    },
    setJourneyStage(state, action: PayloadAction<JourneyStage>) {
      state.stage = action.payload;
    },
    resetJourneyState() {
      return initialState;
    },
  },
});

export const {
  journeyLoaded,
  journeyLoadFailed,
  journeyLoadStarted,
  resetJourneyState,
  setJourneyStage,
  setSelectedJourneyWeek,
} = journeySlice.actions;

export default journeySlice.reducer;
