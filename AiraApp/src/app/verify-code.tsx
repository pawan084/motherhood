import { router } from 'expo-router';
import { useEffect, useRef, useState } from 'react';
import { Pressable, ScrollView, Text, TextInput, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { Icon } from '@/components/icon';
import {
  requestCodeSucceeded,
  setAuthCode,
} from '@/store/slices/authSlice';
import { useAppDispatch, useAppSelector } from '@/store/hooks';

/**
 * Verify code — the second half of the passwordless email path.
 *
 * ── One input, six boxes ──
 *
 * The six boxes are DRAWN; underneath them is a single transparent TextInput
 * holding all six digits. Six separate inputs is the obvious build and the wrong
 * one: paste fills only the first box, OS autofill of a one-time code has no
 * single field to target, and backspace at the start of a box has to be
 * hand-wired to jump backwards. One field gets all three for free — which is
 * also what lets the hint below promise that pasting works.
 *
 * ── What it cannot do yet ──
 *
 * The backend has no OTP endpoint and no mail provider, so nothing here can be
 * verified against a server. `submit()` and `resend()` name the endpoints they
 * need. This screen is UI-complete and deliberately not pretending otherwise.
 */
const CODE_LENGTH = 6;
const RESEND_SECONDS = 30;
const CODE_TTL_SECONDS = 10 * 60;

export default function VerifyCodeScreen() {
  const dispatch = useAppDispatch();
  const { email, mode, code, requestedAt, resendIn, expiresIn } = useAppSelector(
    (s) => s.auth,
  );

  const inputRef = useRef<TextInput>(null);

  // Stays local: a dismissed hint is a per-screen interaction, and in the store
  // it would stay dismissed for a code requested an hour later.
  const [showPasteHint, setShowPasteHint] = useState(true);

  /**
   * One clock, two countdowns.
   *
   * Both timers now derive from the single `requestedAt` the slice holds rather
   * than from two `useState` counters. That is not tidiness: a counter held in
   * component state RESTARTS whenever the screen remounts, so navigating away
   * and back handed you a fresh ten minutes on a code the server had already
   * expired. A timestamp cannot lie about how long ago the code was sent.
   *
   * `now` is local because it is a render concern — the passage of time is not
   * application state.
   */
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const t = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(t);
  }, []);

  /**
   * Stands in for `POST /account/code/request`, which does not exist yet.
   *
   * Marks the code as sent so both countdowns have an origin. When the endpoint
   * lands this becomes a thunk and the payload comes from the server, which is
   * the point of putting `expiresIn` and `resendIn` on the wire rather than
   * hardcoding them in the client.
   */
  useEffect(() => {
    if (requestedAt !== null) return;
    dispatch(
      requestCodeSucceeded({
        expiresIn: CODE_TTL_SECONDS,
        resendIn: RESEND_SECONDS,
        requestedAt: Date.now(),
      }),
    );
  }, [dispatch, requestedAt]);

  const elapsed = requestedAt === null ? 0 : Math.floor((now - requestedAt) / 1000);
  const secondsLeft = Math.max(0, (resendIn ?? RESEND_SECONDS) - elapsed);
  const codeSecondsLeft = Math.max(0, (expiresIn ?? CODE_TTL_SECONDS) - elapsed);
  const codeExpired = requestedAt !== null && codeSecondsLeft <= 0;

  const complete = code.length === CODE_LENGTH;

  // An expired code is cleared once, so the boxes do not keep showing digits
  // that can no longer be submitted.
  useEffect(() => {
    if (codeExpired && code) dispatch(setAuthCode(''));
  }, [codeExpired, code, dispatch]);

  function onChange(next: string) {
    // Digits only, and never longer than the code. Guards paste as well as
    // typing, so pasting "  481 902 " lands as "481902".
    dispatch(setAuthCode(next.replace(/[^0-9]/g, '').slice(0, CODE_LENGTH)));
  }

  function submit() {
    if (!complete) return;
    if (codeExpired) return;
    // POST /account/code/verify { email, code } → session token
    // Does not exist yet. See the note at the top of this file.
    //
    // New sign-ups still need onboarding. Existing-account sign-ins may need a
    // guest-history merge review before landing back in the app.
    router.replace(mode === 'signin' ? '/merge-review' : '/onboarding');
  }

  function resend() {
    // POST /account/code/request { email } — a new code restarts both clocks,
    // which is now one dispatch rather than four setStates that could drift.
    dispatch(
      requestCodeSucceeded({
        expiresIn: CODE_TTL_SECONDS,
        resendIn: RESEND_SECONDS,
        requestedAt: Date.now(),
      }),
    );
  }

  const mmss = `0:${String(Math.max(0, secondsLeft)).padStart(2, '0')}`;
  const codeMmss = `${Math.floor(codeSecondsLeft / 60)}:${String(codeSecondsLeft % 60).padStart(2, '0')}`;

  return (
    <SafeAreaView className="flex-1 bg-background" edges={['top', 'bottom']}>
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
          <Text className="font-rubik-semibold text-lg text-foreground">Check your email</Text>
        </View>
      </View>

      <ScrollView
        contentContainerClassName="grow px-5 pb-8 pt-5"
        keyboardShouldPersistTaps="handled"
        showsVerticalScrollIndicator={false}>
        <View className="w-full max-w-sm flex-1 self-center">
          {codeExpired ? (
            <View className="rounded-[20px] border border-destructive bg-[#FFF0F1] px-5 py-5">
              <Text className="font-rubik-semibold text-base text-destructive">
                That code has expired
              </Text>
              <Text className="mt-3 font-rubik text-sm leading-relaxed text-[#8C2E33]">
                Codes work for 10 minutes. Your account and guest history are unchanged.
              </Text>
            </View>
          ) : (
            <Text className="font-rubik text-base leading-relaxed text-muted-foreground">
              We sent a {CODE_LENGTH}-digit code to{' '}
              <Text className="font-rubik-semibold text-foreground">
                {email || 'your email'}
              </Text>
            </Text>
          )}

          {codeExpired ? (
            <Pressable
              onPress={() => inputRef.current?.focus()}
              accessibilityRole="button"
              accessibilityLabel={`Enter the new ${CODE_LENGTH} digit code`}
              className="mt-4">
              <View className="rounded-[16px] border border-border bg-card px-4 py-3">
                <Text className="font-rubik-medium text-xs text-muted-foreground">
                  New 6-digit code
                </Text>
                <Text className="mt-1 font-rubik text-base text-muted-foreground">
                  {code || 'Enter code'}
                </Text>
              </View>

              <TextInput
                ref={inputRef}
                value={code}
                onChangeText={onChange}
                keyboardType="number-pad"
                inputMode="numeric"
                maxLength={CODE_LENGTH}
                autoFocus
                textContentType="oneTimeCode"
                autoComplete="one-time-code"
                className="absolute inset-0 h-full w-full px-4 opacity-0"
              />
            </Pressable>
          ) : (
            <Pressable
              onPress={() => inputRef.current?.focus()}
              accessibilityRole="button"
              accessibilityLabel={`Enter the ${CODE_LENGTH} digit code`}
              className="mt-6">
              <View className="flex-row justify-between gap-2">
                {Array.from({ length: CODE_LENGTH }).map((_, i) => {
                  const filled = i < code.length;
                  return (
                    <View
                      key={i}
                      className={
                        filled
                          ? 'h-14 flex-1 items-center justify-center rounded-[14px] border-2 border-brand bg-card'
                          : 'h-14 flex-1 items-center justify-center rounded-[14px] border border-border bg-card'
                      }>
                      <Text className="font-rubik-medium text-2xl text-brand">
                        {code[i] ?? ''}
                      </Text>
                    </View>
                  );
                })}
              </View>

              <TextInput
                ref={inputRef}
                value={code}
                onChangeText={onChange}
                keyboardType="number-pad"
                inputMode="numeric"
                maxLength={CODE_LENGTH}
                autoFocus
                textContentType="oneTimeCode"
                autoComplete="one-time-code"
                className="absolute inset-0 h-14 w-full text-center opacity-0"
              />
            </Pressable>
          )}

          {!codeExpired && showPasteHint && (
            <View className="mt-4 flex-row items-start gap-3 rounded-lg bg-notice p-3.5">
              <Icon name="clipboard" size={16} className="mt-0.5 text-notice-foreground" />
              <Text className="flex-1 font-rubik text-xs leading-relaxed text-notice-foreground">
                You can paste the whole code at once — it fills all {CODE_LENGTH} boxes.
              </Text>
              <Pressable
                onPress={() => setShowPasteHint(false)}
                accessibilityRole="button"
                accessibilityLabel="Dismiss hint"
                hitSlop={10}
                className="active:opacity-60">
                <Icon name="x" size={16} className="text-notice-foreground" />
              </Pressable>
            </View>
          )}

          {!codeExpired && (
            <Text className="mt-3 font-rubik text-xs text-muted-foreground">
              Enter all {CODE_LENGTH} digits to continue. Code expires in {codeMmss}.
            </Text>
          )}

          {/* Bottom-anchored like the rest of the flow, rather than stranded
              mid-screen with a large empty gap below it. */}
          <Pressable
            onPress={codeExpired ? resend : submit}
            disabled={!codeExpired && !complete}
            accessibilityRole="button"
            accessibilityState={{ disabled: !codeExpired && !complete }}
            className={codeExpired ? 'mt-4 active:opacity-90' : 'mt-auto pt-8 active:opacity-90'}>
            <View
              className={
                codeExpired || complete
                  ? 'flex-row items-center justify-center gap-2 rounded-[16px] bg-brand bg-gradient-to-br from-brand to-brand-deep py-4'
                  : 'flex-row items-center justify-center gap-2 rounded-[16px] bg-muted py-4'
              }>
              <Text
                className={
                  codeExpired || complete
                    ? 'font-rubik-semibold text-base text-brand-foreground'
                    : 'font-rubik-semibold text-base text-muted-foreground'
                }>
                {codeExpired ? 'Send a new code' : 'Verify & continue'}
              </Text>
            </View>
          </Pressable>

          {codeExpired && (
            <Pressable
              onPress={() => router.back()}
              accessibilityRole="button"
              className="mt-3 active:opacity-80">
              <View className="items-center rounded-[16px] border border-border bg-card px-5 py-4">
                <Text className="font-rubik-semibold text-base text-brand">
                  Change email address
                </Text>
              </View>
            </Pressable>
          )}

          {!codeExpired && (
            <View className="mt-4 flex-row items-center justify-center gap-1.5">
              <Text className="font-rubik text-sm text-muted-foreground">
                Didn&apos;t get it?
              </Text>
              <Pressable
                onPress={resend}
                disabled={secondsLeft > 0}
                accessibilityRole="button"
                hitSlop={8}
                className="active:opacity-60">
                <Text className="font-rubik-semibold text-sm text-brand">
                  {secondsLeft > 0 ? `Resend in ${mmss}` : 'Resend code'}
                </Text>
              </Pressable>
            </View>
          )}
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}
