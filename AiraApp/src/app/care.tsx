import { router } from 'expo-router';
import { useState } from 'react';
import { Pressable, ScrollView, Text, View } from 'react-native';
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
        <Text className="font-serif text-2xl text-foreground">Today&apos;s care</Text>
      </View>
    </View>
  );
}

function ProgressDots({ done, total }: { done: number; total: number }) {
  return (
    <View className="mt-4 flex-row gap-2">
      {Array.from({ length: total }).map((_, index) => {
        const on = index < done;
        return (
          <View
            key={index}
            className={on ? 'h-4 w-4 rounded-full bg-brand' : 'h-4 w-4 rounded-full border border-brand'}
          />
        );
      })}
    </View>
  );
}

function TaskCard({
  icon,
  title,
  subtitle,
  complete,
  children,
}: {
  icon: React.ComponentProps<typeof Icon>['name'];
  title: string;
  subtitle: string;
  complete?: boolean;
  children?: React.ReactNode;
}) {
  return (
    <View className="rounded-[20px] border border-border bg-card p-5">
      <View className="flex-row items-start gap-3">
        <Icon name={icon} size={23} className="mt-1 text-brand" />
        <View className="flex-1">
          <Text className="font-rubik-semibold text-base text-foreground">{title}</Text>
          <Text className="font-rubik text-xs text-muted-foreground">{subtitle}</Text>
        </View>
        <View className="h-12 w-12 items-center justify-center rounded-full bg-secondary">
          <Icon name="check" size={22} className="text-brand" />
        </View>
      </View>

      {children && <View className="mt-4">{children}</View>}
    </View>
  );
}

export default function CareScreen() {
  const [remindersOn, setRemindersOn] = useState(true);
  const [showRoughDay, setShowRoughDay] = useState(true);

  return (
    <SafeAreaView className="flex-1 bg-background" edges={['top', 'bottom']}>
      <Header />

      <ScrollView
        contentContainerClassName="gap-4 px-5 pb-8 pt-5"
        showsVerticalScrollIndicator={false}>
        <View className="flex-row items-center justify-between">
          <Text className="font-rubik-semibold text-xs uppercase tracking-widest text-brand">
            Progress
          </Text>
          <Pressable accessibilityRole="button" className="active:opacity-70">
            <Text className="font-rubik-semibold text-sm text-brand">
              🛟 Snooze all today
            </Text>
          </Pressable>
        </View>

        <View className="flex-row items-start gap-3 rounded-lg bg-secondary px-4 py-3.5">
          <Icon name="info" size={20} className="mt-0.5 text-tile-foreground" />
          <Text className="flex-1 font-rubik text-xs leading-relaxed text-tile-foreground">
            Snoozing stops routine alerts until tomorrow. Tasks stay visible and can be
            restored immediately.
          </Text>
        </View>

        {showRoughDay && (
          <View className="flex-row items-start gap-3 rounded-lg bg-notice px-4 py-3.5">
            <Icon name="bell" size={21} className="mt-0.5 text-foreground" />
            <Text className="flex-1 font-rubik text-xs leading-relaxed text-notice-foreground">
              Having a rough day? Snooze all today mutes every reminder at once —
              nothing&apos;s lost.
            </Text>
            <Pressable
              onPress={() => setShowRoughDay(false)}
              accessibilityRole="button"
              accessibilityLabel="Dismiss snooze hint"
              hitSlop={10}
              className="active:opacity-60">
              <Icon name="x" size={20} className="text-notice-foreground" />
            </Pressable>
          </View>
        )}

        <View className="rounded-[20px] border border-border bg-card px-5 py-4">
          <Text className="font-serif text-[26px] leading-8 text-foreground">
            1 of 3 done today
          </Text>
        </View>

        <TaskCard icon="droplet" title="Drink water" subtitle="3 of 8 today" complete>
          <ProgressDots done={3} total={8} />

          <View className="mt-4 h-px bg-border" />

          <View className="mt-4 flex-row items-center justify-between gap-3">
            <View className="flex-row items-center gap-3">
              <Icon name="bell" size={23} className="text-brand" />
              <Text className="font-rubik-semibold text-base text-foreground">Reminders</Text>
            </View>
            <Pressable
              onPress={() => setRemindersOn((v) => !v)}
              accessibilityRole="switch"
              accessibilityState={{ checked: remindersOn }}
              className="active:opacity-80">
              <View
                className={
                  remindersOn
                    ? 'h-8 w-14 items-end justify-center rounded-full bg-brand px-1'
                    : 'h-8 w-14 items-start justify-center rounded-full bg-muted px-1'
                }>
                <View className="h-6 w-6 rounded-full bg-card" />
              </View>
            </Pressable>
          </View>

          <View className="mt-3 flex-row gap-2.5">
            <View className="flex-row items-center gap-2 rounded-full bg-secondary px-4 py-3">
              <Text className="font-rubik-medium text-sm text-brand">10:00</Text>
              <Icon name="x" size={16} className="text-brand" />
            </View>
            <Pressable accessibilityRole="button" className="active:opacity-80">
              <View className="flex-row items-center gap-2 rounded-full border border-border bg-card px-4 py-3">
                <Icon name="plus" size={16} className="text-brand" />
                <Text className="font-rubik-medium text-sm text-brand">Add time</Text>
              </View>
            </Pressable>
          </View>
        </TaskCard>

        <TaskCard icon="link" title="Prenatal vitamin" subtitle="1 tablet · morning" complete>
          <View className="h-px bg-border" />
          <View className="mt-4 flex-row items-center gap-3">
            <Icon name="bell" size={23} className="text-brand" />
            <Text className="font-rubik text-sm text-muted-foreground">
              Reminds you at 09:00
            </Text>
          </View>
        </TaskCard>

        <Pressable accessibilityRole="button" className="active:opacity-80">
          <View className="flex-row items-center justify-center gap-2 rounded-[18px] border border-brand bg-card px-5 py-4">
            <Icon name="plus" size={20} className="text-brand" />
            <Text className="font-rubik-semibold text-base text-brand">Add care task</Text>
          </View>
        </Pressable>
      </ScrollView>
    </SafeAreaView>
  );
}
