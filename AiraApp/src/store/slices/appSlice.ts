import { createSlice, type PayloadAction } from '@reduxjs/toolkit';

type AppState = {
  hydrated: boolean;
  offline: boolean;
  activeStageDialog: boolean;
};

const initialState: AppState = {
  hydrated: false,
  offline: false,
  activeStageDialog: false,
};

const appSlice = createSlice({
  name: 'app',
  initialState,
  reducers: {
    setHydrated(state, action: PayloadAction<boolean>) {
      state.hydrated = action.payload;
    },
    setOffline(state, action: PayloadAction<boolean>) {
      state.offline = action.payload;
    },
    setStageDialogOpen(state, action: PayloadAction<boolean>) {
      state.activeStageDialog = action.payload;
    },
  },
});

export const { setHydrated, setOffline, setStageDialogOpen } = appSlice.actions;
export default appSlice.reducer;

