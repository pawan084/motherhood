import { router } from 'expo-router';
import { Pressable, ScrollView, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { Icon } from '@/components/icon';

const WEEK = [
  { day: 'W', icon: 'smile', color: 'text-brand', selected: false },
  { day: 'T', icon: 'frown', color: 'text-[#5273B8]', selected: false },
  { day: 'F', icon: null, color: '', selected: false },
  { day: 'S', icon: null, color: '', selected: false },
  { day: 'S', icon: null, color: '', selected: false },
  { day: 'M', icon: 'frown', color: 'text-tile-foreground', selected: false },
  { day: 'T', icon: 'smile', color: 'text-[#39B876]', selected: true },
] as const;

const MONTH = [
  { label: 'Great', count: 6, color: 'bg-[#74C797]', iconColor: 'text-[#39B876]', width: '67%' },
  { label: 'Okay', count: 4, color: 'bg-brand', iconColor: 'text-brand', width: '47%' },
  { label: 'Tired', count: 2, color: 'bg-[#8A9BCB]', iconColor: 'text-[#5273B8]', width: '28%' },
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
        <Text className="font-serif text-2xl text-foreground">Your moods</Text>
      </View>
    </View>
  );
}

export default function MoodsScreen() {
  return (
    <SafeAreaView className="flex-1 bg-background" edges={['top', 'bottom']}>
      <Header />

      <ScrollView
        contentContainerClassName="gap-4 px-5 pb-8 pt-5"
        showsVerticalScrollIndicator={false}>
        <Text className="font-rubik-semibold text-xs uppercase tracking-widest text-brand">
          This week
        </Text>

        <View className="rounded-[20px] border border-border bg-card px-5 py-5">
          <View className="flex-row items-center justify-between">
            {WEEK.map((item, index) => (
              <View key={`${item.day}-${index}`} className="items-center gap-2">
                <Text
                  className={
                    item.selected
                      ? 'font-rubik-medium text-xs text-brand'
                      : 'font-rubik text-xs text-muted-foreground'
                  }>
                  {item.day}
                </Text>
                <View
                  className={
                    item.selected
                      ? 'h-8 w-8 items-center justify-center rounded-full border-2 border-brand bg-card'
                      : 'h-8 w-8 items-center justify-center rounded-full border border-border bg-card'
                  }>
                  {item.icon && (
                    <Icon name={item.icon} size={17} className={item.color} />
                  )}
                </View>
              </View>
            ))}
          </View>
        </View>

        <Text className="mt-1 font-rubik-semibold text-xs uppercase tracking-widest text-brand">
          This month
        </Text>

        <View className="rounded-[20px] border border-border bg-card px-5 py-5">
          <View className="gap-3">
            {MONTH.map((m) => (
              <View key={m.label} className="flex-row items-center gap-3">
                <Icon name="smile" size={17} className={m.iconColor} />
                <Text className="w-16 font-rubik-semibold text-sm text-foreground">
                  {m.label}
                </Text>
                <View className="h-3 flex-1 rounded-full bg-transparent">
                  <View className={`h-3 rounded-full ${m.color}`} style={{ width: m.width }} />
                </View>
                <Text className="w-5 text-right font-rubik-semibold text-base text-foreground">
                  {m.count}
                </Text>
              </View>
            ))}
          </View>

          <Text className="mt-3 font-rubik text-xs text-muted-foreground">
            12 check-ins in the last 30 days
          </Text>
        </View>

        <View className="flex-row items-start gap-3 rounded-lg bg-secondary px-4 py-3.5">
          <Icon name="info" size={20} className="mt-0.5 text-tile-foreground" />
          <Text className="flex-1 font-rubik text-xs leading-relaxed text-tile-foreground">
            Summary: 6 great, 4 okay and 2 tired check-ins. Empty days mean “not
            logged,” not a low mood.
          </Text>
        </View>

        <Pressable accessibilityRole="button" className="active:opacity-90">
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
              Log today&apos;s mood
            </Text>
          </View>
        </Pressable>
      </ScrollView>
    </SafeAreaView>
  );
}
