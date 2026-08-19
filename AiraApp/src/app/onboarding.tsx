import { router } from 'expo-router';
import { useState } from 'react';
import { Pressable, ScrollView, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { Icon } from '@/components/icon';
import {
  Chip,
  ContinueButton,
  Notice,
  OptionCard,
  Segmented,
  SettingRow,
  StepHeading,
  StepProgress,
} from '@/components/onboarding/controls';

/**
 * Onboarding — four questions, one per screen.
 *
 * ── Why one route and not four ──
 *
 * `POST /v1/onboarding` takes the whole answer set in a single call and sets
 * `onboarded: true` as it goes. So the answers are collected here and submitted
 * once at the end. Posting per step would mark somebody onboarded halfway
 * through and leave them with a profile that is missing the fields every later
 * screen reads.
 *
 * Keeping the draft in one component also means Back steps backwards through the
 * questions instead of unwinding a navigation stack that has to carry state.
 */

const TOTAL_STEPS = 4;

/** Must match `accounts.VALID_JOURNEYS` on the server. */
type Journey = 'pregnant' | 'postpartum' | 'trying' | 'loss';

/**
 * Must match `safety.SUPPORTED_LANGUAGES` EXACTLY. The server rejects anything
 * else with a 400 — not as fussiness, but because the deterministic safety
 * keyword floor can only read these three. Offering a fourth would invite
 * somebody to write in a language nothing is screening.
 */
const LANGUAGES = ['English', 'Hindi', 'Hinglish'] as const;
type Language = (typeof LANGUAGES)[number];

type Draft = {
  journey?: Journey;
  weeks?: number | null;
  priorities: string[];
  language?: Language;
  remindersPerDay?: 1 | 2 | 3;
};

export default function OnboardingScreen() {
  const [step, setStep] = useState(1);
  const [draft, setDraft] = useState<Draft>({ priorities: [], remindersPerDay: 2 });

  function set<K extends keyof Draft>(key: K, value: Draft[K]) {
    setDraft((d) => ({ ...d, [key]: value }));
  }

  function back() {
    if (step === 1) router.back();
    else setStep((s) => s - 1);
  }

  function next() {
    if (step < TOTAL_STEPS) setStep((s) => s + 1);
    else finish();
  }

  function finish() {
    // POST /v1/onboarding { journey, language, priorities, weeks }
    //   → returns the assembled Today, and sets onboarded: true
    //
    // Not wired: there is no API client yet. `draft` holds exactly the shape the
    // endpoint accepts, so this becomes one call.
    //
    // Then the personalisation opt-in, which must come BEFORE the first chat
    // turn — it is what decides whether saved memory shapes a reply at all.
    router.replace('/personalisation');
  }

  // Whether this step has a real answer yet. Step 1 is deliberately strict:
  // every later screen — week hero, videos, guidance — is keyed to the journey,
  // so there is no Skip and no pre-picked default.
  const canContinue =
    step === 1
      ? draft.journey !== undefined
      : step === 2
        ? draft.journey === 'pregnant'
          ? draft.weeks !== undefined
          : draft.priorities.length > 0
        : step === 3
          ? draft.language !== undefined
          : true;

  return (
    <SafeAreaView className="flex-1 bg-background" edges={['top', 'bottom']}>
      <StepProgress step={step} total={TOTAL_STEPS} />

      <View className="flex-row items-center justify-between px-5 pt-2">
        <Pressable
          onPress={back}
          accessibilityRole="button"
          accessibilityLabel="Go back"
          hitSlop={14}
          className="h-10 w-10 justify-center active:opacity-60">
          <Icon name="arrow-left" size={22} className="text-foreground" />
        </Pressable>

        {/* Step 1 has no Skip on purpose. The others do: a skipped answer still
            has an honest default, where a missing journey has none. */}
        {step > 1 && (
          <Pressable
            onPress={next}
            accessibilityRole="button"
            hitSlop={10}
            className="active:opacity-60">
            <Text className="font-rubik-semibold text-sm text-brand">Skip</Text>
          </Pressable>
        )}
      </View>

      <ScrollView
        contentContainerClassName="grow px-5 pb-6"
        showsVerticalScrollIndicator={false}>
        <View className="w-full max-w-sm flex-1 self-center">
          {step === 1 && <StageStep draft={draft} set={set} />}
          {step === 2 && <TimingStep draft={draft} set={set} />}
          {step === 3 && <LanguageStep draft={draft} set={set} />}
          {step === 4 && <RemindersStep draft={draft} set={set} />}

          <ContinueButton
            label={step === TOTAL_STEPS ? "You're all set →" : 'Continue →'}
            enabled={canContinue}
            onPress={next}
          />
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}

type StepProps = {
  draft: Draft;
  set: <K extends keyof Draft>(key: K, value: Draft[K]) => void;
};

// ── 1 · stage ────────────────────────────────────────────────────────────────

const STAGES: { value: Journey; label: string; sublabel: string }[] = [
  { value: 'pregnant', label: 'Pregnant', sublabel: 'Week-by-week guidance and videos' },
  { value: 'postpartum', label: 'Postpartum', sublabel: 'Recovery and life with a newborn' },
  { value: 'trying', label: 'Trying to conceive', sublabel: 'Cycle-aware support' },
  // The reference onboarding omits this; its stage-picker includes it, and the
  // server supports it fully — no week count, no countdown, an empty video
  // library rather than a filtered one. Without it, somebody who has had a loss
  // has to pick "Trying to conceive", which is the cruellest available answer.
  { value: 'loss', label: 'After a loss', sublabel: 'Gentle support, nothing asked of you' },
];

function StageStep({ draft, set }: StepProps) {
  return (
    <View className="gap-6">
      <StepHeading
        step={1}
        total={TOTAL_STEPS}
        title="Where are you in your journey?"
        lede="This shapes your week, your videos, and how Aira talks to you. You can change it anytime."
      />
      <View className="gap-2.5">
        {STAGES.map((s) => (
          <OptionCard
            key={s.value}
            label={s.label}
            sublabel={s.sublabel}
            selected={draft.journey === s.value}
            onPress={() => set('journey', s.value)}
          />
        ))}
      </View>
    </View>
  );
}

// ── 2 · timing ───────────────────────────────────────────────────────────────

const WEEK_CHOICES = [8, 16, 24, 32];

/**
 * What Aira should focus on — asked instead of a week for the stages where a
 * gestational week means nothing.
 *
 * These go to `priorities`, which the server stores. The alternative was to ask
 * a postpartum user their baby's age and a trying user how long it has been:
 * both reasonable questions with nowhere to put the answer, since the API has no
 * field for either. Asking for data that cannot be saved is worse than not
 * asking.
 */
const PRIORITY_CHOICES = [
  'Better sleep',
  'Nutrition',
  'Feeling calmer',
  'Visit preparation',
  'Recovery',
  'Cycle tracking',
];

function TimingStep({ draft, set }: StepProps) {
  if (draft.journey === 'pregnant') {
    return (
      <View className="gap-6">
        <StepHeading
          step={2}
          total={TOTAL_STEPS}
          title="About how far along?"
          lede="Pregnant · helps Aira show the right week and guidance."
        />
        <View className="flex-row flex-wrap gap-2">
          {WEEK_CHOICES.map((w) => (
            <Chip
              key={w}
              label={`~${w} weeks`}
              selected={draft.weeks === w}
              onPress={() => set('weeks', w)}
            />
          ))}
          {/* Replaces a bare Skip: it still captures a real answer instead of a
              gap, and null is what the server stores for "unknown". */}
          <Chip
            label="Not sure yet"
            selected={draft.weeks === null}
            onPress={() => set('weeks', null)}
          />
        </View>
        <Notice icon="calendar">
          A due date is steadier than a week — you can add one later in Settings, and Aira
          will count forward from it instead.
        </Notice>
      </View>
    );
  }

  const label =
    draft.journey === 'postpartum'
      ? 'Postpartum'
      : draft.journey === 'loss'
        ? 'After a loss'
        : 'Trying to conceive';

  return (
    <View className="gap-6">
      <StepHeading
        step={2}
        total={TOTAL_STEPS}
        title="What would help most right now?"
        lede={`${label} · pick as many as you like. Aira leads with these.`}
      />
      <View className="flex-row flex-wrap gap-2">
        {PRIORITY_CHOICES.map((p) => {
          const on = draft.priorities.includes(p);
          return (
            <Chip
              key={p}
              label={p}
              selected={on}
              onPress={() =>
                set(
                  'priorities',
                  on ? draft.priorities.filter((x) => x !== p) : [...draft.priorities, p],
                )
              }
            />
          );
        })}
      </View>
    </View>
  );
}

// ── 3 · language ─────────────────────────────────────────────────────────────

const LANGUAGE_SUBLABELS: Record<Language, string> = {
  English: 'Continue in English',
  Hindi: 'हिंदी में बातचीत',
  Hinglish: 'A natural mix of Hindi and English',
};

function LanguageStep({ draft, set }: StepProps) {
  return (
    <View className="gap-6">
      <StepHeading
        step={3}
        total={TOTAL_STEPS}
        title="Which language feels most natural?"
        lede="You can switch anytime in Settings."
      />
      <View className="gap-2.5">
        {LANGUAGES.map((l) => (
          <OptionCard
            key={l}
            icon="globe"
            label={l}
            sublabel={LANGUAGE_SUBLABELS[l]}
            selected={draft.language === l}
            onPress={() => set('language', l)}
          />
        ))}
      </View>
      <Notice icon="lock">
        Core safety guidance is available in all three choices. Some videos may use English
        audio with translated subtitles; Aira will label those clearly.
      </Notice>
    </View>
  );
}

// ── 4 · reminders ────────────────────────────────────────────────────────────

function RemindersStep({ draft, set }: StepProps) {
  const perDay = draft.remindersPerDay ?? 2;
  const times = ['09:00', '20:00', '14:00'].slice(0, perDay);

  return (
    <View className="gap-6">
      <StepHeading
        step={4}
        total={TOTAL_STEPS}
        title="How often should we remind you?"
        lede="Gentle nudges for water and vitamins — change this anytime in Settings."
      />

      <Segmented
        options={[
          { value: 1 as const, label: '1× a day' },
          { value: 2 as const, label: '2× a day' },
          { value: 3 as const, label: '3× a day' },
        ]}
        value={perDay}
        onChange={(v) => set('remindersPerDay', v)}
      />

      <View className="gap-2.5">
        {times.map((t, i) => (
          <SettingRow key={t} label={`Reminder ${i + 1}`} value={t} />
        ))}
        <SettingRow
          label="Quiet hours"
          value="22:00–07:00"
          note="No routine reminders overnight"
        />
      </View>

      <Notice icon="bell">
        Reminders are scheduled on this device. Aira never sends a notification that names a
        medicine on your lock screen.
      </Notice>
    </View>
  );
}
