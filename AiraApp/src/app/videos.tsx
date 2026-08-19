import { router } from 'expo-router';
import { useState } from 'react';
import { Pressable, ScrollView, Text, TextInput, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { BrandOrb } from '@/components/brand-orb';
import { Icon } from '@/components/icon';
import { BottomNav } from '@/components/today/parts';

const WEEK_VIDEOS = [
  { title: 'One habit with outsized benefits', rating: '4.0 (3)', tag: undefined },
  { title: 'Round-ligament aches, explained', rating: 'Not yet rated', tag: 'New' },
  { title: 'Sleep support now', rating: '4.2 (4)', tag: undefined },
] as const;

const BIRTH_VIDEOS = [
  { title: 'Five things to have ready', rating: 'Not yet rated', duration: '1:00' },
  { title: 'Packing your hospital bag', rating: '4.8 (5)', duration: '2:10' },
  { title: 'Birth preferences', rating: '4.6 (6)', duration: '1:40' },
] as const;

function Header() {
  return (
    <View className="border-b border-border bg-card px-5 pb-4 pt-2">
      <View className="flex-row items-center justify-between gap-3">
        <View className="flex-row items-center gap-3">
          <BrandOrb size={36} />
          <View>
            <Text className="font-rubik-semibold text-xl text-foreground">Aira</Text>
            <Text className="font-rubik text-xs text-muted-foreground">
              Short, stage-aware guides
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
  );
}

function VideoThumb({ duration = '1:00', tag }: { duration?: string; tag?: string }) {
  return (
    <View className="h-[118px] justify-center rounded-[12px] bg-[#5A435F]">
      {tag && (
        <View className="absolute left-2 top-2 rounded-full bg-foreground px-2 py-0.5">
          <Text className="font-rubik-medium text-[10px] text-background">{tag}</Text>
        </View>
      )}
      <View className="self-center rounded-full bg-foreground/20 p-1">
        <View className="h-7 w-7 items-center justify-center rounded-full border border-card">
          <Icon name="play" size={13} className="text-card" />
        </View>
      </View>
      <View className="absolute bottom-2 right-2 rounded bg-foreground px-1.5 py-0.5">
        <Text className="font-rubik-medium text-[10px] text-background">{duration}</Text>
      </View>
    </View>
  );
}

function VideoTile({
  title,
  rating,
  tag,
  duration,
}: {
  title: string;
  rating: string;
  tag?: string;
  duration?: string;
}) {
  return (
    <Pressable
      onPress={() => router.push('/video-player')}
      accessibilityRole="button"
      className="w-40 active:opacity-80">
      <VideoThumb tag={tag} duration={duration} />
      <Text className="mt-2 font-rubik-semibold text-sm leading-tight text-foreground">
        {title}
      </Text>
      <View className="mt-1 flex-row items-center gap-1">
        {rating === 'Not yet rated' ? (
          <Text className="font-rubik text-xs text-muted-foreground">Not yet rated</Text>
        ) : (
          <>
            <Icon name="star" size={14} className="text-muted-foreground" />
            <Text className="font-rubik text-xs text-muted-foreground">{rating}</Text>
          </>
        )}
      </View>
    </Pressable>
  );
}

function SectionHeader({ title }: { title: string }) {
  return (
    <View className="flex-row items-center justify-between">
      <Text className="font-serif text-[22px] leading-7 text-foreground">{title}</Text>
      <Pressable accessibilityRole="button" hitSlop={8} className="active:opacity-70">
        <Text className="font-rubik-semibold text-sm text-brand">See all ›</Text>
      </Pressable>
    </View>
  );
}

function Skeleton({ className }: { className: string }) {
  return <View className={`bg-secondary ${className}`} />;
}

function VideosLoadingState() {
  return (
    <ScrollView
      contentContainerClassName="gap-4 px-5 pb-32 pt-5"
      showsVerticalScrollIndicator={false}>
      <Skeleton className="h-11 rounded-lg" />

      <View className="flex-row gap-2">
        <Skeleton className="h-8 w-16 rounded-full" />
        <Skeleton className="h-8 w-28 rounded-full" />
        <Skeleton className="h-8 w-28 rounded-full" />
      </View>

      <Skeleton className="h-[172px] rounded-[20px]" />

      <Skeleton className="h-4 w-32 rounded-full" />

      <View className="flex-row gap-3">
        <View className="flex-1">
          <Skeleton className="h-[94px] rounded-[12px]" />
          <Skeleton className="mt-2 h-3 w-28 rounded-full" />
        </View>
        <View className="flex-1">
          <Skeleton className="h-[94px] rounded-[12px]" />
          <Skeleton className="mt-2 h-3 w-28 rounded-full" />
        </View>
      </View>

      <Pressable accessibilityRole="button" className="mt-2 active:opacity-80">
        <View className="items-center rounded-[16px] border border-border bg-card px-5 py-4">
          <Text className="font-rubik-semibold text-base text-brand">
            Browse saved videos
          </Text>
        </View>
      </Pressable>
    </ScrollView>
  );
}

function VideosNoResultsState({
  query,
  onQueryChange,
}: {
  query: string;
  onQueryChange: (query: string) => void;
}) {
  return (
    <ScrollView
      contentContainerClassName="min-h-full px-5 pb-32 pt-5"
      showsVerticalScrollIndicator={false}>
      <View className="flex-row items-center gap-3 rounded-[14px] border border-brand bg-card px-4 py-3">
        <Icon name="search" size={20} className="text-muted-foreground" />
        <TextInput
          value={query}
          onChangeText={onQueryChange}
          placeholder="Search videos..."
          placeholderTextColor="#8A8295"
          autoCapitalize="none"
          autoCorrect={false}
          returnKeyType="search"
          className="min-h-[28px] flex-1 font-rubik text-base text-foreground outline-none"
          style={{ minWidth: 0 }}
        />
      </View>

      <ScrollView
        horizontal
        showsHorizontalScrollIndicator={false}
        contentContainerClassName="mt-3 gap-2">
        {['All', 'Early pregnancy', 'Postpartum'].map((chip, index) => (
          <Pressable key={chip} accessibilityRole="button" className="active:opacity-80">
            <View
              className={
                index === 0
                  ? 'rounded-full bg-brand px-5 py-3'
                  : 'rounded-full border border-border bg-card px-5 py-3'
              }>
              <Text
                className={
                  index === 0
                    ? 'font-rubik-semibold text-sm text-brand-foreground'
                    : 'font-rubik-semibold text-sm text-foreground'
                }>
                {chip}
              </Text>
            </View>
          </Pressable>
        ))}
      </ScrollView>

      <View className="flex-1 items-center px-6 pt-28">
        <Icon name="search" size={28} className="text-muted-foreground" />
        <Text className="mt-5 text-center font-serif text-[24px] leading-8 text-foreground">
          No videos match yet
        </Text>
        <Text className="mt-3 text-center font-rubik text-sm leading-relaxed text-muted-foreground">
          We don&apos;t have &quot;{query || 'that'}&quot; yet. Try &quot;postpartum&quot; or ask
          Aira in Chat — this helps us know what to make next.
        </Text>
      </View>

      <Pressable
        onPress={() => router.push('/chat')}
        accessibilityRole="button"
        className="mt-14 active:opacity-80">
        <View className="items-center rounded-[16px] border border-border bg-card px-5 py-4">
          <Text className="font-rubik-semibold text-base text-brand">Ask Aira instead</Text>
        </View>
      </Pressable>
    </ScrollView>
  );
}

export default function VideosScreen() {
  const [videoState] = useState<'loading' | 'catalog'>('catalog');
  const [query, setQuery] = useState('');
  const showingSearchResults = query.trim().length > 0;

  return (
    <SafeAreaView className="flex-1 bg-background" edges={['top']}>
      <Header />

      {videoState === 'loading' ? (
        <VideosLoadingState />
      ) : showingSearchResults ? (
        <VideosNoResultsState query={query} onQueryChange={setQuery} />
      ) : (
        <ScrollView
        contentContainerClassName="gap-4 px-5 pb-32 pt-5"
        showsVerticalScrollIndicator={false}>
        <View className="flex-row items-start gap-3 rounded-lg bg-secondary px-4 py-3.5">
          <Icon name="info" size={20} className="mt-0.5 text-tile-foreground" />
          <Text className="flex-1 font-rubik text-xs leading-relaxed text-tile-foreground">
            Stage recommendations are editorially reviewed. Last content review: August
            2026.
          </Text>
        </View>

        <View className="flex-row items-center gap-3 rounded-[14px] border border-border bg-card px-4 py-3">
          <Icon name="search" size={20} className="text-muted-foreground" />
          <TextInput
            value={query}
            onChangeText={setQuery}
            placeholder="Search videos..."
            placeholderTextColor="#8A8295"
            autoCapitalize="none"
            autoCorrect={false}
            returnKeyType="search"
            className="min-h-[28px] flex-1 font-rubik text-base text-foreground outline-none"
            style={{ minWidth: 0 }}
          />
        </View>

        <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerClassName="gap-2">
          {['All', 'Early pregnancy', 'Third trimester', 'Preparing'].map((chip, index) => (
            <Pressable key={chip} accessibilityRole="button" className="active:opacity-80">
              <View
                className={
                  index === 0
                    ? 'rounded-full bg-brand px-5 py-3'
                    : 'rounded-full border border-border bg-card px-5 py-3'
                }>
                <Text
                  className={
                    index === 0
                      ? 'font-rubik-semibold text-sm text-brand-foreground'
                      : 'font-rubik-semibold text-sm text-foreground'
                  }>
                  {chip}
                </Text>
              </View>
            </Pressable>
          ))}
        </ScrollView>

        <View className="flex-row items-start gap-3 rounded-lg bg-notice px-3.5 py-3">
          <Icon name="info" size={20} className="mt-0.5 text-foreground" />
          <Text className="flex-1 font-rubik text-xs leading-relaxed text-notice-foreground">
            Filter by your trimester first — it cuts the list to what&apos;s relevant now.
          </Text>
          <Pressable accessibilityRole="button" accessibilityLabel="Dismiss" hitSlop={10}>
            <Icon name="x" size={18} className="text-notice-foreground" />
          </Pressable>
        </View>

        <Pressable
          onPress={() => router.push('/video-player')}
          accessibilityRole="button"
          className="overflow-hidden rounded-[18px] bg-[#2E1F35] px-4 py-4 active:opacity-90">
          <View className="flex-row items-center justify-between">
            <View className="rounded-full bg-card/20 px-3 py-1">
              <Text className="font-rubik-medium text-xs text-card">Third trimester · Week 24</Text>
            </View>
            <View className="h-9 w-9 items-center justify-center rounded-full bg-card/20">
              <Icon name="play" size={17} className="text-card" />
            </View>
          </View>
          <Text className="mt-3 font-serif text-[22px] leading-7 text-card">
            Five things to have ready before week 28
          </Text>
          <Text className="mt-1 font-rubik text-xs text-card">
            1 min · Editor&apos;s pick for your stage
          </Text>
        </Pressable>

        <Pressable
          onPress={() => router.push('/video-player')}
          accessibilityRole="button"
          className="active:opacity-90">
          <View
            className="flex-row items-center justify-center gap-2 rounded-[16px] bg-brand px-5 py-4"
            style={{
              shadowColor: '#7527F5',
              shadowOpacity: 0.24,
              shadowRadius: 18,
              shadowOffset: { width: 0, height: 10 },
              elevation: 8,
            }}>
            <Icon name="play-circle" size={19} className="text-brand-foreground" />
            <Text className="font-rubik-semibold text-base text-brand-foreground">
              Play featured guide
            </Text>
          </View>
        </Pressable>

        <SectionHeader title="For week 24" />
        <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerClassName="gap-3">
          {WEEK_VIDEOS.map((video) => (
            <VideoTile key={video.title} {...video} />
          ))}
        </ScrollView>

        <Text className="font-serif text-[22px] leading-7 text-foreground">Browse by month</Text>
        <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerClassName="gap-2">
          {[
            ['1', 'Wks 1-4'],
            ['2', 'Wks 5-8'],
            ['3', 'Wks 9-13'],
            ['4', 'Wks 14-17'],
            ['5', 'Wks 18-21'],
          ].map(([month, weeks]) => (
            <Pressable key={month} accessibilityRole="button" className="active:opacity-80">
              <View className="w-[72px] items-center rounded-[14px] bg-card px-3 py-3">
                <Text className="font-serif text-xl text-foreground">{month}</Text>
                <Text className="mt-1 font-rubik text-[10px] text-muted-foreground">{weeks}</Text>
              </View>
            </Pressable>
          ))}
        </ScrollView>

        <SectionHeader title="Preparing for birth" />
        <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerClassName="gap-3">
          {BIRTH_VIDEOS.map((video) => (
            <VideoTile key={video.title} {...video} />
          ))}
        </ScrollView>

        <SectionHeader title="Everyday care" />
        <View className="flex-row gap-3 rounded-[20px] border border-border bg-card p-5">
          <View className="h-[88px] w-[108px] justify-center rounded-[12px] bg-[#9BAA9C]">
            <View className="absolute left-2 top-2 rounded-full bg-foreground/40 px-2 py-0.5">
              <Text className="font-rubik-medium text-[10px] text-card">✓ Watched</Text>
            </View>
            <View className="self-center rounded-full bg-foreground/20 p-1">
              <View className="h-7 w-7 items-center justify-center rounded-full border border-card">
                <Icon name="play" size={13} className="text-card" />
              </View>
            </View>
            <View className="absolute bottom-2 right-2 rounded bg-foreground px-1.5 py-0.5">
              <Text className="font-rubik-medium text-[10px] text-background">1:00</Text>
            </View>
          </View>
          <View className="flex-1">
            <Text className="font-rubik-semibold text-base leading-tight text-muted-foreground">
              Do&apos;s and don&apos;ts, without the fear
            </Text>
            <View className="mt-2 self-start rounded-full bg-tile px-2.5 py-1">
              <Text className="font-rubik-medium text-xs text-tile-foreground">
                Everyday care
              </Text>
            </View>
            <Text className="mt-2 font-rubik text-xs text-muted-foreground">★Not yet rated</Text>
          </View>
          <View className="items-center">
            <Icon name="heart" size={24} className="text-muted-foreground" />
            <Text className="mt-1 font-rubik text-xs text-muted-foreground">0</Text>
          </View>
        </View>
        </ScrollView>
      )}

      <BottomNav active="videos" />
    </SafeAreaView>
  );
}
