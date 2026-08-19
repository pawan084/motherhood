import { router } from 'expo-router';
import { useState } from 'react';
import { Pressable, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { Icon } from '@/components/icon';

const ITEMS = [
  'Ankle swelling since Tuesday',
  'Headaches, twice this week',
  'Confirm glucose test timing',
  'Ask about safe travel at week 26',
];

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
        <Text className="font-serif text-2xl text-foreground">Visit copilot</Text>
      </View>
    </View>
  );
}

export default function VisitCopilotScreen() {
  const [checked, setChecked] = useState<Record<string, boolean>>({
    [ITEMS[0]]: true,
  });
  const [hasAppointment, setHasAppointment] = useState(false);

  return (
    <SafeAreaView className="flex-1 bg-background" edges={['top', 'bottom']}>
      <Header />

      {hasAppointment ? (
      <View className="flex-1 gap-4 px-5 pb-8 pt-5">
        <View className="rounded-[20px] border border-border bg-secondary px-5 py-5">
          <Text className="font-rubik-semibold text-xs uppercase tracking-widest text-brand">
            Next appointment
          </Text>
          <Text className="mt-3 font-serif text-[22px] leading-7 text-foreground">
            Anomaly scan · Fri, 12 Sep
          </Text>
          <Text className="mt-1 font-rubik text-sm text-muted-foreground">
            Dr. Kapoor · 10:30 AM · Sunrise Clinic
          </Text>
        </View>

        <View className="flex-row items-center justify-between">
          <Text className="font-rubik-semibold text-xs uppercase tracking-widest text-brand">
            Things to raise (4)
          </Text>
          <Text className="font-rubik text-xs text-muted-foreground">Auto-saved from Chat</Text>
        </View>

        <View className="rounded-[20px] border border-border bg-card px-5 py-2">
          {ITEMS.map((item, index) => {
            const on = checked[item] ?? false;
            return (
              <View key={item}>
                <Pressable
                  onPress={() => setChecked((next) => ({ ...next, [item]: !on }))}
                  accessibilityRole="checkbox"
                  accessibilityState={{ checked: on }}
                  className="active:opacity-80">
                  <View className="flex-row items-center gap-4 py-4">
                    <View
                      className={
                        on
                          ? 'h-10 w-10 items-center justify-center rounded-full bg-brand'
                          : 'h-10 w-10 rounded-full bg-secondary'
                      }>
                      {on && <Icon name="check" size={22} className="text-brand-foreground" />}
                    </View>
                    <Text className="flex-1 font-rubik text-base text-foreground">{item}</Text>
                  </View>
                </Pressable>
                {index < ITEMS.length - 1 && <View className="ml-14 h-px bg-border" />}
              </View>
            );
          })}
        </View>

        <View className="rounded-[18px] border border-dashed border-border bg-card px-5 py-5">
          <Text className="text-center font-rubik-semibold text-sm text-brand">
            ✦ Aira suggests: ask about iron levels
          </Text>
          <Text className="mt-2 text-center font-rubik text-xs leading-relaxed text-muted-foreground">
            AI suggestion based on your recent fatigue chat—not a clinician priority. Edit
            or remove before sharing.
          </Text>
        </View>

        <Pressable accessibilityRole="button" className="active:opacity-90">
          <View
            className="flex-row items-center justify-center gap-2 rounded-[16px] bg-brand px-5 py-4"
            style={{
              shadowColor: '#7527F5',
              shadowOpacity: 0.28,
              shadowRadius: 18,
              shadowOffset: { width: 0, height: 10 },
              elevation: 8,
            }}>
            <Icon name="share-2" size={18} className="text-brand-foreground" />
            <Text className="font-rubik-semibold text-base text-brand-foreground">
              Share list with Dr. Kapoor ›
            </Text>
          </View>
        </Pressable>

        <Pressable accessibilityRole="button" className="active:opacity-80">
          <View className="flex-row items-center justify-center gap-2 rounded-[16px] border border-border bg-card px-5 py-4">
            <Icon name="plus" size={20} className="text-brand" />
            <Text className="font-rubik-semibold text-base text-brand">Add a question</Text>
          </View>
        </Pressable>
      </View>
      ) : (
        <View className="flex-1 items-center justify-center px-8 pb-20">
          <Icon name="calendar" size={28} className="text-muted-foreground" />

          <Text className="mt-5 text-center font-serif text-[24px] leading-8 text-foreground">
            No appointment added
          </Text>

          <Text className="mt-3 text-center font-rubik text-sm leading-relaxed text-muted-foreground">
            You can still collect questions now and add the visit details later.
          </Text>

          <Pressable
            onPress={() => setHasAppointment(true)}
            accessibilityRole="button"
            className="mt-14 w-full active:opacity-90">
            <View
              className="flex-row items-center justify-center gap-2 rounded-[16px] bg-brand px-5 py-4"
              style={{
                shadowColor: '#7527F5',
                shadowOpacity: 0.28,
                shadowRadius: 18,
                shadowOffset: { width: 0, height: 10 },
                elevation: 8,
              }}>
              <Icon name="plus" size={20} className="text-brand-foreground" />
              <Text className="font-rubik-semibold text-base text-brand-foreground">
                Add appointment
              </Text>
            </View>
          </Pressable>

          <Pressable
            onPress={() => setHasAppointment(true)}
            accessibilityRole="button"
            className="mt-3 w-full active:opacity-80">
            <View className="items-center rounded-[16px] border border-border bg-card px-5 py-4">
              <Text className="font-rubik-semibold text-base text-brand">
                Start a question list
              </Text>
            </View>
          </Pressable>

          <Text className="mt-3 text-center font-rubik text-xs leading-relaxed text-muted-foreground">
            Aira never sends anything to a clinician without your review.
          </Text>
        </View>
      )}
    </SafeAreaView>
  );
}
