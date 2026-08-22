import { createSlice, type PayloadAction } from '@reduxjs/toolkit';

type LoadStatus = 'idle' | 'loading' | 'succeeded' | 'failed';

/**
 * The emergency profile — the single source of the urgent handoff's phone
 * number.
 *
 * `chat._urgent_payload` reads exactly these fields, so a red safety verdict
 * dials what is stored here and never a hardcoded demo number. When
 * `careTeamPhone` is null the urgent screen must fall back to local emergency
 * guidance rather than rendering a dead button.
 */
export type EmergencyProfile = {
  bloodGroup: string;
  allergies: string;
  hospital: string;
  notes: string;
  careTeamName: string;
  careTeamPhone: string;
  emergencyContactName: string;
  emergencyContactPhone: string;
};

export const emptyEmergencyProfile: EmergencyProfile = {
  bloodGroup: '',
  allergies: '',
  hospital: '',
  notes: '',
  careTeamName: '',
  careTeamPhone: '',
  emergencyContactName: '',
  emergencyContactPhone: '',
};

type EmergencyState = {
  status: LoadStatus;
  profile: EmergencyProfile;
  /**
   * The last profile seen on this device.
   *
   * Kept separately because the panel is badged "available offline" and has to
   * be true. It also guards a real hazard: PUT REPLACES the stored profile with
   * whatever it is sent, so saving from a form that failed to load would write
   * eight empty fields over a real one. A screen must refuse to save until it
   * has either loaded or restored this.
   */
  cached: EmergencyProfile | null;
  loadedFromCache: boolean;
  saving: boolean;
  error: string | null;
};

const initialState: EmergencyState = {
  status: 'idle',
  profile: emptyEmergencyProfile,
  cached: null,
  loadedFromCache: false,
  saving: false,
  error: null,
};

const emergencySlice = createSlice({
  name: 'emergency',
  initialState,
  reducers: {
    emergencyLoadStarted(state) {
      state.status = 'loading';
      state.error = null;
    },
    emergencyLoaded(state, action: PayloadAction<EmergencyProfile>) {
      state.status = 'succeeded';
      state.profile = action.payload;
      state.cached = action.payload;
      state.loadedFromCache = false;
      state.error = null;
    },
    emergencyLoadFailed(state, action: PayloadAction<string>) {
      state.status = 'failed';
      state.error = action.payload;
    },
    /** Offline: show the last known profile and mark it as such. */
    emergencyRestoredFromCache(state, action: PayloadAction<EmergencyProfile>) {
      state.profile = action.payload;
      state.cached = action.payload;
      state.loadedFromCache = true;
      state.status = 'succeeded';
    },
    setEmergencyField(
      state,
      action: PayloadAction<{ key: keyof EmergencyProfile; value: string }>,
    ) {
      state.profile[action.payload.key] = action.payload.value;
    },
    emergencySaveStarted(state) {
      state.saving = true;
      state.error = null;
    },
    emergencySaved(state, action: PayloadAction<EmergencyProfile>) {
      state.saving = false;
      state.profile = action.payload;
      state.cached = action.payload;
      state.loadedFromCache = false;
    },
    emergencySaveFailed(state, action: PayloadAction<string>) {
      state.saving = false;
      state.error = action.payload;
    },
    resetEmergencyState() {
      return initialState;
    },
  },
});

export const {
  emergencyLoaded,
  emergencyLoadFailed,
  emergencyLoadStarted,
  emergencyRestoredFromCache,
  emergencySaved,
  emergencySaveFailed,
  emergencySaveStarted,
  resetEmergencyState,
  setEmergencyField,
} = emergencySlice.actions;

export default emergencySlice.reducer;
