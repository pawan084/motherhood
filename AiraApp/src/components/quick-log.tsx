import * as Notifications from 'expo-notifications';
import { router } from 'expo-router';
import { useEffect, useState } from 'react';
import { Modal, Platform, Pressable, Text, View } from 'react-native';

import { BrandOrb } from '@/components/brand-orb';
import { Icon } from '@/components/icon';
import { MOODS } from '@/components/today/data';
import { logMood, moodPromptLabel } from '@/lib/notifications';

/**
 * The quick-log popup.
 *
 * For anything a notification button cannot capture. "One glass" fits on a
 * button; "which mood" is six options, so tapping a mood reminder opens THIS
 * over whatever was already on screen rather than launching the full app and
 * dropping somebody on Today with their place lost.
 *
 * It lives in the root layout for that reason — mounted above the navigation
 * stack, so it floats over Today, Chat, onboarding, anything.
 */

/**
 * Watches for a mood reminder being tapped.
 *
 * Two paths, and both are needed: `getLastNotificationResponseAsync` covers a
 * COLD start (the app was killed, the tap launched it — the listener would
 * never fire because it did not exist yet), and the listener covers a warm one.
 */
export function useMoodPrompt() {
  const [label, setLabel] = useState<string | null>(null);

  useEffect(() => {
    if (Platform.OS === 'web') return;
    let alive = true;

    Notifications.getLastNotificationResponseAsync().then((r) => {
      const l = moodPromptLabel(r);
      if (alive && l) setLabel(l);
    });

    const sub = Notifications.addNotificationResponseReceivedListener((r) => {
      const l = moodPromptLabel(r);
      if (l) setLabel(l);
    });

    return () => {
      alive = false;
      sub.remove();
    };
  }, []);

  return { label, dismiss: () => setLabel(null) };
}

export function QuickLogPopup({
  label,
  onDismiss,
}: {
  label: string | null;
  onDismiss: () => void;
}) {
  const [mood, setMood] = useState<string>('okay');

  return (
    <Modal
      visible={label !== null}
      transparent
      animationType="fade"
      onRequestClose={onDismiss}>
      <View className="flex-1 justify-center px-5">
        <Pressable
          accessibilityRole="button"
          accessibilityLabel="Dismiss"
          onPress={onDismiss}
          className="absolute inset-0">
          <View className="flex-1 bg-foreground opacity-60" />
        </Pressable>

        <View className="rounded-lg bg-card p-5">
          <View className="flex-row items-center gap-2.5">
            <BrandOrb size={30} />
            <Text className="font-rubik-semibold text-2xl text-foreground">
              Quick check-in
            </Text>
          </View>

          <Text className="mt-3 font-rubik text-sm leading-relaxed text-muted-foreground">
            From your {label} reminder — tap a mood to log it, or open the app for more.
          </Text>

          <View className="mt-4 flex-row justify-between gap-1.5">
            {MOODS.map((m) => {
              const on = mood === m.key;
              return (
                <Pressable
                  key={m.key}
                  onPress={() => setMood(m.key)}
                  accessibilityRole="radio"
                  accessibilityState={{ selected: on }}
                  className="flex-1 active:opacity-80">
                  <View
                    className={
                      on
                        ? 'items-center gap-1.5 rounded-lg bg-brand px-1 py-2.5'
                        : 'items-center gap-1.5 rounded-lg bg-secondary px-1 py-2.5'
                    }>
                    <Icon
                      name="smile"
                      size={18}
                      className={on ? 'text-brand-foreground' : 'text-tile-foreground'}
                    />
                    <Text
                      className={
                        on
                          ? 'font-rubik-medium text-xs text-brand-foreground'
                          : 'font-rubik text-xs text-muted-foreground'
                      }>
                      {m.label}
                    </Text>
                  </View>
                </Pressable>
              );
            })}
          </View>

          <Pressable
            onPress={() => {
              void logMood(mood);
              onDismiss();
            }}
            accessibilityRole="button"
            className="mt-5 active:opacity-90">
            <View className="items-center rounded-full bg-brand bg-gradient-to-br from-brand to-brand-deep py-4">
              <Text className="font-rubik-semibold text-base text-brand-foreground">
                Log &amp; dismiss
              </Text>
            </View>
          </Pressable>

          {/* The escape hatch, and the reason this popup is not a dead end: a
              check-in somebody wants to add a note to needs the real screen. */}
          <Pressable
            onPress={() => {
              onDismiss();
              router.push('/today');
            }}
            accessibilityRole="button"
            className="mt-3 items-center py-1 active:opacity-60">
            <Text className="font-rubik-semibold text-sm text-brand">
              Open in app instead
            </Text>
          </Pressable>
        </View>
      </View>
    </Modal>
  );
}
