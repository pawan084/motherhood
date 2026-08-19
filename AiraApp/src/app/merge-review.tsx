import { router } from 'expo-router';
import { useState } from 'react';
import { Pressable, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { Icon } from '@/components/icon';

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
        <Text className="font-rubik-semibold text-lg text-foreground">Review your merge</Text>
      </View>
    </View>
  );
}

function Choice({
  label,
  selected,
  onPress,
}: {
  label: string;
  selected: boolean;
  onPress: () => void;
}) {
  return (
    <Pressable
      onPress={onPress}
      accessibilityRole="radio"
      accessibilityState={{ selected }}
      className="active:opacity-80">
      <View
        className={
          selected
            ? 'rounded-[16px] border-2 border-brand bg-secondary px-4 py-3.5'
            : 'rounded-[16px] border border-border bg-card px-4 py-3.5'
        }>
        <Text className="font-rubik-semibold text-base text-foreground">{label}</Text>
      </View>
    </Pressable>
  );
}

export default function MergeReviewScreen() {
  const [moodChoice, setMoodChoice] = useState<'device' | 'account'>('device');
  const [vitaminChoice, setVitaminChoice] = useState<'latest' | 'account'>('latest');

  return (
    <SafeAreaView className="flex-1 bg-background" edges={['top', 'bottom']}>
      <Header />

      <View className="flex-1 gap-4 px-5 pb-8 pt-5">
        <View className="flex-row items-start gap-3 rounded-lg bg-secondary px-4 py-3.5">
          <Icon name="bell" size={21} className="mt-0.5 text-tile-foreground" />
          <Text className="flex-1 font-rubik text-xs leading-relaxed text-tile-foreground">
            Two mood entries and one reminder exist in both histories. Choose which
            version to keep.
          </Text>
        </View>

        <View className="rounded-[20px] border border-border bg-card px-5 py-5">
          <Text className="font-rubik-semibold text-xs uppercase tracking-widest text-brand">
            Mood · Tuesday
          </Text>
          <View className="mt-3 gap-2.5">
            <Choice
              label="Keep device entry · Tired, 8:10 PM"
              selected={moodChoice === 'device'}
              onPress={() => setMoodChoice('device')}
            />
            <Choice
              label="Keep account entry · Okay, 8:02 PM"
              selected={moodChoice === 'account'}
              onPress={() => setMoodChoice('account')}
            />
          </View>
        </View>

        <View className="rounded-[20px] border border-border bg-card px-5 py-5">
          <Text className="font-rubik-semibold text-xs uppercase tracking-widest text-brand">
            Vitamin reminder
          </Text>
          <View className="mt-3 gap-2.5">
            <Choice
              label="Keep latest · 09:00"
              selected={vitaminChoice === 'latest'}
              onPress={() => setVitaminChoice('latest')}
            />
            <Choice
              label="Keep account time · 08:30"
              selected={vitaminChoice === 'account'}
              onPress={() => setVitaminChoice('account')}
            />
          </View>
        </View>

        <Pressable
          onPress={() => router.replace('/today')}
          accessibilityRole="button"
          className="mt-2 active:opacity-90">
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
              Merge selected history
            </Text>
          </View>
        </Pressable>

        <Pressable
          onPress={() => router.replace('/today')}
          accessibilityRole="button"
          className="active:opacity-80">
          <View className="items-center rounded-[16px] border border-border bg-card px-5 py-4">
            <Text className="font-rubik-semibold text-base text-brand">Cancel merge</Text>
          </View>
        </Pressable>
      </View>
    </SafeAreaView>
  );
}
