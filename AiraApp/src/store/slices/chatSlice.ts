import { createSlice, type PayloadAction } from '@reduxjs/toolkit';

type SendStatus = 'idle' | 'sending' | 'succeeded' | 'failed';
type SafetyMode = 'normal' | 'escalation' | 'offline';
type MessageRole = 'user' | 'assistant' | 'system';
type ChatTool =
  | 'checkIn'
  | 'reminder'
  | 'careVault'
  | 'reset'
  | 'track'
  | 'companion'
  | 'calmMatch';

export type ChatMessage = {
  id: string;
  role: MessageRole;
  text: string;
  createdAt: string;
  safetyChecked?: boolean;
  flaggedForCare?: boolean;
};

export type ChatQuickReply = {
  id: string;
  label: string;
  message: string;
  route?: string | null;
};

export type ChatNextAction = {
  id: string;
  eyebrow: string;
  title: string;
  body: string;
  route: string;
};

type ChatState = {
  messages: ChatMessage[];
  input: string;
  sendStatus: SendStatus;
  typing: boolean;
  safetyMode: SafetyMode;
  inputPaused: boolean;
  toolsOpen: boolean;
  feedbackOpen: boolean;
  selectedTool: ChatTool | null;
  memoryIndicator: string | null;
  quickReplies: ChatQuickReply[];
  nextAction: ChatNextAction | null;
  toolsHintVisible: boolean;
  error: string | null;
};

const initialState: ChatState = {
  messages: [],
  input: '',
  sendStatus: 'idle',
  typing: false,
  safetyMode: 'normal',
  inputPaused: false,
  toolsOpen: false,
  feedbackOpen: false,
  selectedTool: null,
  memoryIndicator: null,
  quickReplies: [],
  nextAction: null,
  toolsHintVisible: true,
  error: null,
};

const chatSlice = createSlice({
  name: 'chat',
  initialState,
  reducers: {
    chatHydrated(
      state,
      action: PayloadAction<{
        messages: ChatMessage[];
        memoryIndicator: string | null;
        quickReplies: ChatQuickReply[];
        nextAction: ChatNextAction | null;
        safetyMode: SafetyMode;
      }>,
    ) {
      state.messages = action.payload.messages;
      state.memoryIndicator = action.payload.memoryIndicator;
      state.quickReplies = action.payload.quickReplies;
      state.nextAction = action.payload.nextAction;
      state.safetyMode = action.payload.safetyMode;
      state.inputPaused = action.payload.safetyMode === 'offline';
      state.error = null;
    },
    setChatInput(state, action: PayloadAction<string>) {
      state.input = action.payload;
      state.error = null;
    },
    sendMessageStarted(state, action: PayloadAction<ChatMessage>) {
      state.sendStatus = 'sending';
      state.typing = true;
      state.messages.push(action.payload);
      state.input = '';
      state.error = null;
    },
    sendMessageSucceeded(
      state,
      action: PayloadAction<{
        assistantMessage: ChatMessage;
        quickReplies?: ChatQuickReply[];
        nextAction?: ChatNextAction | null;
        safetyMode?: SafetyMode;
      }>,
    ) {
      state.sendStatus = 'succeeded';
      state.typing = false;
      state.messages.push(action.payload.assistantMessage);
      state.quickReplies = action.payload.quickReplies ?? state.quickReplies;
      state.nextAction = action.payload.nextAction ?? state.nextAction;

      if (action.payload.safetyMode) {
        state.safetyMode = action.payload.safetyMode;
        state.inputPaused = action.payload.safetyMode === 'offline';
      }

      state.error = null;
    },
    sendMessageFailed(state, action: PayloadAction<string>) {
      state.sendStatus = 'failed';
      state.typing = false;
      state.error = action.payload;
    },
    setTyping(state, action: PayloadAction<boolean>) {
      state.typing = action.payload;
    },
    setSafetyMode(state, action: PayloadAction<SafetyMode>) {
      state.safetyMode = action.payload;
      state.inputPaused = action.payload === 'offline';
    },
    setChatInputPaused(state, action: PayloadAction<boolean>) {
      state.inputPaused = action.payload;
    },
    setChatToolsOpen(state, action: PayloadAction<boolean>) {
      state.toolsOpen = action.payload;
    },
    setFeedbackOpen(state, action: PayloadAction<boolean>) {
      state.feedbackOpen = action.payload;
    },
    selectChatTool(state, action: PayloadAction<ChatTool | null>) {
      state.selectedTool = action.payload;
      state.toolsOpen = false;
    },
    setMemoryIndicator(state, action: PayloadAction<string | null>) {
      state.memoryIndicator = action.payload;
    },
    setQuickReplies(state, action: PayloadAction<ChatQuickReply[]>) {
      state.quickReplies = action.payload;
    },
    setNextAction(state, action: PayloadAction<ChatNextAction | null>) {
      state.nextAction = action.payload;
    },
    setToolsHintVisible(state, action: PayloadAction<boolean>) {
      state.toolsHintVisible = action.payload;
    },
    resetChatState() {
      return initialState;
    },
  },
});

export const {
  chatHydrated,
  resetChatState,
  selectChatTool,
  sendMessageFailed,
  sendMessageStarted,
  sendMessageSucceeded,
  setChatInput,
  setChatInputPaused,
  setChatToolsOpen,
  setFeedbackOpen,
  setMemoryIndicator,
  setNextAction,
  setQuickReplies,
  setSafetyMode,
  setToolsHintVisible,
  setTyping,
} = chatSlice.actions;

export default chatSlice.reducer;
