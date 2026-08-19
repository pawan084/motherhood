import { router } from 'expo-router';
import { useState } from 'react';
import { Pressable, ScrollView, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { BrandOrb } from '@/components/brand-orb';
import { enableReminders } from '@/lib/notifications';
import { Icon } from '@/components/icon';
import {
  StagePickerDialog,
  type JourneyStage,
} from '@/components/stage-picker-dialog';
import {
  MOODS,
  askNotificationPermission,
  care,
  daysSinceCheckIn,
  lastSyncedMinutesAgo,
  reminderTimes,
  moodTrend,
  today,
  water,
  weekVideo,
} from '@/components/today/data';
import {
  BottomNav,
  Card,
  EmptyToday,
  NotificationAsk,
  ReturningCard,
  GhostButton,
  PrimaryButton,
  ProgressRing,
  Sparkline,
} from '@/components/today/parts';

/**
 * Today — the proactive home.
 *
 * Static data for now; see components/today/data.ts, where every field is named
 * after the API field it stands in for.
 *
 * The ordering is the product's argument, not a layout preference: one hero that
 * says where you are, then the things you can act on, then what to learn. The
 * reference notes today's care sits ABOVE mood so the ring lands above the fold
 * rather than behind the nav pill.
 */
/** Time-of-day greeting. The only thing on this screen that reads the clock. */
function greeting(): string {
  const h = new Date().getHours();
  if (h < 12) return 'Good morning';
  if (h < 18) return 'Good afternoon';
  return 'Good evening';
}

export default function TodayScreen() {
  const [mood, setMood] = useState<string | null>('great');
  const [showShieldHint, setShowShieldHint] = useState(true);
  const [stagePickerOpen, setStagePickerOpen] = useState(false);
  const [journeyStage, setJourneyStage] = useState<JourneyStage>(today.journey);
  const [reportedWeeks, setReportedWeeks] = useState<number | null>(today.weeksReported);
  const [draftStage, setDraftStage] = useState({
    journey: today.journey as JourneyStage,
    weeks: today.weeksReported as number | null,
  });

  // A day with nothing on it is its own state, not a stack of cards showing
  // zeroes. The hero stays — where you are in the journey is true either way.
  const nothingScheduled = care.total === 0;

  // Coming back after a gap. This outranks the other two states: the one thing
  // worth asking for is a check-in, and stacking a full care screen underneath
  // it would bury the ask under exactly the backlog the copy promises isn't
  // there.
  const away = daysSinceCheckIn >= 3;

  // The soft ask waits for Today rather than firing during onboarding: by here
  // the person has set a reminder cadence, so "gentle water and medicine
  // reminders" names something they actually asked for. It is also suppressed
  // while the re-engagement card is up — one ask at a time.
  const [askNotifications, setAskNotifications] = useState(
    askNotificationPermission && !away,
  );

  return (
    <SafeAreaView className="flex-1 bg-background" edges={['top']}>
      {/* ── header ── */}
      <View className="flex-row items-center justify-between gap-3 px-5 pb-3 pt-1">
        <View className="flex-row items-center gap-2.5">
          <BrandOrb size={34} />
          <View className="gap-0.5">
            <Text className="font-rubik-semibold text-lg text-foreground">Aira</Text>
            <View className="flex-row items-center gap-1.5">
              <Text className="font-rubik text-xs text-muted-foreground">
                Week {today.weeks}
              </Text>
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
            <View className="h-10 w-10 items-center justify-center rounded-full border border-border bg-card">
              <Icon name="settings" size={18} className="text-foreground" />
            </View>
          </Pressable>
          {/* Always reachable, and never waits for a red flag to appear. */}
          <Pressable
            onPress={() => router.push('/urgent')}
            accessibilityRole="button"
            accessibilityLabel="Crisis help"
            className="active:opacity-70">
            <View className="h-10 w-10 items-center justify-center rounded-full bg-tile">
              <Icon name="shield" size={18} className="text-destructive" />
            </View>
          </Pressable>
        </View>
      </View>

      <ScrollView
        contentContainerClassName="px-5 pb-32 gap-3"
        showsVerticalScrollIndicator={false}>
        {/* Above the hero on purpose: the ask is the point of this state, and
            putting it under the week card makes it a footnote. */}
        {away && (
          <ReturningCard
            onCheckIn={() => {
              // POST /v1/care/checkin — a mood, and nothing retroactive.
            }}
            onNotNow={() => {}}
            onPause={() => {
              // The reversible alternative to switching reminders off.
            }}
          />
        )}

        {/* ── hero ── */}
        <Pressable
          onPress={() => router.push('/journey')}
          accessibilityRole="button"
          className="overflow-hidden rounded-lg bg-secondary p-5 active:opacity-90">
          <View className="flex-row items-start justify-between gap-3">
            <View className="flex-1">
              <Text className="font-rubik-medium text-base text-foreground">
                {greeting()}, {today.name}
              </Text>

              <View className="mt-2 flex-row items-end gap-2">
                <Text className="font-rubik-semibold text-5xl leading-none text-brand">
                  {today.weeks}
                </Text>
                <View className="pb-1">
                  <Text className="font-rubik-semibold text-xs uppercase tracking-widest text-brand">
                    Week
                  </Text>
                  <Text className="font-rubik text-sm text-muted-foreground">
                    Day {today.dayOfWeek}
                  </Text>
                </View>
              </View>

              {!away && (
                <Text className="mt-3 font-rubik text-sm leading-relaxed text-muted-foreground">
                  {today.contextLine}
                </Text>
              )}
            </View>

            <BrandOrb size={96} />
          </View>

          {/* Reduced when returning: the week is reassurance, but a progress bar
              and a "next milestone" pill turn it into a schedule to measure
              yourself against — the opposite of what this state is saying. */}
          {!away && (
            <>
              {/* Progress through the forty weeks. Width is computed. */}
              <View className="mt-4 h-1.5 w-full rounded-full bg-card">
                <View
                  className="h-1.5 rounded-full bg-brand"
                  style={{ width: `${today.progress * 100}%` }}
                />
              </View>

              <Pressable
                onPress={() => router.push('/journey')}
                accessibilityRole="button"
                className="mt-4 self-start active:opacity-80">
                <View className="flex-row items-center gap-2 rounded-full bg-card px-4 py-3">
                  <Text className="font-rubik-medium text-sm text-foreground">
                    {today.nextMilestone}
                  </Text>
                  <Icon name="chevron-right" size={16} className="text-muted-foreground" />
                </View>
              </Pressable>
            </>
          )}
        </Pressable>

        {/* Everything else waits. The ask is a ten-second check-in; putting a
            full care screen under it would bury that ask beneath exactly the
            backlog the copy just promised isn't there. */}
        {!away && (
          <>
          {showShieldHint && (
            <View className="flex-row items-center gap-3 rounded-lg bg-notice p-3.5">
              <Icon name="shield" size={16} className="text-notice-foreground" />
              <Text className="flex-1 font-rubik text-xs text-notice-foreground">
                Shield, top right — crisis help anytime.
              </Text>
              <Pressable
                onPress={() => setShowShieldHint(false)}
                accessibilityRole="button"
                accessibilityLabel="Dismiss"
                hitSlop={10}
                className="active:opacity-60">
                <Icon name="x" size={16} className="text-notice-foreground" />
              </Pressable>
            </View>
          )}

          <Pressable
            onPress={() => {
              setDraftStage({ journey: journeyStage, weeks: reportedWeeks });
              setStagePickerOpen(true);
            }}
            accessibilityRole="button"
            className="items-center py-1 active:opacity-60">
            <Text className="font-rubik text-sm text-muted-foreground">
              Change your journey stage
            </Text>
          </Pressable>

          <View className="flex-row items-center justify-between">
            <View className="flex-row items-center gap-1.5">
              <View className="h-1.5 w-1.5 rounded-full bg-ok" />
              <Text className="font-rubik text-xs text-muted-foreground">Up to date</Text>
            </View>
            <Pressable accessibilityRole="button" hitSlop={8} className="active:opacity-60">
              <Text className="font-rubik-medium text-sm text-brand">Customize home</Text>
            </Pressable>
          </View>

          {nothingScheduled ? (
            <EmptyToday
              lastSyncedMinutesAgo={lastSyncedMinutesAgo}
              onOpenChat={() => {
                // Chat is the one thing always available, empty day or not.
                // There is no /chat route yet.
              }}
            />
          ) : (
            <>
            {/* ── water ── */}
            <Card label="Water intake">
              <View className="-mt-1 mb-3 flex-row justify-end">
                <Pressable accessibilityRole="button" className="active:opacity-70">
                  <View className="flex-row items-center gap-1.5 rounded-full bg-secondary px-3 py-1.5">
                    <Text className="font-rubik-medium text-xs text-brand">
                      Goal {water.goalLitres} L · Edit
                    </Text>
                  </View>
                </Pressable>
              </View>

              <View className="flex-row items-center gap-4">
                <ProgressRing fraction={water.litres / water.goalLitres} size={72}>
                  <Text className="font-rubik-semibold text-sm text-brand">
                    {Math.round((water.litres / water.goalLitres) * 100)}%
                  </Text>
                </ProgressRing>
                <View className="flex-1">
                  <Text className="font-rubik-semibold text-3xl text-foreground">
                    {water.litres} L
                  </Text>
                  <Text className="mt-0.5 font-rubik text-sm text-muted-foreground">
                    of {water.goalLitres} L today
                  </Text>
                </View>
              </View>

              {/* Seven-day strip. Each bar's height is that day's share of goal. */}
              <View className="mt-4 flex-row items-end justify-between gap-1.5">
                {water.week.map((d) => (
                  <View key={d.day} className="flex-1 items-center gap-1.5">
                    <View className="h-8 w-full justify-end rounded-full bg-muted">
                      <View
                        className="w-full rounded-full bg-brand"
                        style={{ height: `${Math.max(6, d.fill * 100)}%` }}
                      />
                    </View>
                    {d.today ? (
                      <View className="items-center justify-center rounded-full bg-brand px-2 py-1">
                        <Text className="font-rubik-semibold text-xs text-brand-foreground">
                          {d.day}
                        </Text>
                      </View>
                    ) : (
                      <Text className="font-rubik text-xs text-muted-foreground">{d.day}</Text>
                    )}
                  </View>
                ))}
              </View>

              <PrimaryButton label="Log water" icon="plus" className="mt-4" />

              <Text className="mt-3 text-center font-rubik text-xs text-muted-foreground">
                Averaging {water.averageLitres} L a day this week
              </Text>
            </Card>

            {/* ── today's care ── */}
            <Card label="Today's care" action="View all" onAction={() => router.push('/care')}>
              <Text className="font-rubik text-sm text-muted-foreground">
                {care.complete} of {care.total} complete
              </Text>
            </Card>

            {/* ── mood ── */}
            <Card label="How are you today?" action="History" onAction={() => router.push('/moods')}>
              <View className="flex-row justify-between gap-1.5">
                {MOODS.map((m) => {
                  const on = mood === m.key;
                  return (
                    <Pressable
                      key={m.key}
                      onPress={() => setMood(m.key)}
                      accessibilityRole="radio"
                      accessibilityState={{ selected: on }}
                      className="flex-1 active:opacity-80">
                      <View
                        className={
                          on
                            ? 'items-center gap-1.5 rounded-lg bg-ok px-1 py-2.5'
                            : 'items-center gap-1.5 rounded-lg bg-secondary px-1 py-2.5'
                        }>
                        <Icon
                          name="smile"
                          size={18}
                          className={on ? 'text-brand-foreground' : 'text-tile-foreground'}
                        />
                        <Text
                          className={
                            on
                              ? 'font-rubik-medium text-xs text-brand-foreground'
                              : 'font-rubik text-xs text-muted-foreground'
                          }>
                          {m.label}
                        </Text>
                      </View>
                    </Pressable>
                  );
                })}
              </View>

              <View className="mt-4">
                <Sparkline points={moodTrend} />
              </View>

              <View className="mt-4 flex-row gap-2.5">
                <GhostButton
                  label="View insights"
                  onPress={() => router.push('/wellness-report')}
                  className="flex-1"
                />
                <PrimaryButton label="Log mood" icon="plus" className="flex-1" />
              </View>
            </Card>

            {/* ── what to watch ── */}
            <Card label="What to watch this week" action="View all">
              <View className="flex-row gap-3">
                <View className="h-24 w-28 items-center justify-center rounded-lg bg-brand-deep">
                  <View className="h-9 w-9 items-center justify-center rounded-full bg-card">
                    <Icon name="play" size={16} className="text-brand" />
                  </View>
                  <View className="absolute bottom-1.5 right-1.5 rounded-full bg-foreground px-1.5 py-0.5">
                    <Text className="font-rubik-medium text-xs text-background">
                      {weekVideo.duration}
                    </Text>
                  </View>
                </View>

                <View className="flex-1 gap-1.5">
                  <View className="self-start rounded-full bg-tile px-2.5 py-1">
                    <Text className="font-rubik-medium text-xs text-tile-foreground">
                      {weekVideo.weekLabel}
                    </Text>
                  </View>
                  <Text className="font-rubik-semibold text-base leading-relaxed text-foreground">
                    {weekVideo.title}
                  </Text>
                  <Text className="font-rubik text-xs leading-relaxed text-muted-foreground">
                    {weekVideo.blurb}
                  </Text>
                </View>
              </View>

              <Text className="mt-3 font-rubik text-xs leading-relaxed text-muted-foreground">
                {weekVideo.reviewed}
              </Text>
            </Card>
            </>
          )}

          <Pressable
            onPress={() => router.push('/personalisation')}
            accessibilityRole="button"
            className="items-center py-2 active:opacity-60">
            <Text className="font-rubik-medium text-sm text-muted-foreground">
              What Aira remembers ›
            </Text>
          </Pressable>
          </>
        )}

      </ScrollView>

      <StagePickerDialog
        visible={stagePickerOpen}
        value={draftStage}
        onChange={setDraftStage}
        onCancel={() => setStagePickerOpen(false)}
        onSave={(next) => {
          // PATCH /v1/today/profile { journey: next.journey, weeks_reported: next.weeks }
          setJourneyStage(next.journey);
          setReportedWeeks(next.weeks);
          setStagePickerOpen(false);
        }}
      />

      <NotificationAsk
        visible={askNotifications}
        onEnable={() => {
          // Dismiss first: the OS prompt is about to take over the screen, and
          // leaving our own sheet behind it means two dialogs stacked.
          setAskNotifications(false);
          // Fires the real system prompt, then registers the quick-action
          // category and schedules the daily reminders. A decline is not an
          // error — reminders still render inside Aira, which is what the
          // sheet's footnote promises.
          void enableReminders(reminderTimes);
        }}
        onNotNow={() => setAskNotifications(false)}
      />

      <BottomNav active="today" />
    </SafeAreaView>
  );
}
