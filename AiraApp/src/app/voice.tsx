import { router } from 'expo-router';
import { Pressable, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { BrandOrb } from '@/components/brand-orb';
import { Icon } from '@/components/icon';
import { SCircle, Svg } from '@/components/svg';
import { BottomNav } from '@/components/today/parts';

function ListeningRing() {
  const size = 118;
  const stroke = 6;
  const r = (size - stroke) / 2;
  const c = 2 * Math.PI * r;

  return (
    <View className="items-center justify-center" style={{ width: size, height: size }}>
      <Svg width={size} height={size} style={{ position: 'absolute', transform: [{ rotate: '-90deg' }] }}>
        <SCircle
          cx={size / 2}
          cy={size / 2}
          r={r}
          strokeWidth={stroke}
          className="fill-secondary stroke-muted"
        />
        <SCircle
          cx={size / 2}
          cy={size / 2}
          r={r}
          strokeWidth={stroke}
          strokeLinecap="round"
          strokeDasharray={`${c * 0.78} ${c}`}
          strokeDashoffset={8}
          className="fill-transparent stroke-brand"
        />
      </Svg>
      <Icon name="mic" size={28} className="text-foreground" />
    </View>
  );
}

export default function VoiceScreen() {
  return (
    <SafeAreaView className="flex-1 bg-background" edges={['top']}>
      <View className="border-b border-border bg-card px-5 pb-4 pt-2">
        <View className="flex-row items-center justify-between gap-3">
          <View className="flex-row items-center gap-3">
            <BrandOrb size={36} />
            <View>
              <Text className="font-rubik-semibold text-xl text-foreground">Aira</Text>
              <View className="flex-row items-center gap-1">
                <Text className="font-rubik text-xs text-muted-foreground">Listening...</Text>
                <View className="h-1.5 w-1.5 rounded-full bg-ok" />
                <Text className="font-rubik text-xs text-muted-foreground">Up to date</Text>
              </View>
            </View>
          </View>

          <Pressable
            onPress={() => router.push('/urgent')}
            accessibilityRole="button"
            accessibilityLabel="Crisis help"
            className="active:opacity-70">
            <View className="h-11 w-11 items-center justify-center rounded-full bg-tile">
              <Icon name="shield" size={19} className="text-destructive" />
            </View>
          </Pressable>
        </View>
      </View>

      <View className="flex-1 items-center px-5 pb-40 pt-12">
        <ListeningRing />

        <Text className="mt-9 text-center font-serif text-[26px] leading-8 text-foreground">
          &quot;...tired again this afternoon&quot;
        </Text>
        <Text className="mt-3 max-w-[260px] text-center font-rubik text-sm leading-snug text-muted-foreground">
          Speak naturally — tap the waveform anytime to switch back to typing.
        </Text>

        <View className="mt-4 flex-row items-start gap-3 rounded-lg bg-secondary px-4 py-3.5">
          <Icon name="lock" size={21} className="mt-0.5 text-tile-foreground" />
          <Text className="flex-1 font-rubik text-xs leading-relaxed text-tile-foreground">
            Your audio is used to create this message, then deleted. Review the transcript
            before it is sent.
          </Text>
        </View>

        <Pressable
          onPress={() => router.push('/chat')}
          accessibilityRole="button"
          accessibilityLabel="Switch back to typing"
          className="mt-7 flex-row items-end gap-2 active:opacity-80">
          {[24, 36, 18, 42, 28].map((height, index) => (
            <View key={index} className="w-1 rounded-full bg-brand" style={{ height }} />
          ))}
        </Pressable>

        <Text className="mt-auto text-center font-rubik text-xs text-muted-foreground">
          Aira offers general support, not medical advice.
        </Text>
      </View>

      <View className="absolute inset-x-0 bottom-[86px] px-5">
        <View className="flex-row items-center gap-3">
          <Pressable
            onPress={() => router.push('/chat')}
            accessibilityRole="button"
            className="min-w-0 flex-1 active:opacity-80">
            <View className="flex-row items-center justify-center gap-2 rounded-[16px] border border-border bg-card px-5 py-4">
              <Text className="font-rubik-semibold text-base text-brand">
                ⌨ Switch to typing
              </Text>
            </View>
          </Pressable>

          <Pressable accessibilityRole="button" accessibilityLabel="Stop listening" className="active:opacity-90">
            <View className="h-[54px] w-[54px] items-center justify-center rounded-full bg-brand">
              <View className="h-3 w-3 rounded-[2px] bg-brand-foreground" />
            </View>
          </Pressable>
        </View>
      </View>

      <BottomNav active="chat" />
    </SafeAreaView>
  );
}
