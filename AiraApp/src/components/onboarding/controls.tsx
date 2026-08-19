import { Pressable, Text, View } from 'react-native';

import { Icon, type IconName } from '@/components/icon';

/**
 * The shared controls for the four onboarding steps.
 *
 * Every step is one question with one kind of answer, so the chrome — progress,
 * eyebrow, heading, bottom-anchored Continue — lives here rather than being
 * re-typed four times and drifting.
 */

/** The thin fill across the top. Width is computed, so it is an inline style. */
export function StepProgress({ step, total }: { step: number; total: number }) {
  return (
    <View className="h-1 w-full bg-muted">
      <View className="h-1 rounded-full bg-brand" style={{ width: `${(step / total) * 100}%` }} />
    </View>
  );
}

export function StepHeading({
  step,
  total,
  title,
  lede,
}: {
  step: number;
  total: number;
  title: string;
  lede: string;
}) {
  return (
    <View>
      <Text className="font-rubik-semibold text-xs uppercase tracking-widest text-brand">
        Step {step} of {total}
      </Text>
      <Text className="mt-2 font-rubik-semibold text-3xl tracking-tight text-foreground">
        {title}
      </Text>
      <Text className="mt-3 font-rubik text-base leading-relaxed text-muted-foreground">
        {lede}
      </Text>
    </View>
  );
}

/** A full-width choice. Used where the options are few and deserve room. */
export function OptionCard({
  label,
  sublabel,
  icon,
  selected,
  onPress,
}: {
  label: string;
  sublabel?: string;
  icon?: IconName;
  selected: boolean;
  onPress: () => void;
}) {
  return (
    <Pressable
      onPress={onPress}
      accessibilityRole="radio"
      accessibilityState={{ selected }}
      className="active:opacity-80">
      <View
        className={
          selected
            ? 'flex-row items-center gap-3 rounded-lg border-2 border-brand bg-card px-4 py-4'
            : 'flex-row items-center gap-3 rounded-lg border border-border bg-card px-4 py-4'
        }>
        {icon && (
          <View className="h-9 w-9 items-center justify-center rounded-xl bg-tile">
            <Icon name={icon} size={16} className="text-tile-foreground" />
          </View>
        )}
        <View className="flex-1">
          <Text className="font-rubik-medium text-base text-foreground">{label}</Text>
          {sublabel && (
            <Text className="mt-0.5 font-rubik text-xs text-muted-foreground">{sublabel}</Text>
          )}
        </View>
      </View>
    </Pressable>
  );
}

/** A pill. Used where there are enough options that cards would be a wall. */
export function Chip({
  label,
  selected,
  onPress,
}: {
  label: string;
  selected: boolean;
  onPress: () => void;
}) {
  return (
    <Pressable
      onPress={onPress}
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
          {label}
        </Text>
      </View>
    </Pressable>
  );
}

/** Three-up segmented control, for a small set of mutually exclusive values. */
export function Segmented<T extends string | number>({
  options,
  value,
  onChange,
}: {
  options: { value: T; label: string }[];
  value: T | undefined;
  onChange: (v: T) => void;
}) {
  return (
    <View className="flex-row gap-1 rounded-full bg-muted p-1">
      {options.map((o) => {
        const on = o.value === value;
        return (
          <Pressable
            key={String(o.value)}
            onPress={() => onChange(o.value)}
            accessibilityRole="radio"
            accessibilityState={{ selected: on }}
            className="flex-1 active:opacity-80">
            <View className={on ? 'items-center rounded-full bg-card py-2.5' : 'items-center rounded-full py-2.5'}>
              <Text
                className={
                  on
                    ? 'font-rubik-semibold text-sm text-brand'
                    : 'font-rubik-medium text-sm text-muted-foreground'
                }>
                {o.label}
              </Text>
            </View>
          </Pressable>
        );
      })}
    </View>
  );
}

/** A row that displays a setting and its current value. */
export function SettingRow({ label, value, note }: { label: string; value: string; note?: string }) {
  return (
    <View className="flex-row items-center justify-between gap-3 rounded-lg border border-border bg-card px-4 py-3.5">
      <View className="flex-1">
        <Text className="font-rubik-medium text-sm text-foreground">{label}</Text>
        {note && <Text className="mt-0.5 font-rubik text-xs text-muted-foreground">{note}</Text>}
      </View>
      <View className="rounded-full bg-secondary px-3 py-1.5">
        <Text className="font-rubik-medium text-xs text-brand">{value}</Text>
      </View>
    </View>
  );
}

/** An informational aside — a hint worth reading, not a warning. */
export function Notice({ icon, children }: { icon: IconName; children: React.ReactNode }) {
  return (
    <View className="flex-row items-start gap-3 rounded-lg bg-notice p-3.5">
      <Icon name={icon} size={16} className="mt-0.5 text-notice-foreground" />
      <Text className="flex-1 font-rubik text-xs leading-relaxed text-notice-foreground">
        {children}
      </Text>
    </View>
  );
}

/** Bottom-anchored primary action, disabled until the step has an answer. */
export function ContinueButton({
  label = 'Continue →',
  enabled,
  onPress,
}: {
  label?: string;
  enabled: boolean;
  onPress: () => void;
}) {
  return (
    <Pressable
      onPress={onPress}
      disabled={!enabled}
      accessibilityRole="button"
      accessibilityState={{ disabled: !enabled }}
      className="mt-auto pt-8 active:opacity-90">
      <View
        className={
          enabled
            ? 'items-center rounded-full bg-brand bg-gradient-to-br from-brand to-brand-deep py-4'
            : 'items-center rounded-full bg-muted py-4'
        }>
        <Text
          className={
            enabled
              ? 'font-rubik-semibold text-base text-brand-foreground'
              : 'font-rubik-semibold text-base text-muted-foreground'
          }>
          {label}
        </Text>
      </View>
    </Pressable>
  );
}

/** A radio choice with an optional badge — one of a mutually exclusive pair. */
export function RadioCard({
  title,
  body,
  badge,
  selected,
  onPress,
}: {
  title: string;
  body: string;
  badge?: string;
  selected: boolean;
  onPress: () => void;
}) {
  return (
    <Pressable
      onPress={onPress}
      accessibilityRole="radio"
      accessibilityState={{ selected }}
      className="active:opacity-80">
      <View
        className={
          selected
            ? 'flex-row items-start gap-3 rounded-lg border-2 border-brand bg-secondary p-4'
            : 'flex-row items-start gap-3 rounded-lg border border-border bg-card p-4'
        }>
        <View
          className={
            selected
              ? 'mt-0.5 h-5 w-5 items-center justify-center rounded-full border-2 border-brand'
              : 'mt-0.5 h-5 w-5 items-center justify-center rounded-full border-2 border-border'
          }>
          {selected && <View className="h-2.5 w-2.5 rounded-full bg-brand" />}
        </View>
        <View className="flex-1">
          <View className="flex-row items-start justify-between gap-2">
            <Text className="flex-1 font-rubik-semibold text-base text-foreground">{title}</Text>
            {badge && (
              <View className="rounded-full bg-brand px-2 py-1">
                <Text className="font-rubik-semibold text-xs uppercase tracking-wide text-brand-foreground">
                  {badge}
                </Text>
              </View>
            )}
          </View>
          <Text className="mt-1 font-rubik text-xs leading-relaxed text-muted-foreground">
            {body}
          </Text>
        </View>
      </View>
    </Pressable>
  );
}

/**
 * A switch.
 *
 * Hand-built rather than React Native's `Switch`, whose `trackColor` and
 * `thumbColor` are colour PROPS — they take literals, so using it would mean
 * hardcoding hexes that global.css already owns.
 */
export function Toggle({
  on,
  disabled,
  onPress,
  label,
}: {
  on: boolean;
  disabled?: boolean;
  onPress: () => void;
  label: string;
}) {
  return (
    <Pressable
      onPress={disabled ? undefined : onPress}
      accessibilityRole="switch"
      accessibilityLabel={label}
      accessibilityState={{ checked: on, disabled: !!disabled }}
      className={disabled ? 'opacity-50' : 'active:opacity-80'}>
      <View
        className={
          on
            ? 'h-6 w-11 justify-center rounded-full bg-brand px-0.5'
            : 'h-6 w-11 justify-center rounded-full bg-muted px-0.5'
        }>
        <View className={on ? 'h-5 w-5 self-end rounded-full bg-card' : 'h-5 w-5 self-start rounded-full bg-card'} />
      </View>
    </Pressable>
  );
}

/**
 * One row of the "what Aira may remember" list.
 *
 * `note` is how a row that is a STATEMENT rather than a control says so — the
 * same distinction `consent.FEATURES` draws with its `locked` and
 * `available: false` entries. A switch that cannot move needs to explain itself,
 * or it reads as broken.
 */
export function ToggleRow({
  label,
  note,
  on,
  disabled,
  onToggle,
}: {
  label: string;
  note?: string;
  on: boolean;
  disabled?: boolean;
  onToggle: () => void;
}) {
  return (
    <View className="flex-row items-center justify-between gap-3 py-2.5">
      <View className="flex-1">
        <Text className="font-rubik-medium text-sm text-foreground">{label}</Text>
        {note && <Text className="mt-0.5 font-rubik text-xs text-muted-foreground">{note}</Text>}
      </View>
      <Toggle on={on} disabled={disabled} onPress={onToggle} label={label} />
    </View>
  );
}
