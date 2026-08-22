import { createAsyncThunk } from '@reduxjs/toolkit';

import * as api from '@/lib/api';
import { ApiError } from '@/lib/api';

import { consentLoaded, consentLoadFailed, consentLoadStarted, consentUpdated, consentUpdateFailed, consentUpdateStarted } from '../slices/consentSlice';
import {
  emergencyLoaded,
  emergencyLoadFailed,
  emergencyLoadStarted,
  emergencySaved,
  emergencySaveFailed,
  emergencySaveStarted,
} from '../slices/emergencySlice';
import {
  onboardingSubmitFailed,
  onboardingSubmitStarted,
  onboardingSubmitted,
} from '../slices/onboardingSlice';
import {
  prefsLoaded,
  prefsLoadFailed,
  prefsLoadStarted,
  prefsSaved,
  prefsSaveFailed,
  prefsSaveStarted,
  type Voice,
} from '../slices/prefsSlice';
import { clearSession, setSession, setSessionLoading } from '../slices/sessionSlice';
import { todayLoaded, todayLoadFailed, todayLoadStarted } from '../slices/todaySlice';
import {
  setVideoSaved,
  videosLoaded,
  videosLoadFailed,
  videosLoadStarted,
} from '../slices/videosSlice';

import type { RootState } from '../index';

/**
 * The async layer.
 *
 * Each thunk is the only place that knows an endpoint exists. Screens dispatch
 * one of these and read the slice; they never call the API directly, which is
 * what keeps the loading and error state in one shape across every screen.
 *
 * The slices already model `started/succeeded/failed`, so these dispatch those
 * rather than duplicating the state machine in `extraReducers`.
 */

/** Turns any failure into the string a slice's `error` field expects. */
function message(e: unknown): string {
  if (e instanceof ApiError) return e.message;
  if (e instanceof Error) return e.message;
  return 'Something went wrong';
}

// ── session ──────────────────────────────────────────────────────────────────

/**
 * Boot: make sure there is an identity, then find out who it belongs to.
 *
 * Anonymous-first — `registerDevice` mints a device user when there is no
 * token, so the app is usable from first launch with no account and nothing
 * asked of anybody.
 */
export const bootstrapSession = createAsyncThunk(
  'session/bootstrap',
  async (_: void, { dispatch }) => {
    dispatch(setSessionLoading());
    const token = await api.registerDevice();
    const user = await api.me();
    dispatch(
      setSession({ token, user, guest: user.kind === 'device' }),
    );
    return user;
  },
);

export const signUpWithEmail = createAsyncThunk(
  'session/signUp',
  async (creds: { email: string; password: string }, { dispatch }) => {
    const user = await api.signUp(creds.email, creds.password);
    const token = (await api.getToken()) ?? '';
    dispatch(setSession({ token, user }));
    return user;
  },
);

export const signInWithEmail = createAsyncThunk(
  'session/signIn',
  async (creds: { email: string; password: string }, { dispatch }) => {
    const user = await api.signIn(creds.email, creds.password);
    const token = (await api.getToken()) ?? '';
    dispatch(setSession({ token, user }));
    return user;
  },
);

export const signOutEverywhere = createAsyncThunk(
  'session/signOut',
  async (_: void, { dispatch }) => {
    await api.signOut();
    dispatch(clearSession());
  },
);

// ── today ────────────────────────────────────────────────────────────────────

/**
 * Today needs two calls, and neither can stand in for the other: `/v1/today`
 * carries the journey and week, `/v1/care` carries what is scheduled. They are
 * fetched together so the screen has one loading state rather than two.
 */
export const loadToday = createAsyncThunk(
  'today/load',
  async (_: void, { dispatch }) => {
    dispatch(todayLoadStarted());
    try {
      const [summary, care] = await Promise.all([api.getToday(), api.getCare()]);
      dispatch(
        todayLoaded({
          summary,
          water: { litres: 0, goalLitres: 2.5, averageLitres: 0, week: [] },
          care: { complete: care.planOnTrack, total: care.planTotal },
          moodKey: null,
          moodTrend: [],
          weekVideo: {
            title: summary.nextMilestone,
            blurb: summary.contextLine,
            weekLabel: summary.weeks === null ? '' : `Week ${summary.weeks}`,
            duration: '',
            reviewed: 'Educational content · not medical advice',
          },
          daysSinceCheckIn: 0,
          lastSyncedMinutesAgo: 0,
          askNotificationPermission: true,
        }),
      );
      return summary;
    } catch (e) {
      dispatch(todayLoadFailed(message(e)));
      throw e;
    }
  },
);

// ── onboarding ───────────────────────────────────────────────────────────────

/**
 * Submit the whole answer set at once.
 *
 * `POST /v1/onboarding` sets `onboarded: true` as it goes, so a per-step post
 * would mark somebody onboarded halfway through with a profile missing fields
 * every later screen reads.
 */
export const submitOnboarding = createAsyncThunk(
  'onboarding/submit',
  async (_: void, { dispatch, getState }) => {
    const { onboarding } = getState() as RootState;
    if (!onboarding.journey) throw new Error('Pick a stage first');

    dispatch(onboardingSubmitStarted());
    try {
      const summary = await api.submitOnboarding({
        journey: onboarding.journey,
        name: onboarding.name || undefined,
        language: onboarding.language ?? undefined,
        priorities: onboarding.priorities,
        weeks: onboarding.weeks,
      });
      dispatch(onboardingSubmitted());
      return summary;
    } catch (e) {
      dispatch(onboardingSubmitFailed(message(e)));
      throw e;
    }
  },
);

// ── videos ───────────────────────────────────────────────────────────────────

export const loadVideos = createAsyncThunk(
  'videos/load',
  async (_: void, { dispatch }) => {
    dispatch(videosLoadStarted());
    try {
      const v = await api.getVideos();
      dispatch(videosLoaded(v));
      return v;
    } catch (e) {
      dispatch(videosLoadFailed(message(e)));
      throw e;
    }
  },
);

/**
 * Optimistic: the star fills immediately and rolls back only if the write
 * fails. A save is cheap and reversible, so making somebody wait on a round
 * trip to see it costs more than the rare rollback.
 */
export const toggleVideoSaved = createAsyncThunk(
  'videos/toggleSaved',
  async (arg: { id: string; saved: boolean }, { dispatch }) => {
    dispatch(setVideoSaved(arg));
    try {
      await (arg.saved ? api.saveVideo(arg.id) : api.unsaveVideo(arg.id));
    } catch (e) {
      dispatch(setVideoSaved({ id: arg.id, saved: !arg.saved }));
      throw e;
    }
  },
);

// ── consent ──────────────────────────────────────────────────────────────────

export const loadConsent = createAsyncThunk(
  'consent/load',
  async (_: void, { dispatch }) => {
    dispatch(consentLoadStarted());
    try {
      const features = await api.getConsent();
      dispatch(consentLoaded(features));
      return features;
    } catch (e) {
      dispatch(consentLoadFailed(message(e)));
      throw e;
    }
  },
);

/**
 * NOT optimistic, unlike a video save.
 *
 * This is a permission. Showing it as granted before the server agrees means a
 * screen can claim consent that was refused — and the server does refuse, for
 * locked and unavailable features. Wait for the answer.
 */
export const updateConsent = createAsyncThunk(
  'consent/update',
  async (arg: { feature: string; granted: boolean }, { dispatch }) => {
    dispatch(consentUpdateStarted(arg.feature));
    try {
      const features = await api.setConsent(arg.feature, arg.granted);
      dispatch(consentUpdated(features));
      return features;
    } catch (e) {
      dispatch(consentUpdateFailed(message(e)));
      throw e;
    }
  },
);

// ── prefs ────────────────────────────────────────────────────────────────────

export const loadPrefs = createAsyncThunk(
  'prefs/load',
  async (_: void, { dispatch }) => {
    dispatch(prefsLoadStarted());
    try {
      const p = await api.getPrefs();
      dispatch(prefsLoaded(p));
      return p;
    } catch (e) {
      dispatch(prefsLoadFailed(message(e)));
      throw e;
    }
  },
);

export const savePrefs = createAsyncThunk(
  'prefs/save',
  async (patch: { voice?: Voice; spokenReplies?: boolean }, { dispatch }) => {
    dispatch(prefsSaveStarted());
    try {
      const p = await api.setPrefs(patch);
      dispatch(prefsSaved(p));
      return p;
    } catch (e) {
      dispatch(prefsSaveFailed(message(e)));
      throw e;
    }
  },
);

// ── emergency profile ────────────────────────────────────────────────────────

export const loadEmergencyProfile = createAsyncThunk(
  'emergency/load',
  async (_: void, { dispatch }) => {
    dispatch(emergencyLoadStarted());
    try {
      const p = await api.getEmergencyProfile();
      dispatch(emergencyLoaded(p));
      return p;
    } catch (e) {
      dispatch(emergencyLoadFailed(message(e)));
      throw e;
    }
  },
);

/**
 * Refuses to save a profile that was never loaded.
 *
 * PUT replaces the stored profile with whatever it is sent, so saving from a
 * form that failed to load would write eight empty fields over a real
 * care-team number — on the one screen that matters in an emergency.
 */
export const saveEmergencyProfile = createAsyncThunk(
  'emergency/save',
  async (_: void, { dispatch, getState }) => {
    const { emergency } = getState() as RootState;
    if (emergency.status !== 'succeeded') {
      throw new Error('Profile has not loaded yet — refusing to overwrite it');
    }

    dispatch(emergencySaveStarted());
    try {
      const saved = await api.putEmergencyProfile(emergency.profile);
      dispatch(emergencySaved(saved));
      return saved;
    } catch (e) {
      dispatch(emergencySaveFailed(message(e)));
      throw e;
    }
  },
);
