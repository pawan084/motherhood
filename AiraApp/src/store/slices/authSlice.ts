import { createSlice, type PayloadAction } from '@reduxjs/toolkit';

type AuthMode = 'signin' | 'signup';
type AuthRequestStatus = 'idle' | 'loading' | 'succeeded' | 'failed';

type AuthState = {
  mode: AuthMode;
  email: string;
  code: string;
  requestStatus: AuthRequestStatus;
  verifyStatus: AuthRequestStatus;
  error: string | null;
  expiresIn: number | null;
  resendIn: number | null;
  requestedAt: number | null;
  devCode: string | null;
};

const initialState: AuthState = {
  mode: 'signin',
  email: '',
  code: '',
  requestStatus: 'idle',
  verifyStatus: 'idle',
  error: null,
  expiresIn: null,
  resendIn: null,
  requestedAt: null,
  devCode: null,
};

const authSlice = createSlice({
  name: 'auth',
  initialState,
  reducers: {
    setAuthMode(state, action: PayloadAction<AuthMode>) {
      state.mode = action.payload;
    },
    setAuthEmail(state, action: PayloadAction<string>) {
      state.email = action.payload;
      state.error = null;
    },
    setAuthCode(state, action: PayloadAction<string>) {
      state.code = action.payload;
      state.error = null;
    },
    requestCodeStarted(state) {
      state.requestStatus = 'loading';
      state.error = null;
    },
    requestCodeSucceeded(
      state,
      action: PayloadAction<{
        expiresIn: number;
        resendIn: number;
        requestedAt: number;
        devCode?: string | null;
      }>,
    ) {
      state.requestStatus = 'succeeded';
      state.verifyStatus = 'idle';
      state.code = '';
      state.error = null;
      state.expiresIn = action.payload.expiresIn;
      state.resendIn = action.payload.resendIn;
      state.requestedAt = action.payload.requestedAt;
      state.devCode = action.payload.devCode ?? null;
    },
    requestCodeFailed(state, action: PayloadAction<string>) {
      state.requestStatus = 'failed';
      state.error = action.payload;
    },
    verifyCodeStarted(state) {
      state.verifyStatus = 'loading';
      state.error = null;
    },
    verifyCodeSucceeded(state) {
      state.verifyStatus = 'succeeded';
      state.error = null;
      state.devCode = null;
    },
    verifyCodeFailed(state, action: PayloadAction<string>) {
      state.verifyStatus = 'failed';
      state.error = action.payload;
    },
    resetAuthFlow() {
      return initialState;
    },
  },
});

export const {
  requestCodeFailed,
  requestCodeStarted,
  requestCodeSucceeded,
  resetAuthFlow,
  setAuthCode,
  setAuthEmail,
  setAuthMode,
  verifyCodeFailed,
  verifyCodeStarted,
  verifyCodeSucceeded,
} = authSlice.actions;

export default authSlice.reducer;
