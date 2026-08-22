import { combineReducers, configureStore } from '@reduxjs/toolkit';
import {
  FLUSH,
  PAUSE,
  PERSIST,
  PURGE,
  REGISTER,
  REHYDRATE,
  persistReducer,
  persistStore,
} from 'redux-persist';

import { persistStorage } from './storage';

import appReducer from './slices/appSlice';
import authReducer from './slices/authSlice';
import careReducer from './slices/careSlice';
import careVaultReducer from './slices/careVaultSlice';
import chatReducer from './slices/chatSlice';
import consentReducer from './slices/consentSlice';
import emergencyReducer from './slices/emergencySlice';
import journeyReducer from './slices/journeySlice';
import mergeReducer from './slices/mergeSlice';
import moodReducer from './slices/moodSlice';
import onboardingReducer from './slices/onboardingSlice';
import prefsReducer from './slices/prefsSlice';
import sessionReducer from './slices/sessionSlice';
import todayReducer from './slices/todaySlice';
import videosReducer from './slices/videosSlice';
import visitCopilotReducer from './slices/visitCopilotSlice';
import wellnessReducer from './slices/wellnessSlice';

const rootReducer = combineReducers({
  app: appReducer,
  auth: authReducer,
  care: careReducer,
  careVault: careVaultReducer,
  chat: chatReducer,
  consent: consentReducer,
  emergency: emergencyReducer,
  journey: journeyReducer,
  merge: mergeReducer,
  mood: moodReducer,
  onboarding: onboardingReducer,
  prefs: prefsReducer,
  session: sessionReducer,
  today: todayReducer,
  videos: videosReducer,
  visitCopilot: visitCopilotReducer,
  wellness: wellnessReducer,
});

/**
 * What survives a restart — and, more importantly, what does not.
 *
 * `session` MUST persist. Without it every launch mints a brand-new anonymous
 * user via `/device/register` and silently orphans the previous one's care
 * data: it stays on the server attached to an id nothing points at any more.
 *
 * `prefs` persists because appearance, accessibility and reminder cadence are
 * DEVICE-local — there is no server field for most of them.
 *
 * Everything else is a cache of server state and is deliberately left out.
 * Rehydrating a stale copy is worse than showing nothing: a week count, a care
 * list or a safety verdict restored from last week is a screen confidently
 * displaying the wrong thing. Those slices reload on mount.
 */
const persistConfig = {
  key: 'aira',
  version: 1,
  storage: persistStorage,
  whitelist: ['session', 'prefs'],
};

export const store = configureStore({
  reducer: persistReducer(persistConfig, rootReducer),
  middleware: (getDefault) =>
    getDefault({
      serializableCheck: {
        // redux-persist dispatches these with non-serialisable payloads by
        // design; without the exemption every launch logs a warning storm.
        ignoredActions: [FLUSH, REHYDRATE, PAUSE, PERSIST, PURGE, REGISTER],
      },
    }),
});

export const persistor = persistStore(store);

export type RootState = ReturnType<typeof rootReducer>;
export type AppDispatch = typeof store.dispatch;
