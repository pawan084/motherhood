import { router } from 'expo-router';
import { Pressable, ScrollView, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { BrandOrb } from '@/components/brand-orb';
import { Icon, type IconName } from '@/components/icon';

/**
 * Welcome — the first screen after the splash.
 *
 * Three promises, in the order the product makes them: what Aira is, what it
 * does with your data, and what it refuses to be. The wording matches the web
 * client's tutorial cards, which are pinned by a test there precisely because
 * softening a promise on one platform and not the other means the product makes
 * two different promises — and the weaker one is what somebody relies on.
 *
 * Sizing uses the Tailwind scale only — no arbitrary `[18px]` values — so the
 * spacing rhythm is shared with every other screen instead of hand-tuned here.
 * Colours are tokens from global.css.
 */
export default function WelcomeScreen() {
  return (
    <SafeAreaView className="flex-1 bg-background" edges={['top', 'bottom']}>
      {/* Header. The status line and the privacy pill both say something true
          before the person has agreed to anything — which is the argument the
          rest of the screen then makes. */}
      <View className="flex-row items-center justify-between gap-3 px-5 pb-2.5 pt-1.5">
        <View className="flex-row items-center gap-2.5">
          <BrandOrb size={30} />
          <View className="gap-0.5">
            <Text className="font-rubik-semibold text-base text-foreground">Aira</Text>
            <View className="flex-row items-center gap-1.5">
              <View className="h-1.5 w-1.5 rounded-full bg-ok" />
              <Text className="font-rubik text-xs text-muted-foreground">Up to date</Text>
            </View>
          </View>
        </View>

        <View className="rounded-full bg-tile px-3 py-1.5">
          <Text className="font-rubik-medium text-xs text-tile-foreground">
            ✦ Private by design
          </Text>
        </View>
      </View>

      <ScrollView
        contentContainerClassName="grow px-5 pb-6"
        showsVerticalScrollIndicator={false}>
        <View className="w-full max-w-sm flex-1 self-center">
          <View className="items-center py-8">
            <BrandOrb size={104} />
          </View>

          <Text className="font-rubik-semibold text-3xl tracking-tight text-foreground">
            Your private AI companion for motherhood.
          </Text>

          <Text className="mt-3 font-rubik text-base leading-relaxed text-muted-foreground">
            One calm conversation for pregnancy, postpartum, and everything between.
          </Text>

          <View className="mt-6 gap-2.5">
            <Promise
              icon="shield"
              title="Safety checked"
              body="Clear support and human handoff"
            />
            <Promise
              icon="lock"
              title="Your context, your control"
              body="Review what Aira remembers"
            />
            <Promise
              icon="info"
              title="Never used for advertising"
              body="Sensitive health data is not an ad product"
            />
          </View>

          {/* `mt-auto` pushes this to the bottom of whatever space is left, so a
              tall screen keeps the button in thumb reach instead of stranding it
              under the cards.

              `bg-brand` is the base; the gradient utilities layer over it on
              web. NativeWind does not compile background gradients for native,
              so this degrades to the flat brand violet there rather than
              rendering nothing.

              Temporary edge: in the real flow this goes to onboarding
              (stage → timing → language → reminders → consent) and the account
              prompt comes AFTER it, framed "Save your journey". It points at
              /sign-in only so that screen is reachable while onboarding does not
              exist yet. */}
          <Pressable
            onPress={() => router.push('/sign-in?mode=signup')}
            accessibilityRole="button"
            accessibilityLabel="Start with Aira"
            className="mt-auto pt-6 active:opacity-90">
            <View className="items-center rounded-full bg-brand bg-gradient-to-br from-brand to-brand-deep py-4">
              <Text className="font-rubik-semibold text-base text-brand-foreground">
                Start with Aira →
              </Text>
            </View>
          </Pressable>

          <Pressable
            onPress={() => router.push('/sign-in?mode=signin')}
            accessibilityRole="button"
            className="mt-4 items-center active:opacity-60">
            <Text className="font-rubik-medium text-sm text-muted-foreground">
              Already have an account?{' '}
              <Text className="font-rubik-semibold text-brand">Sign in</Text>
            </Text>
          </Pressable>
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}

function Promise({ icon, title, body }: { icon: IconName; title: string; body: string }) {
  return (
    <View className="flex-row items-center gap-3 rounded-lg border border-border bg-card px-3.5 py-3">
      <View className="h-9 w-9 items-center justify-center rounded-xl bg-tile">
        <Icon name={icon} size={16} className="text-tile-foreground" />
      </View>
      <View className="flex-1 gap-0.5">
        <Text className="font-rubik-semibold text-sm text-foreground">{title}</Text>
        <Text className="font-rubik text-xs text-muted-foreground">{body}</Text>
      </View>
    </View>
  );
}
