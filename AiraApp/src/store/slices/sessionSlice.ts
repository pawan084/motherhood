import { createSlice, type PayloadAction } from '@reduxjs/toolkit';

type UserProfile = {
  id: string;
  kind: 'device' | 'account';
  email?: string | null;
  name?: string;
  journey?: string;
  onboarded?: boolean;
};

type SessionState = {
  token: string | null;
  user: UserProfile | null;
  status: 'idle' | 'loading' | 'authenticated' | 'guest' | 'signedOut';
};

const initialState: SessionState = {
  token: null,
  user: null,
  status: 'idle',
};

const sessionSlice = createSlice({
  name: 'session',
  initialState,
  reducers: {
    setSession(
      state,
      action: PayloadAction<{ token: string; user: UserProfile; guest?: boolean }>,
    ) {
      state.token = action.payload.token;
      state.user = action.payload.user;
      state.status = action.payload.guest ? 'guest' : 'authenticated';
    },
    setSessionLoading(state) {
      state.status = 'loading';
    },
    clearSession(state) {
      state.token = null;
      state.user = null;
      state.status = 'signedOut';
    },
  },
});

export const { clearSession, setSession, setSessionLoading } = sessionSlice.actions;
export default sessionSlice.reducer;

