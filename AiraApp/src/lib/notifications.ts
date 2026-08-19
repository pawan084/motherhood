import AsyncStorage from '@react-native-async-storage/async-storage';
import * as Notifications from 'expo-notifications';
import * as TaskManager from 'expo-task-manager';
import { Platform } from 'react-native';

/**
 * Local reminders, and the quick actions on them.
 *
 * ── Local, not push ──
 *
 * These are scheduled ON THE DEVICE from the cadence chosen in onboarding step
 * 4. There is no server-side scheduler — no job runner, no push credentials —
 * and for water and vitamin nudges that is the right architecture anyway: the
 * device knows the time, works offline, and never needs the reminder times to
 * leave it.
 *
 * ── The point of the action buttons ──
 *
 * `opensAppToForeground: false` on both actions is what makes "logging a glass
 * never requires opening the app" true rather than a slogan. The response is
 * handled in a background task, so the tap writes and the phone stays on the
 * lock screen.
 *
 * ── Where this does NOT work ──
 *
 *   - Expo Go: background tasks are unavailable, so the buttons appear and the
 *     taps go nowhere. A development build is required.
 *   - Web: no equivalent, so every function here no-ops.
 *   - Android: an action response only arrives while the app is backgrounded or
 *     terminated — which is the case this is for, but it means you cannot test
 *     it with the app open in the foreground.
 */

/** No `:` or `-` — those characters break category matching. */
export const WATER_CATEGORY = 'airaWater';
export const MOOD_CATEGORY = 'airaMood';
export const ACTION_ADD_GLASS = 'addGlass';
export const ACTION_SNOOZE = 'snoozeOneHour';

/**
 * Which reminder a notification came from, carried in its `data`.
 *
 * The split is the whole design: WATER can be answered by a button, because the
 * answer is always "one glass". MOOD cannot — "which mood" needs six options,
 * more than a notification can hold — so that one opens a small popup instead
 * of trying to cram a picker into the shade.
 */
export const KIND_WATER = 'water';
export const KIND_MOOD = 'mood';

const BACKGROUND_TASK = 'aira-notification-response';
const GLASSES_KEY = 'aira_glasses_today';
const MOOD_KEY = 'aira_last_mood';

const supported = Platform.OS !== 'web';

/** How the app behaves when a reminder fires while it is open. */
Notifications.setNotificationHandler({
  handleNotification: async () => ({
    shouldShowBanner: true,
    shouldShowList: true,
    shouldPlaySound: false,
    shouldSetBadge: false,
  }),
});

/**
 * The background handler.
 *
 * Defined at module scope on purpose — the OS may start the app into this task
 * with no UI at all, so it cannot live inside a component.
 */
TaskManager.defineTask<Notifications.NotificationTaskPayload>(
  BACKGROUND_TASK,
  async ({ data, error }) => {
    if (error || !data || !('actionIdentifier' in data)) return;

    if (data.actionIdentifier === ACTION_ADD_GLASS) {
      await addGlass();
    } else if (data.actionIdentifier === ACTION_SNOOZE) {
      await snoozeOneHour();
    }
  },
);

/**
 * Ask for permission.
 *
 * Only ever called after the in-app soft ask has already got a yes: on iOS the
 * system prompt is one-shot, and a decline there can never be re-asked in-app.
 */
export async function requestPermission(): Promise<boolean> {
  if (!supported) return false;
  const existing = await Notifications.getPermissionsAsync();
  if (existing.granted) return true;

  const asked = await Notifications.requestPermissionsAsync({
    ios: { allowAlert: true, allowSound: true, allowBadge: false },
  });
  return asked.granted;
}

/** Register the two buttons that ride on every water reminder. */
export async function registerCategories(): Promise<void> {
  if (!supported) return;
  await Notifications.setNotificationCategoryAsync(WATER_CATEGORY, [
    {
      identifier: ACTION_ADD_GLASS,
      buttonTitle: 'Add a glass',
      // The whole point: log it without launching anything.
      options: { opensAppToForeground: false },
    },
    {
      identifier: ACTION_SNOOZE,
      buttonTitle: 'Remind in 1h',
      options: { opensAppToForeground: false },
    },
  ]);
  await Notifications.registerTaskAsync(BACKGROUND_TASK);
}

/**
 * Replace the schedule with one daily reminder per chosen time.
 *
 * Cancels first rather than adding: the cadence is edited from Settings, and
 * scheduling on top of an existing set is how somebody ends up with six
 * notifications a day after changing their mind twice.
 */
export async function scheduleWaterReminders(times: string[]): Promise<void> {
  if (!supported) return;
  await Notifications.cancelAllScheduledNotificationsAsync();

  for (const t of times) {
    const [hour, minute] = t.split(':').map(Number);
    if (Number.isNaN(hour) || Number.isNaN(minute)) continue;

    await Notifications.scheduleNotificationAsync({
      content: {
        title: 'Time for water',
        // Filled at fire time would be better; a scheduled notification's body
        // is fixed when it is created, so this stays deliberately vague rather
        // than stating a count that may be hours stale.
        body: 'A glass now keeps you on track for today.',
        categoryIdentifier: WATER_CATEGORY,
        sound: false,
        data: { kind: KIND_WATER },
      },
      trigger: {
        type: Notifications.SchedulableTriggerInputTypes.DAILY,
        hour,
        minute,
      },
    });
  }
}

/** One more glass, written locally. */
export async function addGlass(): Promise<number> {
  const raw = await AsyncStorage.getItem(GLASSES_KEY);
  const next = (Number(raw) || 0) + 1;
  await AsyncStorage.setItem(GLASSES_KEY, String(next));
  return next;
}

export async function glassesToday(): Promise<number> {
  const raw = await AsyncStorage.getItem(GLASSES_KEY);
  return Number(raw) || 0;
}

/** A single reminder an hour from now. Does not disturb the daily schedule. */
export async function snoozeOneHour(): Promise<void> {
  if (!supported) return;
  await Notifications.scheduleNotificationAsync({
    content: {
      title: 'Time for water',
      body: 'Picking up where you left off.',
      categoryIdentifier: WATER_CATEGORY,
      sound: false,
    },
    trigger: {
      type: Notifications.SchedulableTriggerInputTypes.TIME_INTERVAL,
      seconds: 60 * 60,
      repeats: false,
    },
  });
}

/**
 * The daily mood check-in.
 *
 * No action buttons: the useful answer is one of six moods, and a notification
 * cannot ask that. Tapping it opens the app just far enough to show the
 * quick-log popup over whatever was on screen.
 */
export async function scheduleMoodCheckIn(time: string): Promise<void> {
  if (!supported) return;
  const [hour, minute] = time.split(':').map(Number);
  if (Number.isNaN(hour) || Number.isNaN(minute)) return;

  await Notifications.scheduleNotificationAsync({
    content: {
      title: 'How are you today?',
      body: 'A ten-second check-in keeps your week accurate.',
      categoryIdentifier: MOOD_CATEGORY,
      sound: false,
      data: { kind: KIND_MOOD, label: labelForHour(hour) },
    },
    trigger: {
      type: Notifications.SchedulableTriggerInputTypes.DAILY,
      hour,
      minute,
    },
  });
}

/** "3 PM" — what the popup credits the prompt to. */
function labelForHour(hour: number): string {
  const suffix = hour < 12 ? 'AM' : 'PM';
  const h = hour % 12 === 0 ? 12 : hour % 12;
  return `${h} ${suffix}`;
}

/**
 * Was this response a mood reminder, and if so what should the popup credit it
 * to? Returns null for anything else, so the caller can ignore water taps.
 */
export function moodPromptLabel(
  response: Notifications.NotificationResponse | null,
): string | null {
  const data = response?.notification.request.content.data as
    | { kind?: string; label?: string }
    | undefined;
  if (!data || data.kind !== KIND_MOOD) return null;
  return data.label ?? 'your reminder';
}

/** The most recent mood check-in, written locally. */
export async function logMood(mood: string): Promise<void> {
  await AsyncStorage.setItem(MOOD_KEY, JSON.stringify({ mood, at: Date.now() }));
  // POST /v1/care/checkin { feeling: mood } once the API client exists —
  // `feeling` is the field this maps to, and it already accepts a free string.
}

/** Called from the soft ask's "Turn on notifications". */
export async function enableReminders(times: string[]): Promise<boolean> {
  const granted = await requestPermission();
  if (!granted) return false;
  await registerCategories();
  await scheduleWaterReminders(times);
  // One mood prompt a day, mid-afternoon — the popup's "From your 3 PM
  // reminder" is this.
  await scheduleMoodCheckIn('15:00');
  return true;
}
