import { router } from 'expo-router';
import { useState } from 'react';
import { Pressable, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { Icon } from '@/components/icon';

const OPTIONS = [
  { key: 'tomorrow', label: 'Until tomorrow morning', action: 'Pause until tomorrow' },
  { key: '3days', label: 'For 3 days', action: 'Pause for 3 days' },
  { key: 'week', label: 'For 1 week', action: 'Pause for 1 week' },
  { key: 'date', label: 'Choose a date', action: 'Choose date' },
] as const;

function Header() {
  return (
    <View className="border-b border-border bg-card px-5 pb-4 pt-2">
      <View className="flex-row items-center gap-4">
        <Pressable
          onPress={() => router.back()}
          accessibilityRole="button"
          accessibilityLabel="Go back"
          className="active:opacity-70">
          <View className="h-11 w-11 items-center justify-center rounded-full border border-border bg-card">
            <Icon name="arrow-left" size={22} className="text-foreground" />
          </View>
        </Pressable>
        <Text className="font-rubik-semibold text-lg text-foreground">Pause reminders</Text>
      </View>
    </View>
  );
}

export default function PauseRemindersScreen() {
  const [selected, setSelected] = useState<(typeof OPTIONS)[number]['key']>('tomorrow');
  const action = OPTIONS.find((option) => option.key === selected)?.action ?? 'Pause reminders';

  return (
    <SafeAreaView className="flex-1 bg-background" edges={['top', 'bottom']}>
      <Header />

      <View className="flex-1 px-5 pb-8 pt-6">
        <Text className="font-serif text-[28px] leading-9 text-foreground">
          Take the time you need
        </Text>
        <Text className="mt-2 font-rubik text-sm leading-relaxed text-muted-foreground">
          Care tasks stay visible; routine notifications pause.
        </Text>

        <View className="mt-5 gap-2.5">
          {OPTIONS.map((option) => {
            const on = selected === option.key;
            return (
              <Pressable
                key={option.key}
                onPress={() => setSelected(option.key)}
                accessibilityRole="radio"
                accessibilityState={{ selected: on }}
                className="active:opacity-80">
                <View
                  className={
                    on
                      ? 'min-h-[48px] justify-center rounded-[16px] border-2 border-brand bg-secondary px-4'
                      : 'min-h-[48px] justify-center rounded-[16px] border border-border bg-card px-4'
                  }>
                  <Text className="font-rubik-semibold text-sm text-foreground">
                    {option.label}
                  </Text>
                </View>
              </Pressable>
            );
          })}
        </View>

        <View className="mt-4 flex-row items-start gap-3 rounded-lg bg-secondary px-4 py-3.5">
          <Icon name="info" size={20} className="mt-0.5 text-tile-foreground" />
          <Text className="flex-1 font-rubik text-xs leading-relaxed text-tile-foreground">
            Appointment and emergency alerts are never paused.
          </Text>
        </View>

        <Pressable
          onPress={() => router.back()}
          accessibilityRole="button"
          className="mt-5 active:opacity-90">
          <View
            className="items-center rounded-[16px] bg-brand px-5 py-4"
            style={{
              shadowColor: '#7527F5',
              shadowOpacity: 0.28,
              shadowRadius: 18,
              shadowOffset: { width: 0, height: 10 },
              elevation: 8,
            }}>
            <Text className="font-rubik-semibold text-base text-brand-foreground">
              {action}
            </Text>
          </View>
        </Pressable>

        <Pressable
          onPress={() => router.back()}
          accessibilityRole="button"
          className="mt-3 active:opacity-80">
          <View className="items-center rounded-[16px] border border-border bg-card px-5 py-4">
            <Text className="font-rubik-semibold text-base text-brand">
              Keep reminders on
            </Text>
          </View>
        </Pressable>
      </View>
    </SafeAreaView>
  );
}
