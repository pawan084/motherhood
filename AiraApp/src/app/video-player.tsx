import { router } from 'expo-router';
import { Pressable, ScrollView, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { Icon } from '@/components/icon';

const NEXT = [
  { title: 'Packing your hospital bag', rating: '4.8 (5)', duration: '2:10' },
  { title: 'Birth plan basics', rating: '4.2 (9)', duration: '1:40' },
  { title: 'Sleep support now', rating: '4.2 (4)', duration: '1:00' },
] as const;

function NextVideo({
  title,
  rating,
  duration,
}: {
  title: string;
  rating: string;
  duration: string;
}) {
  return (
    <Pressable accessibilityRole="button" className="w-40 active:opacity-80">
      <View className="h-[96px] justify-center rounded-[12px] bg-[#71577A]">
        <View className="self-center rounded-full bg-black/20 p-1">
          <View className="h-7 w-7 items-center justify-center rounded-full border border-white">
            <Icon name="play" size={13} className="text-white" />
          </View>
        </View>
        <View className="absolute bottom-2 right-2 rounded bg-black px-1.5 py-0.5">
          <Text className="font-rubik-medium text-[10px] text-white">{duration}</Text>
        </View>
      </View>
      <Text className="mt-2 font-rubik-semibold text-sm leading-tight text-white">
        {title}
      </Text>
      <View className="mt-1 flex-row items-center gap-1">
        <Icon name="star" size={13} className="text-[#A99AAF]" />
        <Text className="font-rubik text-xs text-[#A99AAF]">{rating}</Text>
      </View>
    </Pressable>
  );
}

export default function VideoPlayerScreen() {
  return (
    <SafeAreaView className="flex-1 bg-[#100813]" edges={['top', 'bottom']}>
      <View className="bg-[#140A16] px-5 pb-4 pt-3">
        <View className="flex-row items-center gap-3">
          <Pressable
            onPress={() => router.back()}
            accessibilityRole="button"
            accessibilityLabel="Go back"
            className="active:opacity-80">
            <View className="h-12 w-12 items-center justify-center rounded-full bg-white">
              <Icon name="arrow-left" size={23} className="text-[#140A16]" />
            </View>
          </Pressable>
          <Text className="flex-1 font-rubik-semibold text-base text-white">
            Five things to have ready
          </Text>
        </View>
      </View>

      <ScrollView contentContainerClassName="pb-8" showsVerticalScrollIndicator={false}>
        <View className="min-h-[288px] bg-[#211725] px-5 py-4">
          <View className="self-start rounded-full bg-[#0F0712] px-3 py-1">
            <Text className="font-rubik-black text-xs uppercase text-white">CC</Text>
          </View>

          <View className="mt-3 self-start rounded-lg bg-[#140A16] px-4 py-2">
            <Text className="font-rubik-semibold text-xs text-[#FFE18A]">
              💡 Great for late-night,
            </Text>
            <Text className="font-rubik-semibold text-xs text-[#FFE18A]">
              sound-off viewing
            </Text>
          </View>

          <View className="flex-1 items-center justify-center">
            <Pressable accessibilityRole="button" accessibilityLabel="Play video" className="active:opacity-80">
              <View className="h-14 w-14 items-center justify-center rounded-full bg-white/15">
                <Icon name="play" size={28} className="ml-1 text-white" />
              </View>
            </Pressable>
          </View>

          <Text className="text-center font-rubik-semibold text-sm text-white">
            Have your hospital bag ready by week 32.
          </Text>
          <View className="mt-2 h-1 rounded-full bg-white/25">
            <View className="h-1 w-[35%] rounded-full bg-white" />
          </View>
        </View>

        <View className="bg-[#1B0D1E] px-5 py-4">
          <View className="flex-row items-center justify-between">
            <View className="flex-row items-center gap-4">
              <Icon name="pause" size={20} className="text-white" />
              <Text className="font-rubik text-sm text-white">0:35 / 1:40</Text>
            </View>
            <View className="flex-row items-center gap-4">
              <Text className="font-rubik-semibold text-base text-white">1×</Text>
              <View className="rounded border border-white px-1">
                <Text className="font-rubik-black text-xs text-white">CC</Text>
              </View>
              <Icon name="maximize" size={20} className="text-white" />
            </View>
          </View>
        </View>

        <View className="bg-[#170B1A] px-5 py-4">
          <View className="flex-row items-center justify-between">
            <View className="flex-row items-center gap-3">
              <Icon name="heart" size={24} className="text-[#FF6F9B]" />
              <Text className="font-rubik-semibold text-sm text-white">3</Text>
            </View>
            <Text className="font-rubik-semibold text-base text-[#F6C84B]">★ ★ ★ ★ ☆</Text>
            <Text className="font-rubik text-sm text-white">★ 4.0 (3)</Text>
          </View>

          <Pressable accessibilityRole="button" className="mt-5 items-center py-2 active:opacity-70">
            <Text className="font-rubik-semibold text-sm text-white">Show transcript</Text>
          </Pressable>
        </View>

        <View className="h-px bg-white/10" />

        <View className="bg-[#170B1A] px-5 py-4">
          <Text className="font-rubik-semibold text-xs uppercase tracking-widest text-[#C59AD8]">
            Up next
          </Text>
          <ScrollView
            horizontal
            showsHorizontalScrollIndicator={false}
            contentContainerClassName="mt-3 gap-3">
            {NEXT.map((video) => (
              <NextVideo key={video.title} {...video} />
            ))}
          </ScrollView>

          <Pressable accessibilityRole="button" className="mt-5 active:opacity-90">
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
                Play next video
              </Text>
            </View>
          </Pressable>
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}
