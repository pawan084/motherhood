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
        <Text className="font-rubik-semibold text-lg text-foreground">Review your message</Text>
      </View>
    </View>
  );
}

export default function VoiceReviewScreen() {
  return (
    <SafeAreaView className="flex-1 bg-background" edges={['top', 'bottom']}>
      <Header />

      <View className="flex-1 gap-4 px-5 pt-5">
        <View className="flex-row items-start gap-3 rounded-lg bg-secondary px-4 py-3.5">
          <Icon name="lock" size={20} className="mt-0.5 text-tile-foreground" />
          <Text className="flex-1 font-rubik text-xs leading-relaxed text-tile-foreground">
            Audio processing is complete. The recording is queued for deletion.
          </Text>
        </View>

        <View className="rounded-[16px] border border-border bg-card px-4 py-3">
          <Text className="font-rubik-medium text-xs text-muted-foreground">Transcript</Text>
          <Text className="mt-2 font-rubik text-base leading-snug text-foreground">
            I&apos;ve been feeling tired again this afternoon
          </Text>
        </View>

        <View className="rounded-[20px] border border-border bg-secondary px-5 py-4">
          <View className="flex-row items-center justify-between gap-4 py-1">
            <Text className="font-rubik text-lg text-foreground">Language</Text>
            <Text className="font-rubik-semibold text-lg text-foreground">English</Text>
          </View>
          <View className="my-2 h-px bg-border" />
          <View className="flex-row items-center justify-between gap-4 py-1">
            <Text className="font-rubik text-lg text-foreground">Confidence</Text>
            <Text className="font-rubik-semibold text-lg text-foreground">High</Text>
          </View>
        </View>

        <Pressable
          onPress={() => router.replace('/chat')}
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
              Send to Aira
            </Text>
          </View>
        </Pressable>

        <Pressable
          onPress={() => router.replace('/voice')}
          accessibilityRole="button"
          className="active:opacity-80">
          <View className="items-center rounded-[16px] border border-border bg-card px-5 py-4">
            <Text className="font-rubik-semibold text-base text-brand">Record again</Text>
          </View>
        </Pressable>

        <Pressable
          onPress={() => router.replace('/chat')}
          accessibilityRole="button"
          className="mt-2 flex-row items-center justify-center gap-2 py-3 active:opacity-70">
          <Icon name="trash-2" size={18} className="text-brand" />
          <Text className="font-rubik-semibold text-sm text-brand">
            Delete without sending
          </Text>
        </Pressable>
      </View>
    </SafeAreaView>
  );
}
