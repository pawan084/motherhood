import { createSlice, type PayloadAction } from '@reduxjs/toolkit';

type LoadStatus = 'idle' | 'loading' | 'succeeded' | 'failed';
type ReminderPausePreset = 'tomorrow' | '3days' | 'week' | 'date';

export type CareReminder = {
  id: string;
  time: string;
  enabled: boolean;
};

export type CareTask = {
  id: string;
  title: string;
  subtitle: string;
  icon: string;
  complete: boolean;
  progressDone?: number | null;
  progressTotal?: number | null;
  reminders: CareReminder[];
};

type CareState = {
  status: LoadStatus;
  tasks: CareTask[];
  complete: number;
  total: number;
  snoozedUntil: string | null;
  pausePreset: ReminderPausePreset;
  notificationPermission: 'unknown' | 'granted' | 'denied';
  roughDayHintVisible: boolean;
  error: string | null;
};

const initialState: CareState = {
  status: 'idle',
  tasks: [],
  complete: 0,
  total: 0,
  snoozedUntil: null,
  pausePreset: 'tomorrow',
  notificationPermission: 'unknown',
  roughDayHintVisible: true,
  error: null,
};

function recalculateProgress(state: CareState) {
  state.total = state.tasks.length;
  state.complete = state.tasks.filter((task) => task.complete).length;
}

const careSlice = createSlice({
  name: 'care',
  initialState,
  reducers: {
    careLoadStarted(state) {
      state.status = 'loading';
      state.error = null;
    },
    careLoaded(
      state,
      action: PayloadAction<{
        tasks: CareTask[];
        snoozedUntil: string | null;
        notificationPermission: CareState['notificationPermission'];
      }>,
    ) {
      state.status = 'succeeded';
      state.tasks = action.payload.tasks;
      state.snoozedUntil = action.payload.snoozedUntil;
      state.notificationPermission = action.payload.notificationPermission;
      recalculateProgress(state);
      state.error = null;
    },
    careLoadFailed(state, action: PayloadAction<string>) {
      state.status = 'failed';
      state.error = action.payload;
    },
    setCareTaskComplete(
      state,
      action: PayloadAction<{ taskId: string; complete: boolean }>,
    ) {
      const task = state.tasks.find((item) => item.id === action.payload.taskId);

      if (task) {
        task.complete = action.payload.complete;
        recalculateProgress(state);
      }
    },
    setCareTaskProgress(
      state,
      action: PayloadAction<{ taskId: string; done: number; total: number }>,
    ) {
      const task = state.tasks.find((item) => item.id === action.payload.taskId);

      if (task) {
        task.progressDone = action.payload.done;
        task.progressTotal = action.payload.total;
      }
    },
    addCareTask(state, action: PayloadAction<CareTask>) {
      state.tasks.push(action.payload);
      recalculateProgress(state);
    },
    removeCareTask(state, action: PayloadAction<string>) {
      state.tasks = state.tasks.filter((task) => task.id !== action.payload);
      recalculateProgress(state);
    },
    setCareReminderEnabled(
      state,
      action: PayloadAction<{ taskId: string; reminderId: string; enabled: boolean }>,
    ) {
      const task = state.tasks.find((item) => item.id === action.payload.taskId);
      const reminder = task?.reminders.find((item) => item.id === action.payload.reminderId);

      if (reminder) {
        reminder.enabled = action.payload.enabled;
      }
    },
    addCareReminder(
      state,
      action: PayloadAction<{ taskId: string; reminder: CareReminder }>,
    ) {
      const task = state.tasks.find((item) => item.id === action.payload.taskId);

      if (task) {
        task.reminders.push(action.payload.reminder);
      }
    },
    removeCareReminder(
      state,
      action: PayloadAction<{ taskId: string; reminderId: string }>,
    ) {
      const task = state.tasks.find((item) => item.id === action.payload.taskId);

      if (task) {
        task.reminders = task.reminders.filter(
          (reminder) => reminder.id !== action.payload.reminderId,
        );
      }
    },
    setReminderPausePreset(state, action: PayloadAction<ReminderPausePreset>) {
      state.pausePreset = action.payload;
    },
    snoozeCareReminders(state, action: PayloadAction<string>) {
      state.snoozedUntil = action.payload;
    },
    restoreCareReminders(state) {
      state.snoozedUntil = null;
    },
    setCareNotificationPermission(
      state,
      action: PayloadAction<CareState['notificationPermission']>,
    ) {
      state.notificationPermission = action.payload;
    },
    setRoughDayHintVisible(state, action: PayloadAction<boolean>) {
      state.roughDayHintVisible = action.payload;
    },
    resetCareState() {
      return initialState;
    },
  },
});

export const {
  addCareReminder,
  addCareTask,
  careLoaded,
  careLoadFailed,
  careLoadStarted,
  removeCareReminder,
  removeCareTask,
  resetCareState,
  restoreCareReminders,
  setCareNotificationPermission,
  setCareReminderEnabled,
  setCareTaskComplete,
  setCareTaskProgress,
  setReminderPausePreset,
  setRoughDayHintVisible,
  snoozeCareReminders,
} = careSlice.actions;

export default careSlice.reducer;
