import { createSlice, type PayloadAction } from '@reduxjs/toolkit';

type LoadStatus = 'idle' | 'loading' | 'succeeded' | 'failed';
type SubmitStatus = 'idle' | 'loading' | 'succeeded' | 'failed';

export type Appointment = {
  id: string;
  title: string;
  clinician: string;
  time: string;
  location: string;
};

export type VisitQuestion = {
  id: string;
  text: string;
  checked: boolean;
  source: 'chat' | 'user' | 'aira';
};

type VisitCopilotState = {
  status: LoadStatus;
  appointment: Appointment | null;
  questions: VisitQuestion[];
  suggestion: string | null;
  autosavedFromChat: boolean;
  draftQuestion: string;
  addAppointmentOpen: boolean;
  shareStatus: SubmitStatus;
  error: string | null;
};

const initialState: VisitCopilotState = {
  status: 'idle',
  appointment: null,
  questions: [],
  suggestion: null,
  autosavedFromChat: false,
  draftQuestion: '',
  addAppointmentOpen: false,
  shareStatus: 'idle',
  error: null,
};

const visitCopilotSlice = createSlice({
  name: 'visitCopilot',
  initialState,
  reducers: {
    visitCopilotLoadStarted(state) {
      state.status = 'loading';
      state.error = null;
    },
    visitCopilotLoaded(
      state,
      action: PayloadAction<{
        appointment: Appointment | null;
        questions: VisitQuestion[];
        suggestion: string | null;
        autosavedFromChat: boolean;
      }>,
    ) {
      state.status = 'succeeded';
      state.appointment = action.payload.appointment;
      state.questions = action.payload.questions;
      state.suggestion = action.payload.suggestion;
      state.autosavedFromChat = action.payload.autosavedFromChat;
      state.error = null;
    },
    visitCopilotLoadFailed(state, action: PayloadAction<string>) {
      state.status = 'failed';
      state.error = action.payload;
    },
    setAppointment(state, action: PayloadAction<Appointment | null>) {
      state.appointment = action.payload;
      state.addAppointmentOpen = false;
    },
    setAddAppointmentOpen(state, action: PayloadAction<boolean>) {
      state.addAppointmentOpen = action.payload;
    },
    setDraftVisitQuestion(state, action: PayloadAction<string>) {
      state.draftQuestion = action.payload;
    },
    addVisitQuestion(state, action: PayloadAction<VisitQuestion>) {
      state.questions.push(action.payload);
      state.draftQuestion = '';
    },
    updateVisitQuestion(
      state,
      action: PayloadAction<{ questionId: string; text: string }>,
    ) {
      const question = state.questions.find((item) => item.id === action.payload.questionId);

      if (question) {
        question.text = action.payload.text;
      }
    },
    removeVisitQuestion(state, action: PayloadAction<string>) {
      state.questions = state.questions.filter((question) => question.id !== action.payload);
    },
    setVisitQuestionChecked(
      state,
      action: PayloadAction<{ questionId: string; checked: boolean }>,
    ) {
      const question = state.questions.find((item) => item.id === action.payload.questionId);

      if (question) {
        question.checked = action.payload.checked;
      }
    },
    visitShareStarted(state) {
      state.shareStatus = 'loading';
      state.error = null;
    },
    visitShareSucceeded(state) {
      state.shareStatus = 'succeeded';
      state.error = null;
    },
    visitShareFailed(state, action: PayloadAction<string>) {
      state.shareStatus = 'failed';
      state.error = action.payload;
    },
    resetVisitCopilotState() {
      return initialState;
    },
  },
});

export const {
  addVisitQuestion,
  removeVisitQuestion,
  resetVisitCopilotState,
  setAddAppointmentOpen,
  setAppointment,
  setDraftVisitQuestion,
  setVisitQuestionChecked,
  updateVisitQuestion,
  visitCopilotLoaded,
  visitCopilotLoadFailed,
  visitCopilotLoadStarted,
  visitShareFailed,
  visitShareStarted,
  visitShareSucceeded,
} = visitCopilotSlice.actions;

export default visitCopilotSlice.reducer;
