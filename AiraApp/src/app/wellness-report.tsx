import { LinearGradient } from 'expo-linear-gradient';
import { router } from 'expo-router';
import { Pressable, ScrollView, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { Icon } from '@/components/icon';

const DAYS = [
  { label: 'M', height: 18, active: false },
  { label: 'T', height: 34, active: false },
  { label: 'W', height: 26, active: false },
  { label: 'T', height: 38, active: false },
  { label: 'F', height: 36, active: false },
  { label: 'S', height: 36, active: false },
  { label: 'S', height: 42, active: true },
] as const;

const SUMMARY = [
  { icon: 'droplet', color: 'text-[#45BCEB]', label: 'Water goal met', value: '5 / 7 days' },
  { icon: 'activity', color: 'text-[#F08B2F]', label: 'Vitamin taken', value: '7 / 7 days' },
  { icon: 'smile', color: 'text-[#E0A900]', label: 'Mood check-ins', value: '6 / 7 days' },
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
        <Text className="font-serif text-2xl text-foreground">Weekly report</Text>
      </View>
    </View>
  );
}

export default function WellnessReportScreen() {
  return (
    <SafeAreaView className="flex-1 bg-background" edges={['top', 'bottom']}>
      <Header />

      <ScrollView
        contentContainerClassName="gap-4 px-5 pb-8 pt-0"
        showsVerticalScrollIndicator={false}>
        <View className="overflow-hidden rounded-b-[22px] bg-secondary px-5 pb-5 pt-0">
          <Text className="font-serif text-[40px] leading-[48px] text-foreground">86%</Text>
          <Text className="mt-1 font-rubik text-sm leading-relaxed text-foreground">
            Up from 71% last week — steady water and vitamin logging carried it.
          </Text>
        </View>

        <View className="mt-1 px-1">
          <View className="flex-row items-end justify-between gap-2">
            {DAYS.map((day, index) => (
              <View key={`${day.label}-${index}`} className="flex-1 items-center gap-2">
                <View
                  className={
                    day.active
                      ? 'w-full rounded-t-[4px] bg-brand'
                      : 'w-full rounded-t-[4px] bg-secondary'
                  }
                  style={{ height: day.height }}
                />
                <Text
                  className={
                    day.active
                      ? 'font-rubik-medium text-xs text-brand'
                      : 'font-rubik text-xs text-muted-foreground'
                  }>
                  {day.label}
                </Text>
              </View>
            ))}
          </View>
        </View>

        <View className="rounded-[20px] border border-border bg-card px-5 py-3.5">
          {SUMMARY.map((item, index) => (
            <View key={item.label}>
              <View className="flex-row items-center gap-3 py-2">
                <Icon name={item.icon} size={18} className={item.color} />
                <Text className="flex-1 font-rubik text-base text-foreground">
                  {item.label}
                </Text>
                <Text className="font-rubik-semibold text-base text-foreground">
                  {item.value}
                </Text>
              </View>
              {index < SUMMARY.length - 1 && <View className="h-px bg-border" />}
            </View>
          ))}
        </View>

        <View className="rounded-[20px] border border-border bg-secondary px-5 py-5">
          <Text className="font-rubik-semibold text-xs uppercase tracking-widest text-brand">
            Aira noticed
          </Text>
          <Text className="mt-3 font-rubik text-base leading-snug text-foreground">
            Your best days follow a morning water reminder. Want to move vitamin time to
            match?
          </Text>
        </View>

        <View className="flex-row items-start gap-3 rounded-lg bg-secondary px-4 py-3.5">
          <Icon name="info" size={20} className="mt-0.5 text-tile-foreground" />
          <Text className="flex-1 font-rubik text-xs leading-relaxed text-tile-foreground">
            This is a wellness summary from 6–7 check-ins, not a medical assessment.
            Trends may change as you add more data.
          </Text>
        </View>

        <Pressable
          onPress={() => router.push('/share-week')}
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
              Share this week ›
            </Text>
          </LinearGradient>
        </Pressable>
      </ScrollView>
    </SafeAreaView>
  );
}
