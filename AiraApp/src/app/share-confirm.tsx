import { LinearGradient } from 'expo-linear-gradient';
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
        <Text className="font-rubik-semibold text-lg text-foreground">Confirm sharing</Text>
      </View>
    </View>
  );
}

export default function ShareConfirmScreen() {
  return (
    <SafeAreaView className="flex-1 bg-background" edges={['top', 'bottom']}>
      <Header />

      <View className="flex-1 gap-4 px-5 pt-5">
        <View className="rounded-[20px] border border-border bg-secondary px-5 py-5">
          <Text className="font-rubik-semibold text-xs uppercase tracking-widest text-brand">
            Sharing
          </Text>
          <Text className="mt-2 font-rubik-semibold text-lg text-foreground">
            Week 24 summary
          </Text>
          <Text className="mt-1 font-rubik text-sm leading-relaxed text-muted-foreground">
            Includes journey week and care consistency · excludes mood, chat, and vault
          </Text>
        </View>

        <View className="flex-row items-center gap-3 rounded-[20px] border border-border bg-card px-5 py-5">
          <View className="flex-1">
            <Text className="font-rubik-semibold text-lg text-foreground">
              Partner · Aarav
            </Text>
            <Text className="font-rubik text-xs text-muted-foreground">
              WhatsApp contact ending 4821
            </Text>
          </View>
          <Icon name="check" size={22} className="text-foreground" />
        </View>

        <View className="flex-row items-start gap-3 rounded-lg bg-secondary px-4 py-3.5">
          <Icon name="info" size={20} className="mt-0.5 text-tile-foreground" />
          <Text className="flex-1 font-rubik text-xs leading-relaxed text-tile-foreground">
            After sharing, the recipient can save or forward the image. Aira cannot
            revoke an image already sent.
          </Text>
        </View>

        <Pressable accessibilityRole="button" className="overflow-hidden rounded-[16px] active:opacity-90">
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
              shadowColor: '#7527F5',
              shadowOpacity: 0.28,
              shadowRadius: 18,
              shadowOffset: { width: 0, height: 10 },
              elevation: 8,
            }}>
            <Icon name="share-2" size={18} className="text-brand-foreground" />
            <Text className="font-rubik-semibold text-base text-brand-foreground">
              Share with Aarav
            </Text>
          </LinearGradient>
        </Pressable>

        <Pressable
          onPress={() => router.back()}
          accessibilityRole="button"
          className="active:opacity-80">
          <View className="items-center rounded-[16px] border border-border bg-card px-5 py-4">
            <Text className="font-rubik-semibold text-base text-brand">
              Choose someone else
            </Text>
          </View>
        </Pressable>
      </View>
    </SafeAreaView>
  );
}
