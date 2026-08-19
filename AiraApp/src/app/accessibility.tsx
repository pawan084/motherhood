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
        <Text className="font-rubik-semibold text-lg text-foreground">Accessibility</Text>
      </View>
    </View>
  );
}

function Toggle({ on, onPress }: { on: boolean; onPress: () => void }) {
  return (
    <Pressable
      onPress={onPress}
      accessibilityRole="switch"
      accessibilityState={{ checked: on }}
      className="active:opacity-80">
      <View
        className={
          on
            ? 'h-8 w-12 items-end justify-center rounded-full bg-brand px-1'
            : 'h-8 w-12 items-start justify-center rounded-full bg-[#C9C2BD] px-1'
        }>
        <View className="h-6 w-6 rounded-full bg-card" />
      </View>
    </Pressable>
  );
}

function Row({
  title,
  note,
  on,
  onToggle,
}: {
  title: string;
  note: string;
  on: boolean;
  onToggle: () => void;
}) {
  return (
    <View className="flex-row items-center gap-3 py-3">
      <View className="flex-1">
        <Text className="font-rubik-semibold text-base text-foreground">{title}</Text>
        <Text className="font-rubik text-xs text-muted-foreground">{note}</Text>
      </View>
      <Toggle on={on} onPress={onToggle} />
    </View>
  );
}

export default function AccessibilityScreen() {
  const [largerText, setLargerText] = useState(true);
  const [reduceMotion, setReduceMotion] = useState(false);
  const [highContrast, setHighContrast] = useState(false);
  const [captions, setCaptions] = useState(true);
  const [extendedTap, setExtendedTap] = useState(false);

  return (
    <SafeAreaView className="flex-1 bg-background" edges={['top', 'bottom']}>
      <Header />

      <View className="flex-1 gap-5 px-5 pt-5">
        <View className="rounded-[20px] border border-border bg-card px-5 py-3">
          <Row
            title="Larger text"
            note="Follow Android text size"
            on={largerText}
            onToggle={() => setLargerText((v) => !v)}
          />
          <View className="h-px bg-border" />
          <Row
            title="Reduce motion"
            note="Limit pulsing and transitions"
            on={reduceMotion}
            onToggle={() => setReduceMotion((v) => !v)}
          />
          <View className="h-px bg-border" />
          <Row
            title="High contrast"
            note="Stronger borders and text"
            on={highContrast}
            onToggle={() => setHighContrast((v) => !v)}
          />
        </View>

        <View className="rounded-[20px] border border-border bg-card px-5 py-3">
          <Row
            title="Video captions"
            note="Always show when available"
            on={captions}
            onToggle={() => setCaptions((v) => !v)}
          />
          <View className="h-px bg-border" />
          <Row
            title="Extended tap time"
            note="Prevent accidental actions"
            on={extendedTap}
            onToggle={() => setExtendedTap((v) => !v)}
          />
        </View>

        <Pressable
          onPress={() => router.back()}
          accessibilityRole="button"
          className="active:opacity-90">
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
              Save accessibility preferences
            </Text>
          </View>
        </Pressable>
      </View>
    </SafeAreaView>
  );
}
