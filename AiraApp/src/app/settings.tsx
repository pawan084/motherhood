import { LinearGradient } from 'expo-linear-gradient';
import { router } from 'expo-router';
import { useState } from 'react';
import { Pressable, ScrollView, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { BrandOrb } from '@/components/brand-orb';
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
        <Text className="font-serif text-2xl text-foreground">Settings</Text>
      </View>
    </View>
  );
}

function Toggle({ on, onPress }: { on: boolean; onPress: () => void }) {
  return (
    <Pressable
      onPress={onPress}
      accessibilityRole="switch"
      accessibilityState={{ checked: on }}
      className="active:opacity-80">
      <View
        className={
          on
            ? 'h-8 w-12 items-end justify-center rounded-full bg-brand px-1'
            : 'h-8 w-12 items-start justify-center rounded-full bg-muted px-1'
        }>
        <View className="h-6 w-6 rounded-full bg-card" />
      </View>
    </Pressable>
  );
}

function Section({
  title,
  children,
}: {
  title: string;
  children: React.ReactNode;
}) {
  return (
    <View>
      <Text className="mb-2 font-rubik-semibold text-xs uppercase tracking-widest text-brand">
        {title}
      </Text>
      <View className="rounded-[20px] border border-border bg-card px-5 py-4">{children}</View>
    </View>
  );
}

function Row({
  label,
  note,
  danger,
  onPress,
  children,
}: {
  label: string;
  note?: string;
  danger?: boolean;
  onPress?: () => void;
  children?: React.ReactNode;
}) {
  return (
    <Pressable onPress={onPress} accessibilityRole="button" className="active:opacity-70">
      <View className="flex-row items-center gap-3 py-3">
        <View className="flex-1">
          <Text
            className={
              danger
                ? 'font-rubik text-base text-destructive'
                : 'font-rubik text-base text-foreground'
            }>
            {label}
          </Text>
          {note && <Text className="mt-0.5 font-rubik text-xs text-muted-foreground">{note}</Text>}
        </View>
        {children ?? (
          <Icon
            name="chevron-right"
            size={22}
            className={danger ? 'text-destructive' : 'text-brand'}
          />
        )}
      </View>
    </Pressable>
  );
}

function Divider() {
  return <View className="h-px bg-border" />;
}

export default function SettingsScreen() {
  const [appearance, setAppearance] = useState<'system' | 'light' | 'dark'>('system');
  const [careReminders, setCareReminders] = useState(true);
  const [personalizedMemory, setPersonalizedMemory] = useState(true);
  const [frequency, setFrequency] = useState<'1x' | '2x' | '3x'>('2x');

  return (
    <SafeAreaView className="flex-1 bg-background" edges={['top', 'bottom']}>
      <Header />

      <ScrollView
        contentContainerClassName="gap-4 px-5 pb-8 pt-5"
        showsVerticalScrollIndicator={false}>
        <Pressable
          onPress={() => router.push('/sign-in')}
          accessibilityRole="button"
          className="active:opacity-80">
          <View className="flex-row items-center gap-3 rounded-[20px] border border-border bg-secondary px-5 py-5">
            <BrandOrb size={40} />
            <View className="flex-1">
              <Text className="font-rubik-semibold text-base text-foreground">
                Using Aira as a guest
              </Text>
              <Text className="font-rubik text-xs leading-snug text-muted-foreground">
                Sign in to keep your journey if you switch devices
              </Text>
            </View>
            <Icon name="chevron-right" size={22} className="text-brand" />
          </View>
        </Pressable>

        <Section title="Appearance">
          <Text className="font-rubik text-xs text-muted-foreground">
            Dark mode is easier on 3 AM eyes
          </Text>
          <View className="mt-3 flex-row rounded-[14px] bg-secondary p-1">
            {[
              { key: 'system', label: 'System' },
              { key: 'light', label: 'Light' },
              { key: 'dark', label: 'Dark' },
            ].map((option) => {
              const selected = appearance === option.key;
              return (
                <Pressable
                  key={option.key}
                  onPress={() => setAppearance(option.key as typeof appearance)}
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
        </Section>

        <Section title="Care reminders">
          <Pressable
            onPress={() => router.push('/notifications')}
            accessibilityRole="button"
            className="active:opacity-80">
            <View className="flex-row items-center gap-3">
            <View className="flex-1">
              <Text className="font-rubik-semibold text-base text-foreground">Care reminders</Text>
              <Text className="font-rubik text-xs text-muted-foreground">2× a day · 09:00, 20:00</Text>
            </View>
            <Toggle on={careReminders} onPress={() => setCareReminders((v) => !v)} />
            </View>
          </Pressable>

          <Text className="mt-4 font-rubik-semibold text-xs uppercase tracking-widest text-brand">
            How often
          </Text>
          <View className="mt-2 flex-row rounded-[14px] bg-secondary p-1">
            {[
              { key: '1x', label: '1× a day' },
              { key: '2x', label: '2× a day' },
              { key: '3x', label: '3× a day' },
            ].map((option) => {
              const selected = frequency === option.key;
              return (
                <Pressable
                  key={option.key}
                  onPress={() => setFrequency(option.key as typeof frequency)}
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

          <Text className="mt-4 font-rubik-semibold text-xs uppercase tracking-widest text-brand">
            At
          </Text>
          <View className="mt-2 gap-1">
            <Row label="Reminder 1">
              <View className="rounded-full border border-border bg-card px-4 py-2.5">
                <Text className="font-rubik-semibold text-sm text-brand">09:00</Text>
              </View>
            </Row>
            <Row label="Reminder 2">
              <View className="rounded-full border border-border bg-card px-4 py-2.5">
                <Text className="font-rubik-semibold text-sm text-brand">20:00</Text>
              </View>
            </Row>
          </View>
        </Section>

        <View className="rounded-[20px] border border-border bg-[#EFFBF3] px-5 py-4">
          <Text className="font-rubik-semibold text-base text-foreground">
            Never used for advertising
          </Text>
          <Text className="font-rubik text-xs text-muted-foreground">
            Sensitive health data is not an ad product
          </Text>
        </View>

        <Section title="AI & Privacy">
          <View className="flex-row items-center gap-3 py-1">
            <View className="flex-1">
              <Text className="font-rubik-semibold text-base text-foreground">
                Personalized AI memory
              </Text>
              <Text className="font-rubik text-xs text-muted-foreground">
                Uses journey, moods and care history
              </Text>
            </View>
            <Toggle
              on={personalizedMemory}
              onPress={() => setPersonalizedMemory((v) => !v)}
            />
          </View>
          <Divider />
          <Row label="Review what Aira remembers" />
          <Divider />
          <Row label="Consent and privacy choices" />
        </Section>

        <Section title="Experience & Support">
          <Row label="Language" note="English" />
          <Divider />
          <Row label="Accessibility" onPress={() => router.push('/accessibility')} />
          <Divider />
          <Row label="Security and signed-in devices" />
          <Divider />
          <Row label="Help, feedback and crisis resources" />
        </Section>

        <Section title="Your Data">
          <Row label="Export my data" />
          <Divider />
          <Row
            label="Delete my account"
            danger
            onPress={() => router.push('/delete-account')}
          />
        </Section>

        <Text className="px-4 text-center font-rubik text-xs leading-relaxed text-muted-foreground">
          Changes save automatically on this device and synchronize when connected.
        </Text>

        <Pressable
          onPress={() => router.back()}
          accessibilityRole="button"
          className="active:opacity-90">
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
            <Icon name="check" size={18} className="text-brand-foreground" />
            <Text className="font-rubik-semibold text-base text-brand-foreground">Done</Text>
          </LinearGradient>
        </Pressable>
      </ScrollView>
    </SafeAreaView>
  );
}
