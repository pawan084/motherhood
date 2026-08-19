import { router } from 'expo-router';
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
        <Text className="font-rubik-semibold text-lg text-foreground">Emergency profile</Text>
      </View>
    </View>
  );
}

export default function EmergencyProfileScreen() {
  return (
    <SafeAreaView className="flex-1 bg-background" edges={['top', 'bottom']}>
      <Header />

      <View className="flex-1 gap-4 px-5 pt-5">
        <View className="rounded-[20px] border border-destructive bg-[#FFF0F1] px-5 py-5">
          <Text className="font-rubik-semibold text-base text-destructive">
            Care-team number not added
          </Text>
          <Text className="mt-2 font-rubik text-sm leading-relaxed text-[#8C2E33]">
            Emergency services remain available. Add a verified clinician or hospital
            number when you can.
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
            <Icon name="plus" size={20} className="text-brand-foreground" />
            <Text className="font-rubik-semibold text-base text-brand-foreground">
              Add care-team contact
            </Text>
          </View>
        </Pressable>

        <Pressable accessibilityRole="button" className="active:opacity-80">
          <View className="flex-row items-center justify-center gap-2 rounded-[16px] border border-border bg-card px-5 py-4">
            <Icon name="phone" size={20} className="text-brand" />
            <Text className="font-rubik-semibold text-base text-brand">
              Call emergency services (112)
            </Text>
          </View>
        </Pressable>

        <Text className="mt-1 font-rubik-semibold text-xs uppercase tracking-widest text-brand">
          Medical details
        </Text>

        <View className="rounded-[20px] border border-border bg-card px-5 py-3">
          <View className="flex-row items-center justify-between gap-4 py-3">
            <Text className="font-rubik text-lg text-foreground">Due date</Text>
            <Text className="font-rubik-semibold text-base text-foreground">24 Nov 2026</Text>
          </View>
          <View className="h-px bg-border" />
          <View className="flex-row items-center justify-between gap-4 py-3">
            <Text className="font-rubik text-lg text-foreground">Allergies</Text>
            <Text className="font-rubik-semibold text-base text-foreground">None added</Text>
          </View>
        </View>
      </View>
    </SafeAreaView>
  );
}
