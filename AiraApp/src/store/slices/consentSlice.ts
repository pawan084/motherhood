import { createSlice, type PayloadAction } from '@reduxjs/toolkit';

type LoadStatus = 'idle' | 'loading' | 'succeeded' | 'failed';

/**
 * One row of the consent ledger.
 *
 * `locked` and `available` are NOT the same as `granted: false`, and collapsing
 * them is the bug this registry exists to prevent:
 *
 *   locked          a permanent policy denial — health data for ads. A
 *                   statement, not a control, and the server refuses to grant it.
 *   available:false the feature does not exist in this build, so the consent
 *                   would govern nothing. The server refuses a grant for it too.
 *
 * A screen must render those two as explained states rather than switches
 * somebody can flip, or it ships a control that changes nothing.
 */
export type ConsentFeature = {
  key: string;
  label: string;
  granted: boolean;
  locked: boolean;
  available: boolean;
};

export type ConsentEvent = {
  feature: string;
  granted: boolean;
  ts: number;
  note: string;
};

type ConsentState = {
  status: LoadStatus;
  features: ConsentFeature[];
  /** The append-only history, for the privacy centre. */
  history: ConsentEvent[];
  /** Which key is mid-write, so one row can show a pending state alone. */
  pendingKey: string | null;
  error: string | null;
};

const initialState: ConsentState = {
  status: 'idle',
  features: [],
  history: [],
  pendingKey: null,
  error: null,
};

const consentSlice = createSlice({
  name: 'consent',
  initialState,
  reducers: {
    consentLoadStarted(state) {
      state.status = 'loading';
      state.error = null;
    },
    consentLoaded(state, action: PayloadAction<ConsentFeature[]>) {
      state.status = 'succeeded';
      state.features = action.payload;
      state.error = null;
    },
    consentLoadFailed(state, action: PayloadAction<string>) {
      state.status = 'failed';
      state.error = action.payload;
    },
    consentHistoryLoaded(state, action: PayloadAction<ConsentEvent[]>) {
      state.history = action.payload;
    },
    consentUpdateStarted(state, action: PayloadAction<string>) {
      state.pendingKey = action.payload;
      state.error = null;
    },
    /** The server returns the whole set back, so the whole set is replaced. */
    consentUpdated(state, action: PayloadAction<ConsentFeature[]>) {
      state.pendingKey = null;
      state.features = action.payload;
    },
    consentUpdateFailed(state, action: PayloadAction<string>) {
      state.pendingKey = null;
      state.error = action.payload;
    },
    resetConsentState() {
      return initialState;
    },
  },
});

export const {
  consentHistoryLoaded,
  consentLoaded,
  consentLoadFailed,
  consentLoadStarted,
  consentUpdated,
  consentUpdateFailed,
  consentUpdateStarted,
  resetConsentState,
} = consentSlice.actions;

export default consentSlice.reducer;
