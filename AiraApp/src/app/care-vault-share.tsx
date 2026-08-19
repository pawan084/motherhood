import { router } from 'expo-router';
import { useState } from 'react';
import { Pressable, Text, TextInput, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { Icon } from '@/components/icon';

const EXPIRY = [
  { key: '24h', label: '24 hours' },
  { key: '7d', label: '7 days' },
  { key: '30d', label: '30 days' },
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
        <Text className="font-rubik-semibold text-lg text-foreground">Share document</Text>
      </View>
    </View>
  );
}

export default function CareVaultShareScreen() {
  const [recipient, setRecipient] = useState('');
  const [expires, setExpires] = useState<(typeof EXPIRY)[number]['key']>('24h');

  return (
    <SafeAreaView className="flex-1 bg-background" edges={['top', 'bottom']}>
      <Header />

      <View className="flex-1 gap-4 px-5 pt-5">
        <View className="flex-row items-center gap-3 rounded-[20px] border border-border bg-card px-5 py-5">
          <Icon name="file-text" size={24} className="text-foreground" />
          <View className="flex-1">
            <Text className="font-rubik-semibold text-lg text-foreground">
              Anomaly scan report
            </Text>
            <Text className="font-rubik text-xs text-muted-foreground">PDF · 2.1 MB</Text>
          </View>
        </View>

        <View className="rounded-[16px] border border-border bg-card px-4 py-3">
          <Text className="font-rubik-medium text-xs text-muted-foreground">Recipient</Text>
          <TextInput
            value={recipient}
            onChangeText={setRecipient}
            placeholder="Name or email"
            placeholderTextColor="#8A8295"
            autoCapitalize="none"
            autoComplete="email"
            keyboardType="email-address"
            className="mt-1 min-h-[28px] font-rubik text-base text-foreground outline-none"
          />
        </View>

        <View>
          <Text className="font-rubik-semibold text-xs uppercase tracking-widest text-brand">
            Link expires
          </Text>
          <View className="mt-3 flex-row rounded-[14px] bg-secondary p-1">
            {EXPIRY.map((option) => {
              const selected = expires === option.key;
              return (
                <Pressable
                  key={option.key}
                  onPress={() => setExpires(option.key)}
                  accessibilityRole="radio"
                  accessibilityState={{ selected }}
                  className="flex-1 active:opacity-80">
                  <View
                    className={
                      selected
                        ? 'items-center rounded-[12px] bg-card py-3'
                        : 'items-center rounded-[12px] py-3'
                    }>
                    <Text
                      className={
                        selected
                          ? 'font-rubik-semibold text-sm text-brand'
                          : 'font-rubik-semibold text-sm text-foreground'
                      }>
                      {option.label}
                    </Text>
                  </View>
                </Pressable>
              );
            })}
          </View>
        </View>

        <View className="flex-row items-start gap-3 rounded-lg bg-secondary px-4 py-3.5">
          <Icon name="info" size={20} className="mt-0.5 text-tile-foreground" />
          <Text className="flex-1 font-rubik text-xs leading-relaxed text-tile-foreground">
            You can revoke this link anytime. The recipient may still save a downloaded
            copy.
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
            <Icon name="share-2" size={18} className="text-brand-foreground" />
            <Text className="font-rubik-semibold text-base text-brand-foreground">
              Review share link
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
