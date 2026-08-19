import { LinearGradient } from 'expo-linear-gradient';
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
        <Text className="font-serif text-2xl text-foreground">Share your week</Text>
      </View>
    </View>
  );
}

function Toggle({
  on,
  onPress,
}: {
  on: boolean;
  onPress: () => void;
}) {
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

export default function ShareWeekScreen() {
  const [journeyWeek, setJourneyWeek] = useState(true);
  const [careConsistency, setCareConsistency] = useState(true);
  const [moodCheckins, setMoodCheckins] = useState(false);

  return (
    <SafeAreaView className="flex-1 bg-background" edges={['top', 'bottom']}>
      <Header />

      <ScrollView
        contentContainerClassName="gap-4 px-5 pb-8 pt-5"
        showsVerticalScrollIndicator={false}>
        <View className="overflow-hidden rounded-[22px] bg-secondary px-5 py-8">
          <Text className="text-center font-rubik text-xs uppercase tracking-widest text-muted-foreground">
            Week 24 · Pawan&apos;s journey
          </Text>

          <Text className="mt-3 text-center font-serif text-[32px] leading-10 text-foreground">
            86% consistency
          </Text>

          <Text className="mt-2 text-center font-rubik text-base leading-relaxed text-foreground">
            Baby is about the size of an ear of corn
          </Text>

          <Text className="mt-0 text-center text-xl" accessibilityElementsHidden importantForAccessibility="no">
            🌽
          </Text>

          <View className="mt-4 flex-row justify-center gap-8">
            {journeyWeek && (
              <View className="items-center">
                <Text className="font-rubik-semibold text-lg text-foreground">16</Text>
                <Text className="font-rubik text-xs text-muted-foreground">weeks to go</Text>
              </View>
            )}

            {careConsistency && (
              <View className="items-center">
                <Text className="font-rubik-semibold text-lg text-foreground">5/7</Text>
                <Text className="font-rubik text-xs text-muted-foreground">water goal</Text>
              </View>
            )}

            {moodCheckins && (
              <View className="items-center">
                <Text className="font-rubik-semibold text-lg text-foreground">6/7</Text>
                <Text className="font-rubik text-xs text-muted-foreground">mood check-ins</Text>
              </View>
            )}
          </View>

          <Text className="mt-5 text-center font-rubik text-xs text-muted-foreground">
            Made with Aira
          </Text>
        </View>

        <View className="rounded-[20px] border border-border bg-card px-5 py-4">
          <Text className="font-rubik-semibold text-xs uppercase tracking-widest text-brand">
            Included in this image
          </Text>

          <View className="mt-3 gap-3">
            <View className="flex-row items-center justify-between gap-4">
              <Text className="font-rubik text-sm text-foreground">Journey week</Text>
              <Toggle on={journeyWeek} onPress={() => setJourneyWeek((v) => !v)} />
            </View>
            <View className="flex-row items-center justify-between gap-4">
              <Text className="font-rubik text-sm text-foreground">Care consistency</Text>
              <Toggle on={careConsistency} onPress={() => setCareConsistency((v) => !v)} />
            </View>
            <View className="flex-row items-center justify-between gap-4">
              <Text className="font-rubik text-sm text-foreground">Mood check-ins</Text>
              <Toggle on={moodCheckins} onPress={() => setMoodCheckins((v) => !v)} />
            </View>
          </View>
        </View>

        <Text className="px-3 text-center font-rubik text-xs leading-relaxed text-muted-foreground">
          Preview exactly what will be shared. Chat and vault stay private, but recipients
          can save or forward the image.
        </Text>

        <Pressable
          accessibilityRole="button"
          className="overflow-hidden rounded-[16px] active:opacity-90"
          style={{
            shadowColor: '#7527F5',
            shadowOpacity: 0.28,
            shadowRadius: 18,
            shadowOffset: { width: 0, height: 10 },
            elevation: 8,
          }}>
          <LinearGradient
            colors={['#9333FF', '#6D17EA']}
            start={{ x: 0, y: 0 }}
            end={{ x: 1, y: 1 }}
            style={{
              minHeight: 52,
              borderRadius: 16,
              flexDirection: 'row',
              alignItems: 'center',
              justifyContent: 'center',
              gap: 8,
              paddingHorizontal: 20,
              paddingVertical: 14,
            }}>
            <Icon name="share-2" size={18} className="text-brand-foreground" />
            <Text className="font-rubik-semibold text-base text-brand-foreground">
              Share with partner ›
            </Text>
          </LinearGradient>
        </Pressable>

        <Pressable accessibilityRole="button" className="active:opacity-80">
          <View className="flex-row items-center justify-center gap-2 rounded-[16px] border border-border bg-card px-5 py-4">
            <Icon name="download" size={18} className="text-brand" />
            <Text className="font-rubik-semibold text-base text-brand">Save image</Text>
          </View>
        </Pressable>
      </ScrollView>
    </SafeAreaView>
  );
}
