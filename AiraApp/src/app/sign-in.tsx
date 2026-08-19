import { router, useLocalSearchParams } from 'expo-router';
import { useState } from 'react';
import {
  KeyboardAvoidingView,
  Platform,
  Pressable,
  ScrollView,
  Text,
  TextInput,
  View,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { BrandOrb } from '@/components/brand-orb';
import { Icon } from '@/components/icon';
import { useToken } from '@/hooks/use-token';

/**
 * Sign in / create account — reached from "Save your journey" or Settings.
 *
 * Never a gate. Aira works fully from first launch on an anonymous device
 * identity, and an account exists only so care context can follow someone to
 * another device. Hence "Continue as guest" is a first-class exit, not fine
 * print.
 *
 * The email path is PASSWORDLESS: an address here, a six-digit code on
 * /verify-code. That is what the reference design specifies, and it needs
 * backend work that does not exist yet — there is no OTP endpoint and no mail
 * provider. What the server can do today is email + password; if that is wanted
 * as an interim, it is a password field here and no /verify-code hop.
 *
 * `mode` decides which endpoint completes the flow, and it comes from wherever
 * the user tapped rather than being guessed. That matters: signing UP carries
 * this device's anonymous care data onto the new account, and signing IN
 * deliberately does not — the server refuses to merge, because folding one
 * person's notes into an existing account cannot be undone.
 */

/**
 * Google Sign-In is only offered when this deployment has a client ID. The
 * backend verifies the ID token's `aud` against its own GOOGLE_CLIENT_ID and
 * returns 503 when unset, so rendering the button unconditionally would offer a
 * control that can only fail.
 */
const GOOGLE_CLIENT_ID = process.env.EXPO_PUBLIC_GOOGLE_CLIENT_ID ?? '';

type Mode = 'signup' | 'signin';

export default function SignInScreen() {
  const params = useLocalSearchParams<{ mode?: string }>();
  const mode: Mode = params.mode === 'signin' ? 'signin' : 'signup';

  const [email, setEmail] = useState('');
  const [submitted, setSubmitted] = useState(false);

  const placeholder = useToken('--muted-foreground');

  const trimmed = email.trim();
  const emailLooksWrong =
    !trimmed.includes('@') || trimmed.startsWith('@') || trimmed.endsWith('@');

  function submit() {
    setSubmitted(true);
    if (emailLooksWrong) return;
    // POST /account/code/request { email } — then the code screen verifies it.
    // The request is not wired because the endpoint does not exist; the hop is,
    // so the flow is walkable.
    router.push(`/verify-code?email=${encodeURIComponent(trimmed)}&mode=${mode}`);
  }

  return (
    <SafeAreaView className="flex-1 bg-background" edges={['top', 'bottom']}>
      <KeyboardAvoidingView
        className="flex-1"
        behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
        <ScrollView
          contentContainerClassName="grow px-6 pb-8"
          keyboardShouldPersistTaps="handled"
          showsVerticalScrollIndicator={false}>
          <View className="w-full max-w-sm flex-1 self-center">
            <Pressable
              onPress={() => router.back()}
              accessibilityRole="button"
              accessibilityLabel="Go back"
              hitSlop={14}
              className="mt-1 h-10 w-10 justify-center active:opacity-60">
              <Icon name="arrow-left" size={22} className="text-foreground" />
            </Pressable>

            <View className="mt-1">
              <BrandOrb size={34} />
            </View>

            <Text className="mt-4 font-rubik-semibold text-3xl tracking-tight text-foreground">
              {mode === 'signup' ? 'Save your journey' : 'Welcome back'}
            </Text>

            <Text className="mt-2 font-rubik text-base leading-relaxed text-muted-foreground">
              {mode === 'signup'
                ? 'Sign in to keep your history if you switch devices.'
                : 'Sign in to pick up where you left off.'}
            </Text>

            <View className="mt-3 flex-row items-center gap-2">
              <Icon name="lock" size={14} className="text-foreground" />
              <Text className="font-rubik-medium text-xs text-foreground">
                Private, always — never used for advertising
              </Text>
            </View>

            <View className="mt-4 rounded-lg border border-border bg-card px-3.5 pb-2.5 pt-2">
              <Text className="font-rubik-medium text-xs text-muted-foreground">Email</Text>
              <TextInput
                value={email}
                onChangeText={setEmail}
                onSubmitEditing={submit}
                placeholder="you@example.com"
                placeholderTextColor={placeholder}
                autoCapitalize="none"
                autoCorrect={false}
                autoComplete="email"
                keyboardType="email-address"
                inputMode="email"
                returnKeyType="go"
                className="font-rubik text-base text-foreground"
              />
            </View>

            {submitted && emailLooksWrong ? (
              <Text className="mt-2 font-rubik text-xs text-destructive">
                That doesn&apos;t look like an email address.
              </Text>
            ) : (
              <Text className="mt-2 font-rubik text-xs text-muted-foreground">
                We use your email only for sign-in, security alerts, and account recovery.
              </Text>
            )}

            <Pressable
              onPress={submit}
              accessibilityRole="button"
              className="mt-5 active:opacity-90">
              <View className="items-center rounded-full bg-brand bg-gradient-to-br from-brand to-brand-deep py-4">
                <Text className="font-rubik-semibold text-base text-brand-foreground">
                  Continue with email →
                </Text>
              </View>
            </Pressable>

            <View className="my-5 flex-row items-center gap-3">
              <View className="h-px flex-1 bg-border" />
              <Text className="font-rubik text-xs text-muted-foreground">
                or continue with
              </Text>
              <View className="h-px flex-1 bg-border" />
            </View>

            {GOOGLE_CLIENT_ID ? (
              <Pressable accessibilityRole="button" className="active:opacity-70">
                <View className="flex-row items-center justify-center gap-3 rounded-lg border border-border bg-card py-3.5">
                  <Text className="font-rubik-semibold text-base text-muted-foreground">G</Text>
                  <Text className="font-rubik-medium text-base text-foreground">
                    Continue with Google
                  </Text>
                </View>
              </Pressable>
            ) : (
              <Text className="text-center font-rubik text-xs leading-relaxed text-muted-foreground">
                Google Sign-In isn&apos;t configured for this build.
              </Text>
            )}

            {/* Apple is deliberately absent: the backend has no Apple endpoint,
                only Google. Note it becomes an App Store requirement once Google
                ships on iOS, so it is backend work, not a button. */}

            <Pressable
              onPress={() => router.replace('/merge-review')}
              accessibilityRole="button"
              className="mt-6 items-center active:opacity-60">
              <Text className="font-rubik-semibold text-base text-brand">
                Continue as guest →
              </Text>
            </Pressable>

            <Text className="mt-auto pt-8 text-center font-rubik text-xs leading-relaxed text-muted-foreground">
              By continuing you agree to Aira&apos;s Terms and Privacy Policy.
            </Text>
          </View>
        </ScrollView>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}
