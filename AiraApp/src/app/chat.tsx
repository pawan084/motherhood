import { router } from 'expo-router';
import { useState } from 'react';
import { Pressable, ScrollView, Text, TextInput, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { BrandOrb } from '@/components/brand-orb';
import { AiFeedbackSheet } from '@/components/ai-feedback-sheet';
import { ChatToolsSheet } from '@/components/chat-tools-sheet';
import { Icon } from '@/components/icon';
import { BottomNav } from '@/components/today/parts';

function Avatar() {
  return (
    <View className="h-8 w-8 items-center justify-center rounded-full bg-brand">
      <Text className="font-rubik-semibold text-sm text-brand-foreground">A</Text>
    </View>
  );
}

type SafetyMode = 'normal' | 'escalation' | 'offline';

function TypingDots() {
  return (
    <View className="flex-row items-center gap-2.5">
      <Avatar />
      <View className="flex-row gap-1 rounded-full bg-card px-4 py-3">
        <View className="h-2 w-2 rounded-full bg-muted-foreground opacity-60" />
        <View className="h-2 w-2 rounded-full bg-muted-foreground opacity-60" />
        <View className="h-2 w-2 rounded-full bg-muted-foreground opacity-60" />
      </View>
    </View>
  );
}

function EscalationNudge() {
  return (
    <>
      <View className="self-end rounded-[12px] bg-brand px-5 py-3">
        <Text className="font-rubik-medium text-base leading-snug text-brand-foreground">
          I haven&apos;t felt the baby move much today and I&apos;m scared
        </Text>
      </View>

      <View className="flex-row items-end gap-2.5">
        <Avatar />
        <View className="flex-1 rounded-[18px] border border-border bg-card px-4 py-3">
          <Text className="font-rubik text-base leading-snug text-foreground">
            That sounds frightening, and I&apos;m glad you told me. Reduced movement is
            worth checking on properly — I can&apos;t assess that for you.
          </Text>
        </View>
      </View>

      <View className="ml-10 self-start rounded-full bg-[#FFE2E5] px-3 py-1">
        <Text className="font-rubik-medium text-xs text-destructive">
          ⚠ Flagged for care follow-up
        </Text>
      </View>

      <View className="rounded-[18px] border border-destructive bg-[#FFF6F7] p-4">
        <View className="flex-row items-center gap-3">
          <Icon name="phone" size={24} className="text-foreground" />
          <Text className="flex-1 font-rubik-semibold text-base text-[#7A2024]">
            Please contact your care team today
          </Text>
        </View>
        <Text className="mt-3 font-rubik text-sm leading-relaxed text-[#8C2E33]">
          Reduced movement is best checked by a professional the same day, not tomorrow.
        </Text>
        <Pressable accessibilityRole="button" className="mt-4 active:opacity-90">
          <View className="flex-row items-center justify-center gap-2 rounded-[14px] bg-[#D23542] px-5 py-4">
            <Icon name="phone" size={19} className="text-white" />
            <Text className="font-rubik-semibold text-base text-white">Call care team</Text>
          </View>
        </Pressable>
      </View>

      <View className="flex-row gap-2">
        <Pressable accessibilityRole="button" className="flex-1 active:opacity-80">
          <View className="items-center rounded-full border border-border bg-card px-3 py-3">
            <Text className="font-rubik-medium text-xs text-foreground">
              What counts as reduced movement?
            </Text>
          </View>
        </Pressable>
        <Pressable accessibilityRole="button" className="flex-1 active:opacity-80">
          <View className="items-center rounded-full border border-border bg-card px-3 py-3">
            <Text className="font-rubik-medium text-xs text-foreground">
              I&apos;ve called them
            </Text>
          </View>
        </Pressable>
      </View>
    </>
  );
}

function SafetyOfflineFallback({ onFeedback }: { onFeedback: () => void }) {
  return (
    <>
      <View className="self-end rounded-[12px] bg-brand px-5 py-3">
        <Text className="font-rubik-medium text-base leading-snug text-brand-foreground">
          I&apos;ve been having sharp pain since this morning
        </Text>
      </View>

      <View className="rounded-[20px] border border-border bg-secondary p-5">
        <View className="flex-row items-center gap-3">
          <Icon name="wifi-off" size={24} className="text-foreground" />
          <Text className="flex-1 font-rubik-semibold text-base text-foreground">
            Safety check is offline right now
          </Text>
        </View>
        <Text className="mt-3 font-rubik text-sm leading-relaxed text-foreground">
          Aira won&apos;t guess on health questions without it. For sharp or worsening
          pain, please contact your care team directly.
        </Text>
        <Pressable accessibilityRole="button" className="mt-4 active:opacity-90">
          <View className="flex-row items-center justify-center gap-2 rounded-[14px] bg-[#D23542] px-5 py-4">
            <Icon name="phone" size={19} className="text-white" />
            <Text className="font-rubik-semibold text-base text-white">Call care team</Text>
          </View>
        </Pressable>
        <Pressable accessibilityRole="button" className="mt-3 active:opacity-80">
          <View className="flex-row items-center justify-center gap-2 rounded-[14px] border border-border bg-card px-5 py-4">
            <Icon name="refresh-cw" size={18} className="text-brand" />
            <Text className="font-rubik-semibold text-base text-brand">Try again</Text>
          </View>
        </Pressable>
        <Pressable
          onPress={onFeedback}
          accessibilityRole="button"
          className="mt-3 items-center py-1 active:opacity-70">
          <Text className="font-rubik-semibold text-xs text-brand">This didn&apos;t help</Text>
        </Pressable>
      </View>

      <View className="flex-row gap-2">
        <Pressable
          onPress={() => router.push('/emergency-profile')}
          accessibilityRole="button"
          className="flex-1 active:opacity-80">
          <View className="items-center rounded-full border border-border bg-card px-3 py-3">
            <Text className="font-rubik-medium text-xs text-foreground">
              Open emergency profile
            </Text>
          </View>
        </Pressable>
        <Pressable accessibilityRole="button" className="flex-1 active:opacity-80">
          <View className="items-center rounded-full border border-border bg-card px-3 py-3">
            <Text className="font-rubik-medium text-xs text-foreground">
              Browse videos instead
            </Text>
          </View>
        </Pressable>
      </View>
    </>
  );
}

function NormalChatPreview() {
  return (
    <>
      <View className="flex-row items-end gap-2.5">
        <Avatar />
        <View className="flex-1 rounded-[18px] border border-border bg-card px-4 py-3">
          <Text className="font-rubik text-base leading-snug text-foreground">
            Welcome back. How are you feeling today?
          </Text>
        </View>
      </View>

      <View className="ml-10 self-start rounded-full bg-[#D8F0DD] px-3 py-1">
        <Text className="font-rubik-medium text-xs text-ok">Safety checked</Text>
      </View>

      <View className="self-end rounded-[12px] bg-brand px-5 py-3">
        <Text className="font-rubik-medium text-base text-brand-foreground">
          I&apos;ve been really tired lately
        </Text>
      </View>

      <TypingDots />

      <View className="rounded-[18px] border border-brand bg-secondary p-4">
        <View className="flex-row items-start gap-3">
          <View className="h-10 w-10 items-center justify-center rounded-[14px] bg-brand">
            <Icon name="zap" size={18} className="text-brand-foreground" />
          </View>
          <View className="flex-1">
            <Text className="font-rubik-semibold text-xs uppercase tracking-widest text-brand">
              Next best action
            </Text>
            <Text className="font-rubik text-xs text-muted-foreground">
              Based on your day right now
            </Text>
          </View>
        </View>

        <Text className="mt-3 font-serif text-[26px] leading-8 text-foreground">
          Water check
        </Text>
        <Text className="mt-1 font-rubik text-base leading-snug text-muted-foreground">
          3 of 8 glasses so far — a good moment for one.
        </Text>

        <Pressable accessibilityRole="button" className="mt-4 active:opacity-90">
          <View className="items-center rounded-[14px] bg-brand px-5 py-4">
            <Text className="font-rubik-semibold text-base text-brand-foreground">
              Open today&apos;s care ›
            </Text>
          </View>
        </Pressable>
      </View>

      <View className="flex-row gap-2">
        <Pressable accessibilityRole="button" className="flex-1 active:opacity-80">
          <View className="items-center rounded-full border border-border bg-card px-3 py-3">
            <Text className="font-rubik-medium text-xs text-foreground">
              What&apos;s happening in week 24?
            </Text>
          </View>
        </Pressable>
        <Pressable accessibilityRole="button" className="flex-1 active:opacity-80">
          <View className="items-center rounded-full border border-border bg-card px-3 py-3">
            <Text className="font-rubik-medium text-xs text-foreground">Set a reminder</Text>
          </View>
        </Pressable>
      </View>
    </>
  );
}

export default function ChatScreen() {
  const [toolsOpen, setToolsOpen] = useState(false);
  const [feedbackOpen, setFeedbackOpen] = useState(false);
  const [safetyMode] = useState<SafetyMode>('offline');
  const inputPaused = safetyMode === 'offline';

  return (
    <SafeAreaView className="flex-1 bg-background" edges={['top']}>
      <View className="border-b border-border bg-card px-5 pb-4 pt-2">
        <View className="flex-row items-center justify-between gap-3">
          <View className="flex-row items-center gap-3">
            <BrandOrb size={36} />
            <View>
              <Text className="font-rubik-semibold text-xl text-foreground">Aira</Text>
              <Text className="font-rubik text-xs text-muted-foreground">
                Your care companion
              </Text>
              <View className="mt-1 flex-row items-center gap-1.5">
                <View className="h-1.5 w-1.5 rounded-full bg-ok" />
                <Text className="font-rubik text-xs text-muted-foreground">Up to date</Text>
              </View>
            </View>
          </View>

          <View className="flex-row items-center gap-2">
            <Pressable
              onPress={() => router.push('/settings')}
              accessibilityRole="button"
              accessibilityLabel="Settings"
              className="active:opacity-70">
              <View className="h-11 w-11 items-center justify-center rounded-full border border-border bg-card">
                <Icon name="settings" size={19} className="text-foreground" />
              </View>
            </Pressable>
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
      </View>

      <ScrollView
        contentContainerClassName="gap-3 px-5 pb-44 pt-3"
        showsVerticalScrollIndicator={false}>
        <View className="flex-row items-center justify-between gap-3">
          <View className="flex-1 rounded-full bg-secondary px-4 py-2">
            <Text className="font-rubik-semibold text-xs text-brand">
              ✦ Remembering: week 24 · tired lately · water goal
            </Text>
          </View>
          <Pressable accessibilityRole="button" hitSlop={8} className="active:opacity-60">
            <Text className="font-rubik-semibold text-xs text-brand">Manage</Text>
          </Pressable>
        </View>

        {safetyMode === 'normal' && <NormalChatPreview />}
        {safetyMode === 'escalation' && <EscalationNudge />}
        {safetyMode === 'offline' && (
          <SafetyOfflineFallback onFeedback={() => setFeedbackOpen(true)} />
        )}

        <View className="flex-row items-start gap-3 rounded-lg bg-notice px-3.5 py-3">
          <Icon name="bell" size={22} className="mt-0.5 text-foreground" />
          <Text className="flex-1 font-rubik text-xs leading-relaxed text-notice-foreground">
            Tap + anytime for Care Vault, reminders, and more — not just typing.
          </Text>
          <Pressable accessibilityRole="button" accessibilityLabel="Dismiss" hitSlop={10}>
            <Icon name="x" size={18} className="text-notice-foreground" />
          </Pressable>
        </View>

        <Text className="text-center font-rubik text-xs text-muted-foreground">
          Aira offers general support, not medical advice.
        </Text>
      </ScrollView>

      <View className="absolute inset-x-0 bottom-[92px] px-5">
        <View className="flex-row items-center gap-3">
          <Pressable
            onPress={() => setToolsOpen(true)}
            accessibilityRole="button"
            accessibilityLabel="Open tools"
            className="active:opacity-70">
            <Icon name="plus" size={24} className="text-brand" />
          </Pressable>
          <View className="min-w-0 flex-1 flex-row items-center rounded-full border border-border bg-card py-2 pl-5 pr-2">
            <TextInput
              editable={!inputPaused}
              placeholder={inputPaused ? 'Chat is paused while safety check is offline' : 'Message Aira...'}
              placeholderTextColor="#8A8295"
              className={
                inputPaused
                  ? 'min-h-[34px] flex-1 font-rubik text-base text-muted-foreground outline-none'
                  : 'min-h-[34px] flex-1 font-rubik text-base text-foreground outline-none'
              }
              style={{ minWidth: 0 }}
            />
            <Pressable
              disabled={inputPaused}
              accessibilityRole="button"
              accessibilityLabel="Send message"
              accessibilityState={{ disabled: inputPaused }}
              className="active:opacity-80">
              <View
                className={
                  inputPaused
                    ? 'h-9 w-9 items-center justify-center rounded-full bg-muted'
                    : 'h-9 w-9 items-center justify-center rounded-full bg-tile'
                }>
                <Icon name="send" size={18} className="text-tile-foreground" />
              </View>
            </Pressable>
          </View>
          <Pressable
            onPress={() => router.push('/voice')}
            accessibilityRole="button"
            accessibilityLabel="Start voice input"
            className="active:opacity-80">
            <Icon name="mic" size={23} className="text-brand" />
          </Pressable>
        </View>
      </View>

      <ChatToolsSheet visible={toolsOpen} onClose={() => setToolsOpen(false)} />
      <AiFeedbackSheet visible={feedbackOpen} onClose={() => setFeedbackOpen(false)} />
      <BottomNav active="chat" />
    </SafeAreaView>
  );
}
