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
        <Text className="font-rubik-semibold text-lg text-foreground">Notifications</Text>
      </View>
    </View>
  );
}

export default function NotificationsScreen() {
  return (
    <SafeAreaView className="flex-1 bg-background" edges={['top', 'bottom']}>
      <Header />

      <View className="flex-1 items-center justify-center px-8 pb-20">
        <Icon name="bell" size={28} className="text-muted-foreground" />

        <Text className="mt-5 text-center font-serif text-[24px] leading-8 text-foreground">
          Notifications are off
        </Text>

        <Text className="mt-3 text-center font-rubik text-sm leading-relaxed text-muted-foreground">
          Your reminders still appear inside Aira. To see them outside the app, allow
          notifications in Android Settings.
        </Text>

        <Pressable accessibilityRole="button" className="mt-14 w-full active:opacity-90">
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
              Open Android Settings
            </Text>
          </View>
        </Pressable>

        <Pressable
          onPress={() => router.back()}
          accessibilityRole="button"
          className="mt-3 w-full active:opacity-80">
          <View className="items-center rounded-[16px] border border-border bg-card px-5 py-4">
            <Text className="font-rubik-semibold text-base text-brand">
              Keep reminders in Aira only
            </Text>
          </View>
        </Pressable>

        <Text className="mt-3 text-center font-rubik text-xs leading-relaxed text-muted-foreground">
          Aira will not ask again unless you choose to enable them.
        </Text>
      </View>
    </SafeAreaView>
  );
}
