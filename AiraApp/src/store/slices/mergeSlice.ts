import { createSlice, type PayloadAction } from '@reduxjs/toolkit';

type MergeStatus = 'idle' | 'loading' | 'ready' | 'submitting' | 'completed' | 'cancelled' | 'failed';

export type MergeConflictOption = {
  id: string;
  label: string;
  source: 'device' | 'account' | 'latest';
};

export type MergeConflict = {
  id: string;
  title: string;
  description?: string;
  selectedOptionId: string;
  options: MergeConflictOption[];
};

type MergeState = {
  status: MergeStatus;
  conflicts: MergeConflict[];
  hasConflicts: boolean;
  error: string | null;
};

const initialState: MergeState = {
  status: 'idle',
  conflicts: [],
  hasConflicts: false,
  error: null,
};

const mergeSlice = createSlice({
  name: 'merge',
  initialState,
  reducers: {
    mergeConflictsStarted(state) {
      state.status = 'loading';
      state.error = null;
    },
    mergeConflictsLoaded(state, action: PayloadAction<MergeConflict[]>) {
      state.status = 'ready';
      state.conflicts = action.payload;
      state.hasConflicts = action.payload.length > 0;
      state.error = null;
    },
    mergeConflictsFailed(state, action: PayloadAction<string>) {
      state.status = 'failed';
      state.error = action.payload;
    },
    setMergeConflictChoice(
      state,
      action: PayloadAction<{ conflictId: string; optionId: string }>,
    ) {
      const conflict = state.conflicts.find((item) => item.id === action.payload.conflictId);

      if (conflict) {
        conflict.selectedOptionId = action.payload.optionId;
      }
    },
    mergeSubmitStarted(state) {
      state.status = 'submitting';
      state.error = null;
    },
    mergeSubmitSucceeded(state) {
      state.status = 'completed';
      state.error = null;
    },
    mergeSubmitFailed(state, action: PayloadAction<string>) {
      state.status = 'failed';
      state.error = action.payload;
    },
    mergeCancelled(state) {
      state.status = 'cancelled';
      state.conflicts = [];
      state.hasConflicts = false;
      state.error = null;
    },
    resetMergeState() {
      return initialState;
    },
  },
});

export const {
  mergeCancelled,
  mergeConflictsFailed,
  mergeConflictsLoaded,
  mergeConflictsStarted,
  mergeSubmitFailed,
  mergeSubmitStarted,
  mergeSubmitSucceeded,
  resetMergeState,
  setMergeConflictChoice,
} = mergeSlice.actions;

export default mergeSlice.reducer;
