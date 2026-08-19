import { router } from 'expo-router';
import { useState } from 'react';
import { Pressable, ScrollView, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { Icon } from '@/components/icon';
import { ContinueButton, RadioCard, ToggleRow } from '@/components/onboarding/controls';

/**
 * AI personalisation — the explicit opt-in, shown once, before the first chat.
 *
 * Chat quietly uses stage, week, mood and care history to personalise replies.
 * That needs a plain-language opt-in at the moment it starts mattering, not a
 * toggle somebody might find in Settings later.
 *
 * ── What the switch actually controls ──
 *
 * `POST /v1/consent { feature: "personalization" }`, and it is enforced server
 * side inside `memory.context_summary` — not at the call site, so no future
 * caller can forget to ask. Revoking stops memory being USED; it never deletes,
 * so items stay reviewable and the switch can be turned back on.
 *
 * It gates SAVED MEMORY and nothing else. Three things reach the reply prompt
 * whether it is on or off, each pinned by a test in the backend:
 *
 *   - the person's name
 *   - their journey and week
 *   - the conversation they are currently having
 *
 * So the rows below are not three equal switches. Presenting them as if they
 * were would put three controls on screen where only one controls anything —
 * which is the exact defect `consent.FEATURES` was built to prevent, after
 * `partner_access` shipped as a toggle that changed nothing.
 */
export default function PersonalisationScreen() {
  const [personalise, setPersonalise] = useState(true);
  const [rememberCheckins, setRememberCheckins] = useState(true);

  function finish() {
    // POST /v1/consent { feature: "personalization", granted: personalise }
    //
    // `rememberCheckins` has no separate server-side feature today — it is the
    // same flag. It is a real control only once consent.FEATURES grows a key
    // for it; until then turning it off must ALSO clear the consent, or the
    // switch is decorative. Hence the guard below.
    //
    router.replace('/today');
  }

  const granted = personalise && rememberCheckins;

  return (
    <SafeAreaView className="flex-1 bg-background" edges={['top', 'bottom']}>
      <View className="px-5 pt-2">
        <Pressable
          onPress={() => router.back()}
          accessibilityRole="button"
          accessibilityLabel="Go back"
          hitSlop={14}
          className="h-10 w-10 justify-center active:opacity-60">
          <Icon name="arrow-left" size={22} className="text-foreground" />
        </Pressable>
      </View>

      <ScrollView
        contentContainerClassName="grow px-5 pb-6"
        showsVerticalScrollIndicator={false}>
        <View className="w-full max-w-sm flex-1 self-center">
          <Text className="font-rubik-semibold text-xs uppercase tracking-widest text-brand">
            Before we chat
          </Text>

          <Text className="mt-2 font-rubik-semibold text-3xl tracking-tight text-foreground">
            Aira remembers what you share, to help you better
          </Text>

          <Text className="mt-3 font-rubik text-base leading-relaxed text-muted-foreground">
            Your week, mood check-ins and care history shape Aira&apos;s replies and video
            picks. It&apos;s never sold, never used for ads, and you can turn it off or
            delete it anytime in Settings.
          </Text>

          <View className="mt-6 gap-2.5">
            <RadioCard
              title="Personalize my replies & videos"
              body="Uses your stage, week and recent check-ins. Recommended."
              badge={personalise ? 'Selected' : undefined}
              selected={personalise}
              onPress={() => setPersonalise(true)}
            />
            <RadioCard
              title="Keep chats generic, don't personalize"
              body="Aira won't reference your saved check-ins or care history in replies."
              selected={!personalise}
              onPress={() => setPersonalise(false)}
            />
          </View>

          <View className="mt-5 rounded-lg border border-border bg-card px-4 py-2">
            <Text className="py-2 font-rubik-semibold text-xs uppercase tracking-widest text-brand">
              Choose what Aira may remember
            </Text>

            {/* Always on, and said so rather than drawn as a switch that cannot
                move. Aira cannot answer a pregnancy question without knowing the
                stage — the journey phrase is in every system prompt, and a
                backend test pins that it survives this screen. */}
            <ToggleRow
              label="Journey stage and week"
              note="Always on — Aira can't answer without it"
              on
              disabled
              onToggle={() => {}}
            />

            <View className="h-px bg-border" />

            {/* The one row that genuinely maps to the consent. */}
            <ToggleRow
              label="Mood and care check-ins"
              note="What personalisation actually uses"
              on={personalise && rememberCheckins}
              disabled={!personalise}
              onToggle={() => setRememberCheckins((v) => !v)}
            />

            <View className="h-px bg-border" />

            {/* Off because it does not exist, not because it is switched off.
                There is no cross-session chat memory in this build; the current
                conversation is sent with each turn and nothing carries over. */}
            <ToggleRow
              label="Previous conversations"
              note="Not available in this build"
              on={false}
              disabled
              onToggle={() => {}}
            />
          </View>

          <ContinueButton
            label={
              granted ? 'Continue with personalization →' : 'Continue without it →'
            }
            enabled
            onPress={finish}
          />
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}
