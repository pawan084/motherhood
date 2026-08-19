import { Modal, Pressable, Text, View } from 'react-native';

import { Icon } from '@/components/icon';

export type JourneyStage = 'pregnant' | 'postpartum' | 'trying' | 'loss';

type StagePickerValue = {
  journey: JourneyStage;
  weeks: number | null;
};

const STAGES: { value: JourneyStage; label: string }[] = [
  { value: 'pregnant', label: 'Pregnant' },
  { value: 'postpartum', label: 'Postpartum' },
  { value: 'trying', label: 'Trying to conceive' },
  { value: 'loss', label: 'Navigating a loss' },
];

const WEEK_CHOICES = [8, 16, 24, 32];

export function StagePickerDialog({
  visible,
  value,
  onChange,
  onCancel,
  onSave,
}: {
  visible: boolean;
  value: StagePickerValue;
  onChange: (value: StagePickerValue) => void;
  onCancel: () => void;
  onSave: (value: StagePickerValue) => void;
}) {
  function setJourney(journey: JourneyStage) {
    onChange({ journey, weeks: journey === 'pregnant' ? value.weeks ?? 24 : null });
  }

  function setWeeks(weeks: number) {
    onChange({ journey: 'pregnant', weeks });
  }

  return (
    <Modal visible={visible} transparent animationType="fade" onRequestClose={onCancel}>
      <View className="flex-1 justify-center px-5">
        <Pressable
          accessibilityRole="button"
          accessibilityLabel="Close journey stage picker"
          onPress={onCancel}
          className="absolute inset-0">
          <View className="flex-1 bg-foreground opacity-35" />
        </Pressable>

        <View
          className="w-full max-w-sm self-center rounded-[24px] bg-card px-5 py-6"
          style={{
            shadowColor: '#7527F5',
            shadowOpacity: 0.18,
            shadowRadius: 26,
            shadowOffset: { width: 0, height: 18 },
            elevation: 12,
          }}>
          <Text className="font-serif text-[24px] leading-7 text-foreground">
            Where are you in your journey?
          </Text>
          <Text className="mt-2 font-rubik text-xs leading-relaxed text-muted-foreground">
            Aira uses this to shape your week, videos, and guidance.
          </Text>

          <View className="mt-5 gap-2.5">
            {STAGES.map((stage) => {
              const selected = value.journey === stage.value;
              return (
                <Pressable
                  key={stage.value}
                  onPress={() => setJourney(stage.value)}
                  accessibilityRole="radio"
                  accessibilityState={{ selected }}
                  className="active:opacity-80">
                  <View
                    className={
                      selected
                        ? 'rounded-[16px] border-2 border-brand bg-secondary px-4 py-3.5'
                        : 'rounded-[16px] border border-border bg-card px-4 py-3.5'
                    }>
                    <Text className="font-rubik-semibold text-sm text-foreground">
                      {stage.label}
                    </Text>
                  </View>
                </Pressable>
              );
            })}
          </View>

          <View className="mt-3 flex-row items-start gap-3 rounded-lg bg-secondary px-3.5 py-3">
            <Icon name="bell" size={20} className="mt-0.5 text-tile-foreground" />
            <Text className="flex-1 font-rubik text-xs leading-relaxed text-tile-foreground">
              Choosing loss support gently pauses pregnancy milestones and celebratory
              reminders. You can change this anytime.
            </Text>
          </View>

          {value.journey === 'pregnant' ? (
            <>
              <Text className="mt-4 font-rubik text-xs text-muted-foreground">
                About how far along?
              </Text>
              <View className="mt-2 flex-row flex-wrap gap-2">
                {WEEK_CHOICES.map((weeks) => {
                  const selected = value.weeks === weeks;
                  return (
                    <Pressable
                      key={weeks}
                      onPress={() => setWeeks(weeks)}
                      accessibilityRole="radio"
                      accessibilityState={{ selected }}
                      className="active:opacity-80">
                      <View
                        className={
                          selected
                            ? 'rounded-full bg-brand px-4 py-2.5'
                            : 'rounded-full border border-border bg-card px-4 py-2.5'
                        }>
                        <Text
                          className={
                            selected
                              ? 'font-rubik-medium text-sm text-brand-foreground'
                              : 'font-rubik-medium text-sm text-foreground'
                          }>
                          ~{weeks} weeks
                        </Text>
                      </View>
                    </Pressable>
                  );
                })}
              </View>
            </>
          ) : (
            <Text className="mt-4 font-rubik text-xs leading-relaxed text-muted-foreground">
              No week needed for this stage. Aira will keep guidance focused on the
              support you asked for.
            </Text>
          )}

          <View className="mt-6 flex-row gap-2.5">
            <Pressable
              onPress={onCancel}
              accessibilityRole="button"
              className="flex-1 active:opacity-80">
              <View className="items-center rounded-[16px] border border-border bg-card py-4">
                <Text className="font-rubik-semibold text-sm text-brand">Cancel</Text>
              </View>
            </Pressable>

            <Pressable
              onPress={() => onSave(value)}
              accessibilityRole="button"
              className="flex-1 active:opacity-90">
              <View className="flex-row items-center justify-center gap-2 rounded-[16px] bg-brand py-4">
                <Icon name="check" size={18} className="text-brand-foreground" />
                <Text className="font-rubik-semibold text-sm text-brand-foreground">
                  Save stage
                </Text>
              </View>
            </Pressable>
          </View>
        </View>
      </View>
    </Modal>
  );
}
