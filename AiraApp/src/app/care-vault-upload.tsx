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
        <Text className="font-rubik-semibold text-lg text-foreground">Add to Care Vault</Text>
      </View>
    </View>
  );
}

export default function CareVaultUploadScreen() {
  return (
    <SafeAreaView className="flex-1 bg-background" edges={['top', 'bottom']}>
      <Header />

      <View className="flex-1 gap-4 px-5 pt-5">
        <View className="rounded-[20px] border border-border bg-secondary px-5 py-5">
          <View className="flex-row items-center gap-3">
            <Icon name="file-text" size={24} className="text-foreground" />
            <View className="flex-1">
              <Text className="font-rubik-semibold text-lg text-foreground">
                Anomaly-scan.pdf
              </Text>
              <Text className="font-rubik text-xs text-muted-foreground">
                2.1 MB · uploading securely
              </Text>
            </View>
          </View>

          <View className="mt-4 h-1.5 rounded-full bg-muted">
            <View className="h-1.5 w-[72%] rounded-full bg-brand" />
          </View>

          <Text className="mt-2 font-rubik text-xs text-muted-foreground">
            72% · safe to leave this screen
          </Text>
        </View>

        <View className="flex-row items-start gap-3 rounded-lg bg-secondary px-4 py-3.5">
          <Icon name="info" size={20} className="mt-0.5 text-tile-foreground" />
          <Text className="flex-1 font-rubik text-xs leading-relaxed text-tile-foreground">
            Files are scanned before they enter your vault and are not used to train AI.
          </Text>
        </View>

        <Pressable
          onPress={() => router.back()}
          accessibilityRole="button"
          className="active:opacity-80">
          <View className="items-center rounded-[16px] border border-border bg-card px-5 py-4">
            <Text className="font-rubik-semibold text-base text-brand">Cancel upload</Text>
          </View>
        </Pressable>
      </View>
    </SafeAreaView>
  );
}
