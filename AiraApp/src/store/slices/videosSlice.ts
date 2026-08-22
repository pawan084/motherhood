import { createSlice, type PayloadAction } from '@reduxjs/toolkit';

type LoadStatus = 'idle' | 'loading' | 'succeeded' | 'failed';

export type VideoTiming = {
  type: 'gestational_week' | 'on_demand';
  startWeek: number | null;
  endWeek: number | null;
};

export type VideoTopic = {
  id: string;
  slug: string;
  title: string;
  category: string;
  categoryLabel: string;
  journeys: string[];
  timing: VideoTiming;
  description: string;
  durationLabel: string;
  languages: string[];
  /**
   * Whether there is anything to play. FALSE for every topic in the catalogue
   * today — the 100 topics are written, not filmed — so a screen must render a
   * "reviewed" state rather than a play glyph it cannot honour.
   */
  playable: boolean;
  mediaUrl: string | null;
  /** True when `mediaUrl` is the backend's stand-in rather than a real video. */
  mediaIsPlaceholder: boolean;
  saved: boolean;
};

export type VideoCategory = { key: string; label: string };

type VideosState = {
  status: LoadStatus;
  items: VideoTopic[];
  /** The one picked for the current gestational week; null off a week band. */
  weekVideo: VideoTopic | null;
  categories: VideoCategory[];
  savedIds: string[];
  activeCategory: string | null;
  query: string;
  /** The topic open in the player, if any. */
  playingId: string | null;
  error: string | null;
};

const initialState: VideosState = {
  status: 'idle',
  items: [],
  weekVideo: null,
  categories: [],
  savedIds: [],
  activeCategory: null,
  query: '',
  playingId: null,
  error: null,
};

const videosSlice = createSlice({
  name: 'videos',
  initialState,
  reducers: {
    videosLoadStarted(state) {
      state.status = 'loading';
      state.error = null;
    },
    videosLoaded(
      state,
      action: PayloadAction<{
        items: VideoTopic[];
        weekVideo: VideoTopic | null;
        categories: VideoCategory[];
        savedIds: string[];
      }>,
    ) {
      state.status = 'succeeded';
      state.items = action.payload.items;
      state.weekVideo = action.payload.weekVideo;
      state.categories = action.payload.categories;
      state.savedIds = action.payload.savedIds;
      state.error = null;
    },
    videosLoadFailed(state, action: PayloadAction<string>) {
      state.status = 'failed';
      state.error = action.payload;
    },
    setVideoCategory(state, action: PayloadAction<string | null>) {
      state.activeCategory = action.payload;
    },
    setVideoQuery(state, action: PayloadAction<string>) {
      state.query = action.payload;
    },
    /**
     * Optimistic save. The saved flag lives in two places the server also keeps
     * apart — the id list and the item — so both move together here rather than
     * leaving a screen reading a stale one.
     */
    setVideoSaved(state, action: PayloadAction<{ id: string; saved: boolean }>) {
      const { id, saved } = action.payload;
      state.savedIds = saved
        ? Array.from(new Set([...state.savedIds, id]))
        : state.savedIds.filter((x) => x !== id);

      const item = state.items.find((v) => v.id === id);
      if (item) item.saved = saved;
      if (state.weekVideo?.id === id) state.weekVideo.saved = saved;
    },
    setPlayingVideo(state, action: PayloadAction<string | null>) {
      state.playingId = action.payload;
    },
    resetVideosState() {
      return initialState;
    },
  },
});

export const {
  resetVideosState,
  setPlayingVideo,
  setVideoCategory,
  setVideoQuery,
  setVideoSaved,
  videosLoaded,
  videosLoadFailed,
  videosLoadStarted,
} = videosSlice.actions;

export default videosSlice.reducer;
