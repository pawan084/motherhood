import { router } from 'expo-router';
import { Pressable, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { Icon } from '@/components/icon';

export default function UrgentScreen() {
  return (
    <SafeAreaView className="flex-1 bg-[#FFF0F1]" edges={['top', 'bottom']}>
      <View className="flex-1 px-8 pb-10 pt-16">
        <View className="h-16 w-16 items-center justify-center rounded-full bg-[#D23542]">
          <Icon name="shield" size={28} className="text-white" />
        </View>

        <Text className="mt-7 font-serif text-[32px] leading-[38px] text-[#7A2024]">
          Please contact your care team now.
        </Text>

        <Text className="mt-5 font-rubik text-base leading-relaxed text-[#8C2E33]">
          Do not wait for an AI response if you feel seriously unwell or are worried
          about your baby.
        </Text>

        <Pressable accessibilityRole="button" className="mt-12 active:opacity-90">
          <View
            className="flex-row items-center justify-center gap-2 rounded-[16px] bg-[#D23542] px-5 py-4"
            style={{
              shadowColor: '#D23542',
              shadowOpacity: 0.3,
              shadowRadius: 18,
              shadowOffset: { width: 0, height: 10 },
              elevation: 8,
            }}>
            <Icon name="phone" size={20} className="text-white" />
            <Text className="font-rubik-semibold text-base text-white">Call care team</Text>
          </View>
        </Pressable>

        <Pressable accessibilityRole="button" className="mt-3 active:opacity-80">
          <View className="flex-row items-center justify-center gap-2 rounded-[16px] border border-[#E56E76] bg-white px-5 py-4">
            <Icon name="phone" size={20} className="text-[#D23542]" />
            <Text className="font-rubik-semibold text-base text-[#D23542]">
              Open emergency profile
            </Text>
          </View>
        </Pressable>

        <Pressable
          onPress={() => router.back()}
          accessibilityRole="button"
          className="mt-7 items-center py-2 active:opacity-70">
          <Text className="font-rubik-semibold text-sm text-[#7A2024]">
            I&apos;m safe for now
          </Text>
        </Pressable>
      </View>
    </SafeAreaView>
  );
}
