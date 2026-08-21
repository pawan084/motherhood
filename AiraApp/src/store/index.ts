import { configureStore } from '@reduxjs/toolkit';

import appReducer from './slices/appSlice';
import sessionReducer from './slices/sessionSlice';

export const store = configureStore({
  reducer: {
    app: appReducer,
    session: sessionReducer,
  },
});

export type RootState = ReturnType<typeof store.getState>;
export type AppDispatch = typeof store.dispatch;

