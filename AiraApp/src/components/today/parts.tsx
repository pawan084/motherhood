import { Modal, Pressable, Text, View } from 'react-native';
import { router } from 'expo-router';

import { BrandOrb } from '@/components/brand-orb';
import { Icon, type IconName } from '@/components/icon';
import { SCircle, SPolyline, Svg } from '@/components/svg';

/** A titled panel. Every block on Today is one of these. */
export function Card({
  label,
  action,
  onAction,
  children,
}: {
  label?: string;
  action?: string;
  onAction?: () => void;
  children: React.ReactNode;
}) {
  return (
    <View className="rounded-lg border border-border bg-card p-4">
      {(label || action) && (
        <View className="mb-3 flex-row items-center justify-between gap-3">
          {label && (
            <Text className="font-rubik-semibold text-xs uppercase tracking-widest text-brand">
              {label}
            </Text>
          )}
          {action && (
            <Pressable onPress={onAction} hitSlop={8} accessibilityRole="button" className="active:opacity-60">
              <Text className="font-rubik-medium text-sm text-brand">{action}</Text>
            </Pressable>
          )}
        </View>
      )}
      {children}
    </View>
  );
}

/** Filled action. Gradient on web, flat brand on native — NativeWind does not
 *  compile background gradients for native. */
export function PrimaryButton({
  label,
  icon,
  onPress,
  className = '',
}: {
  label: string;
  icon?: IconName;
  onPress?: () => void;
  className?: string;
}) {
  return (
    <Pressable onPress={onPress} accessibilityRole="button" className={`active:opacity-90 ${className}`}>
      <View className="flex-row items-center justify-center gap-2 rounded-full bg-brand bg-gradient-to-br from-brand to-brand-deep px-5 py-3.5">
        {icon && <Icon name={icon} size={16} className="text-brand-foreground" />}
        <Text className="font-rubik-semibold text-base text-brand-foreground">{label}</Text>
      </View>
    </Pressable>
  );
}

export function GhostButton({
  label,
  onPress,
  className = '',
}: {
  label: string;
  onPress?: () => void;
  className?: string;
}) {
  return (
    <Pressable onPress={onPress} accessibilityRole="button" className={`active:opacity-70 ${className}`}>
      <View className="items-center rounded-full border border-border bg-card px-5 py-3.5">
        <Text className="font-rubik-semibold text-base text-brand">{label}</Text>
      </View>
    </Pressable>
  );
}

/**
 * A progress ring.
 *
 * `strokeDasharray` + `strokeDashoffset` on one circle, rotated so it starts at
 * twelve o'clock. Colours come from classNames — see components/svg.tsx for why
 * that needs an interop.
 */
export function ProgressRing({
  fraction,
  size = 64,
  stroke = 6,
  children,
}: {
  fraction: number;
  size?: number;
  stroke?: number;
  children?: React.ReactNode;
}) {
  const r = (size - stroke) / 2;
  const circumference = 2 * Math.PI * r;
  const clamped = Math.max(0, Math.min(1, fraction));

  return (
    <View className="items-center justify-center" style={{ width: size, height: size }}>
      <Svg width={size} height={size} style={{ position: 'absolute', transform: [{ rotate: '-90deg' }] }}>
        <SCircle
          cx={size / 2}
          cy={size / 2}
          r={r}
          strokeWidth={stroke}
          className="fill-transparent stroke-muted"
        />
        <SCircle
          cx={size / 2}
          cy={size / 2}
          r={r}
          strokeWidth={stroke}
          strokeLinecap="round"
          strokeDasharray={`${circumference} ${circumference}`}
          strokeDashoffset={circumference * (1 - clamped)}
          className="fill-transparent stroke-brand"
        />
      </Svg>
      {children}
    </View>
  );
}

/**
 * The mood trend line.
 *
 * A polyline plus a dot per point, drawn in a fixed viewBox and scaled by the
 * container — so the shape is resolution independent and the maths stays in
 * one place.
 */
export function Sparkline({ points, width = 280, height = 64 }: { points: number[]; width?: number; height?: number }) {
  const pad = 6;
  const stepX = (width - pad * 2) / Math.max(1, points.length - 1);
  const xy = points.map((p, i) => ({
    x: pad + i * stepX,
    y: pad + (1 - Math.max(0, Math.min(1, p))) * (height - pad * 2),
  }));

  return (
    <Svg width="100%" height={height} viewBox={`0 0 ${width} ${height}`}>
      <SPolyline
        points={xy.map((p) => `${p.x},${p.y}`).join(' ')}
        strokeWidth={2}
        strokeLinejoin="round"
        strokeLinecap="round"
        className="fill-transparent stroke-brand"
      />
      {xy.map((p, i) => (
        <SCircle key={i} cx={p.x} cy={p.y} r={3.5} strokeWidth={2} className="fill-card stroke-brand" />
      ))}
    </Svg>
  );
}

/** The floating tab pill. Three destinations, one of them active. */
export function BottomNav({ active }: { active: 'today' | 'chat' | 'videos' }) {
  const tabs: { key: 'today' | 'chat' | 'videos'; label: string; icon: IconName }[] = [
    { key: 'today', label: 'Today', icon: 'home' },
    { key: 'chat', label: 'Chat', icon: 'message-circle' },
    { key: 'videos', label: 'Videos', icon: 'video' },
  ];

  return (
    <View className="absolute inset-x-0 bottom-6 items-center px-5">
      <View className="flex-row items-center gap-1 rounded-full border border-border bg-card p-1.5">
        {tabs.map((t) => {
          const on = t.key === active;
          return (
            <Pressable
              key={t.key}
              onPress={() => {
                if (t.key === 'today') router.push('/today');
                if (t.key === 'chat') router.push('/chat');
                if (t.key === 'videos') router.push('/videos');
              }}
              accessibilityRole="tab"
              accessibilityState={{ selected: on }}
              className="active:opacity-80">
              <View
                className={
                  on
                    ? 'flex-row items-center gap-2 rounded-full bg-brand px-4 py-2.5'
                    : 'flex-row items-center gap-2 rounded-full px-4 py-2.5'
                }>
                <Icon
                  name={t.icon}
                  size={16}
                  className={on ? 'text-brand-foreground' : 'text-muted-foreground'}
                />
                <Text
                  className={
                    on
                      ? 'font-rubik-semibold text-sm text-brand-foreground'
                      : 'font-rubik-medium text-sm text-muted-foreground'
                  }>
                  {t.label}
                </Text>
              </View>
            </Pressable>
          );
        })}
      </View>
    </View>
  );
}

/**
 * Today with nothing on it.
 *
 * A first session, or a day with nothing scheduled, is a real state and gets a
 * designed one — not the ordinary stack of cards rendering zeroes, and not an
 * absent placeholder. Two things it has to do: say WHY the list is empty ("fills
 * in each morning") so it does not read as broken, and offer the one thing that
 * is always available anyway, which is Chat.
 *
 * The sync line is the other half of not reading as broken: somebody who expected
 * a care task needs to know whether they are looking at stale data.
 */
export function EmptyToday({
  lastSyncedMinutesAgo,
  onOpenChat,
}: {
  lastSyncedMinutesAgo: number;
  onOpenChat?: () => void;
}) {
  return (
    <View className="items-center rounded-lg border border-border bg-card px-5 py-8">
      <Text className="text-4xl" accessibilityElementsHidden importantForAccessibility="no">
        ⛅
      </Text>

      <Text className="mt-4 text-center font-rubik-semibold text-2xl leading-relaxed text-foreground">
        Nothing scheduled yet today
      </Text>

      <Text className="mt-2 text-center font-rubik text-sm leading-relaxed text-muted-foreground">
        Your care list fills in each morning. Check back after breakfast, or open Chat if
        something&apos;s on your mind right now.
      </Text>

      <PrimaryButton label="Open Chat" onPress={onOpenChat} className="mt-6 w-full" />

      <Text className="mt-3 text-center font-rubik text-xs leading-relaxed text-muted-foreground">
        Last synchronized {lastSyncedMinutesAgo} minute
        {lastSyncedMinutesAgo === 1 ? '' : 's'} ago. Pull to refresh if you expected a care
        task.
      </Text>
    </View>
  );
}

/**
 * Coming back after a few quiet days.
 *
 * Re-engagement copy in a wellness app carries real tone risk, so this is
 * written warm on purpose rather than defaulting to the streak-loss language
 * every habit app reaches for. Three rules it follows:
 *
 *   - No count of what was missed. "A few quiet days", never "3 days missed",
 *     and no broken-streak badge. Somebody pregnant, exhausted or grieving does
 *     not need an app keeping score.
 *   - Nothing to backfill. "Nothing to catch up on — just today" is the whole
 *     point: the ask is ten seconds, not a week of retroactive logging.
 *   - Leaving is a first-class answer. "Not now" is a plain, unpunished exit,
 *     and pausing reminders is offered as a REVERSIBLE alternative to switching
 *     them off — which is what somebody actually reaches for on a bad week.
 */
export function ReturningCard({
  onCheckIn,
  onNotNow,
  onPause,
}: {
  onCheckIn?: () => void;
  onNotNow?: () => void;
  onPause?: () => void;
}) {
  return (
    <View className="rounded-lg border border-border bg-secondary p-4">
      <View className="flex-row items-start gap-3">
        <BrandOrb size={34} />
        <View className="flex-1">
          <Text className="font-rubik-semibold text-base text-foreground">
            It&apos;s been a few quiet days
          </Text>
          <Text className="mt-0.5 font-rubik text-sm leading-relaxed text-muted-foreground">
            No pressure — pregnancy has days like that.
          </Text>
        </View>
      </View>

      <Text className="mt-4 font-rubik text-sm leading-relaxed text-foreground">
        Whenever you&apos;re ready, a 10-second check-in helps Aira keep your week accurate.
        Nothing to catch up on — just today.
      </Text>

      <PrimaryButton
        label="How am I feeling today? →"
        onPress={onCheckIn}
        className="mt-4"
      />

      <Pressable
        onPress={onNotNow}
        accessibilityRole="button"
        className="mt-3 items-center py-1 active:opacity-60">
        <Text className="font-rubik-medium text-sm text-muted-foreground">Not now</Text>
      </Pressable>

      <GhostButton label="Pause reminders for a few days" onPress={onPause} className="mt-2" />
    </View>
  );
}

/**
 * The notification soft ask.
 *
 * A PRE-permission screen: it explains the benefit and takes the "no" itself,
 * so the operating system's own prompt is only ever fired by somebody who has
 * already said yes here. That ordering is the whole point on iOS, where the
 * system prompt is one-shot — a decline there can never be re-asked in-app, and
 * the only route back is the Settings app. Spending that single chance on a
 * cold prompt is how apps lose reminders permanently.
 *
 * Which is also why the footnote matters: declining has to be survivable, and
 * it is, because reminders still render inside Aira. The ask is for the
 * heads-up, not for the feature.
 */
export function NotificationAsk({
  visible,
  onEnable,
  onNotNow,
}: {
  visible: boolean;
  onEnable?: () => void;
  onNotNow?: () => void;
}) {
  return (
    <Modal visible={visible} transparent animationType="fade" onRequestClose={onNotNow}>
      <View className="flex-1 justify-center px-5">
        {/* A separate scrim view with `opacity-*` rather than an alpha colour:
            the tokens are `hsl(var(--x))` with no <alpha-value> slot, so a
            `bg-foreground/60` modifier would not resolve. */}
        <Pressable
          accessibilityRole="button"
          accessibilityLabel="Dismiss"
          onPress={onNotNow}
          className="absolute inset-0">
          <View className="flex-1 bg-foreground opacity-60" />
        </Pressable>

        <View className="rounded-lg bg-card p-6">
          <Icon name="bell" size={24} className="text-foreground" />

          <Text className="mt-4 font-rubik-semibold text-2xl leading-relaxed text-foreground">
            Never miss a moment that matters
          </Text>

          <Text className="mt-2 font-rubik text-sm leading-relaxed text-muted-foreground">
            Turn on notifications for gentle water and medicine reminders, and a heads-up
            before appointments. You choose the times — change or turn them off anytime in
            Settings.
          </Text>

          <PrimaryButton
            label="Turn on notifications"
            onPress={onEnable}
            className="mt-6"
          />

          <Pressable
            onPress={onNotNow}
            accessibilityRole="button"
            className="mt-3 items-center py-1 active:opacity-60">
            <Text className="font-rubik-semibold text-sm text-brand">Not now</Text>
          </Pressable>

          <Text className="mt-3 text-center font-rubik text-xs leading-relaxed text-muted-foreground">
            If you decline, reminders remain visible in Aira. You can enable system
            notifications later in Settings.
          </Text>
        </View>
      </View>
    </Modal>
  );
}
