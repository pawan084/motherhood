import { router } from 'expo-router';
import { useState } from 'react';
import { Pressable, Text, TextInput, View } from 'react-native';
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
        <Text className="font-serif text-2xl text-foreground">Delete your account</Text>
      </View>
    </View>
  );
}

export default function DeleteAccountScreen() {
  const [confirmation, setConfirmation] = useState('');
  const canDelete = confirmation.trim() === 'DELETE';

  return (
    <SafeAreaView className="flex-1 bg-background" edges={['top', 'bottom']}>
      <Header />

      <View className="flex-1 gap-4 px-5 pb-8 pt-5">
        <View className="rounded-[20px] border border-destructive bg-[#FFF0F1] px-5 py-5">
          <Text className="font-rubik-semibold text-base text-[#7A2024]">
            This can&apos;t be undone
          </Text>
          <Text className="mt-3 font-rubik text-sm leading-relaxed text-[#8C2E33]">
            Your journey, chat history, care vault and moods are permanently deleted
            within 30 days. Nothing is kept for marketing.
          </Text>
        </View>

        <View className="rounded-[20px] border border-border bg-card px-5 py-5">
          <Text className="font-rubik text-sm text-muted-foreground">Want a copy first?</Text>
          <Pressable accessibilityRole="button" className="mt-3 active:opacity-80">
            <View className="flex-row items-center justify-center gap-2 rounded-[14px] border border-border bg-card px-5 py-4">
              <Icon name="download" size={18} className="text-brand" />
              <Text className="font-rubik-semibold text-base text-brand">
                Export my data (.zip)
              </Text>
            </View>
          </Pressable>
        </View>

        <View className="rounded-[20px] border border-border bg-card px-5 py-5">
          <Text className="font-rubik text-sm text-muted-foreground">
            Type DELETE to confirm
          </Text>
          <View className="mt-3 rounded-[14px] border border-border bg-card px-4 py-3">
            <TextInput
              value={confirmation}
              onChangeText={setConfirmation}
              autoCapitalize="characters"
              placeholder="Type here"
              placeholderTextColor="#8A8295"
              className="min-h-[28px] font-rubik text-base text-foreground outline-none"
            />
          </View>
        </View>

        <View className="flex-row items-start gap-3 rounded-lg bg-secondary px-4 py-3.5">
          <Icon name="info" size={20} className="mt-0.5 text-tile-foreground" />
          <Text className="flex-1 font-rubik text-xs leading-relaxed text-tile-foreground">
            For your protection, Aira will ask you to verify your email again before
            deletion begins. You can cancel during the 30-day deletion window.
          </Text>
        </View>

        <Pressable
          disabled={!canDelete}
          accessibilityRole="button"
          accessibilityState={{ disabled: !canDelete }}
          className="active:opacity-90">
          <View
            className={
              canDelete
                ? 'flex-row items-center justify-center gap-2 rounded-[16px] bg-destructive px-5 py-4'
                : 'flex-row items-center justify-center gap-2 rounded-[16px] bg-muted px-5 py-4'
            }>
            <Icon
              name="trash-2"
              size={18}
              className={canDelete ? 'text-white' : 'text-muted-foreground'}
            />
            <Text
              className={
                canDelete
                  ? 'font-rubik-semibold text-base text-white'
                  : 'font-rubik-semibold text-base text-muted-foreground'
              }>
              Permanently delete my account
            </Text>
          </View>
        </Pressable>

        <Pressable
          onPress={() => router.back()}
          accessibilityRole="button"
          className="active:opacity-80">
          <View className="items-center rounded-[16px] border border-border bg-card px-5 py-4">
            <Text className="font-rubik-semibold text-base text-brand">Cancel</Text>
          </View>
        </Pressable>
      </View>
    </SafeAreaView>
  );
}
